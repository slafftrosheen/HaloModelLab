package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.TelemetryView
import com.slafftrosheen.halomodellab.runner.LabClock
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface ExportResult {
    data class Success(val file: File) : ExportResult

    /** RUNNING, PAUSING and STOPPING must be stopped or paused first. */
    data class NotExportable(val state: SessionState) : ExportResult

    data class MissingSession(val sessionId: String) : ExportResult

    data class Failure(val code: String, val detail: String) : ExportResult
}

/**
 * Builds a real ZIP from a consistent repository snapshot.
 *
 * It never copies the live SQLite file, never renames an origin, never invents a runtime
 * version and never silently drops required data: oversized raw sections are truncated only
 * with an explicit omission record, and a payload over the archive limit fails the export.
 *
 * The archive is written to `<name>.part` in private cache and renamed only on success.
 */
class ReportExporter(
    private val repository: SessionRepository,
    private val exportRoot: File,
    private val clock: LabClock,
    private val packageName: String,
    private val appVersion: String,
    private val sourceRevision: String = SOURCE_REVISION_UNKNOWN,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun export(sessionId: String, previousSessionId: String? = null): ExportResult = withContext(ioDispatcher) {
        val snapshot = repository.exportSnapshot(sessionId)
            ?: return@withContext ExportResult.MissingSession(sessionId)
        val detail = snapshot.detail

        if (detail.summary.state in NON_EXPORTABLE_STATES) {
            return@withContext ExportResult.NotExportable(detail.summary.state)
        }

        try {
            val (comparisonBytes, comparisonSummary) = when {
                previousSessionId == null -> RealEvidenceExportJson.comparison() to "No previous session selected; comparison was not requested."
                else -> {
                    val previous = repository.exportSnapshot(previousSessionId)
                    when {
                        previous == null -> RealEvidenceExportJson.comparison(previousSessionId, unavailableReason = "PREVIOUS_SESSION_MISSING") to
                            "Selected previous session $previousSessionId is missing; comparison is unavailable."
                        !isExportable(previous.detail.summary.state) -> RealEvidenceExportJson.comparison(
                            previousSessionId,
                            unavailableReason = "PREVIOUS_SESSION_NOT_QUIESCENT",
                        ) to "Selected previous session is still active; comparison is unavailable."
                        else -> {
                            val comparison = ComparisonBuilder.compare(
                                ComparisonBuilder.fromSnapshot(snapshot),
                                ComparisonBuilder.fromSnapshot(previous),
                            )
                            RealEvidenceExportJson.comparison(previousSessionId, comparison) to
                                comparison.toReportSummary(previousSessionId)
                        }
                    }
                }
            }
            ExportResult.Success(writeArchive(detail, snapshot.realEvidence, comparisonBytes, comparisonSummary))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ArchiveTooLargeException) {
            ExportResult.Failure(
                code = "EXPORT_TOO_LARGE",
                detail = tooLarge.message ?: "The archive exceeds the configured limit.",
            )
        } catch (sectionTooLarge: ExportSectionTooLargeException) {
            ExportResult.Failure("EXPORT_SECTION_TOO_LARGE", sectionTooLarge.message ?: "A required section exceeds its bound.")
        } catch (failure: Exception) {
            ExportResult.Failure(
                code = "EXPORT_FAILED",
                detail = failure.message ?: failure::class.java.simpleName,
            )
        }
    }

    private suspend fun writeArchive(
        detail: SessionDetail,
        realEvidence: RealEvidenceExportSnapshot?,
        comparisonBytes: ByteArray,
        comparisonSummary: String,
    ): File {
        val omissions = mutableListOf<String>()
        val generatedAt = iso(clock.wallTimeMs())
        val outputPreviews = RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(detail, realEvidence)
        val eligiblePreviewAttempts = if (
            detail.summary.origin == EvidenceOrigin.DEVICE_MEASUREMENT &&
            detail.summary.profileId == "halo-fast-smoke-cpu-v1" &&
            RealEvidenceExportJson.hasFixedSmokeFixture(realEvidence)
        ) {
            detail.queue.filter { it.scenarioId == "short-ready" && it.role.name == "FAST" && it.requestedBackend.name == "CPU" }
                .sumOf { it.attempts.size }
        } else 0
        val omittedOutputPreviews = (eligiblePreviewAttempts - outputPreviews.size).coerceAtLeast(0)

        val payload = LinkedHashMap<String, ByteArray>()
        payload["results.json"] = ExportJson.results(toResults(detail))
        payload["events.jsonl"] = buildEventsJsonl(detail, omissions)
        payload["telemetry.csv"] = buildTelemetryCsv(detail, omissions)
        payload["coverage.json"] = RealEvidenceExportJson.coverage(detail, realEvidence)
        val qualificationBytes = RealEvidenceExportJson.qualifications(realEvidence)
        if (qualificationBytes.size > MAX_SECTION_BYTES) {
            throw ExportSectionTooLargeException("qualification.json is ${qualificationBytes.size} bytes; the section limit is $MAX_SECTION_BYTES.")
        }
        payload["qualification.json"] = qualificationBytes
        payload["comparison.json"] = comparisonBytes
        outputPreviews.take(MAX_ARTIFACT_FILES).forEachIndexed { index, preview ->
            payload["artifacts/output-$index.txt"] = preview.text.toByteArray(Charsets.UTF_8)
        }
        payload["artifacts/index.json"] = RealEvidenceExportJson.artifactIndex(
            realEvidence,
            outputPreviews.take(MAX_ARTIFACT_FILES),
            omittedOutputPreviews,
        )
        payload["failures/index.json"] = RealEvidenceExportJson.failureIndex(detail)
        payload["failures/clusters.json"] = RealEvidenceExportJson.failureClusters(detail)
        buildFailureFiles(detail, omissions).forEach { (name, bytes) -> payload[name] = bytes }
        buildReproductionFiles(detail, realEvidence, omissions).forEach { (name, bytes) -> payload[name] = bytes }
        if (realEvidence != null) {
            val realPlan = RealEvidenceExportJson.plan(realEvidence, detail.planJson)
            if (realPlan.size > MAX_SECTION_BYTES) {
                throw ExportSectionTooLargeException("real-plan.json is ${realPlan.size} bytes; the section limit is $MAX_SECTION_BYTES.")
            }
            payload["real-plan.json"] = realPlan
            payload["remote-events.jsonl"] = buildRemoteEventsJsonl(realEvidence, omissions)
            if (realEvidence.events.size >= MAX_REMOTE_EVENT_ROWS) {
                omissions += "remote-events.jsonl reached the $MAX_REMOTE_EVENT_ROWS row cap; completeness is unknown."
            }
            realEvidence.truncatedSections.forEach { omissions += "$it was limited while reading the Room snapshot." }
        }
        val reportBody = ReportFormatter.formatContent(detail, generatedAt, omissions, comparisonSummary)
        val report = if (realEvidence == null) {
            ReportFormatter.bound(reportBody)
        } else {
            ReportFormatter.bound(reportBody, ReportFormatter.MAX_REPORT_BYTES - REAL_SUMMARY_RESERVE_BYTES)
                .let { base -> appendRealEvidenceSummary(base, detail, realEvidence, outputPreviews.size) }
                .let { ReportFormatter.bound(it) }
        }
        payload["report.md"] = report.toByteArray(Charsets.UTF_8)

        val payloadBytes = payload.values.sumOf { it.size.toLong() }
        if (payloadBytes > MAX_ARCHIVE_BYTES) {
            throw ArchiveTooLargeException(
                "Payload is $payloadBytes bytes; the archive limit is $MAX_ARCHIVE_BYTES.",
            )
        }

        val fileEntries = payload.entries
            .sortedBy { it.key }
            .map { (name, bytes) ->
                FileEntryDto(name = name, sha256 = sha256(bytes), sizeBytes = bytes.size)
            }

        val manifest = ManifestDto(
            schemaVersion = SCHEMA_VERSION,
            exportVersion = EXPORT_VERSION,
            application = ApplicationDto(
                packageName = packageName,
                versionName = appVersion,
                sourceRevision = sourceRevision,
            ),
            sessionId = detail.summary.sessionId,
            sessionState = detail.summary.state.name,
            sessionOrigin = detail.summary.origin.name,
            createdAt = generatedAt,
            snapshotEventSeq = detail.events.lastOrNull()?.seq ?: 0L,
            runtime = RuntimeDto(
                bridgeStatus = when (detail.summary.origin) {
                    EvidenceOrigin.SYNTHETIC -> "NOT_APPLICABLE_SYNTHETIC"
                    EvidenceOrigin.DEVICE_MEASUREMENT -> when {
                        realEvidence?.binding?.remoteRunId != null -> "REMOTE_RUN_RECORDED"
                        else -> "UNKNOWN_NO_REMOTE_BINDING"
                    }
                    EvidenceOrigin.IMPORTED_UNVERIFIED -> "IMPORTED_UNVERIFIED"
                },
                inferenceRuntime = RealEvidenceExportJson.consistentTerminalRuntimeVersion(realEvidence),
            ),
            plan = PlanDto(
                profileId = detail.summary.profileId,
                engineId = detail.summary.engineId,
                caseTimeoutMs = detail.caseTimeoutMs,
                budgetMs = detail.budgetMs,
                repeatCount = detail.repeatCount,
                requireCharging = detail.requireCharging,
                itemCount = detail.summary.itemCount,
            ),
            planSnapshot = detail.planJson,
            provenance = ProvenanceDto(
                evidenceOrigin = detail.summary.origin.name,
                engineId = detail.summary.engineId,
                synthetic = detail.summary.origin == EvidenceOrigin.SYNTHETIC,
            ),
            omissions = omissions.toList(),
            privacyMode = PRIVACY_MODE,
            files = fileEntries,
        )

        val manifestBytes = ExportJson.manifest(manifest)
        val checksums = (fileEntries + FileEntryDto(
            name = MANIFEST_ENTRY,
            sha256 = sha256(manifestBytes),
            sizeBytes = manifestBytes.size,
        ))
            .sortedBy { it.name }
            .joinToString(separator = "") { entry -> "${entry.sha256}  ${entry.name}\n" }
            .toByteArray(Charsets.UTF_8)

        val totalBytes = payloadBytes + manifestBytes.size + checksums.size
        if (totalBytes > MAX_ARCHIVE_BYTES) {
            throw ArchiveTooLargeException(
                "Archive is $totalBytes bytes; the archive limit is $MAX_ARCHIVE_BYTES.",
            )
        }

        val order = listOf(REPORT_ENTRY) +
            payload.keys.filter { it != REPORT_ENTRY } +
            MANIFEST_ENTRY +
            CHECKSUMS_ENTRY

        return writeZip(
            order = order,
            payload = payload,
            manifestBytes = manifestBytes,
            checksumBytes = checksums,
            sessionId = detail.summary.sessionId,
        )
    }

    private suspend fun writeZip(
        order: List<String>,
        payload: Map<String, ByteArray>,
        manifestBytes: ByteArray,
        checksumBytes: ByteArray,
        sessionId: String,
    ): File {
        exportRoot.mkdirs()
        val fileName = "halo-model-lab_${safeSessionId(sessionId)}.zip"
        val part = File(exportRoot, "$fileName.part")
        val target = File(exportRoot, fileName)

        part.delete()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(part))).use { zip ->
                order.forEach { name ->
                    currentCoroutineContext().ensureActive()
                    val bytes = when (name) {
                        MANIFEST_ENTRY -> manifestBytes
                        CHECKSUMS_ENTRY -> checksumBytes
                        else -> payload.getValue(name)
                    }
                    zip.putNextEntry(ZipEntry(name))
                    var offset = 0
                    while (offset < bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val length = minOf(ZIP_WRITE_CHUNK_BYTES, bytes.size - offset)
                        zip.write(bytes, offset, length)
                        offset += length
                    }
                    zip.closeEntry()
                }
            }

            currentCoroutineContext().ensureActive()
            if (part.length() > MAX_ARCHIVE_BYTES) {
                throw ArchiveTooLargeException("Compressed ZIP is ${part.length()} bytes; the archive limit is $MAX_ARCHIVE_BYTES.")
            }

            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            return target
        } catch (failure: Exception) {
            part.delete()
            throw failure
        }
    }

    private fun toResults(detail: SessionDetail): ResultsDto {
        val metricsByAttempt: Map<String, List<TelemetryView>> = detail.telemetry
            .filter { it.attemptId != null }
            .groupBy { it.attemptId ?: "" }

        return ResultsDto(
            schemaVersion = SCHEMA_VERSION,
            sessionId = detail.summary.sessionId,
            state = detail.summary.state.name,
            origin = detail.summary.origin.name,
            engineId = detail.summary.engineId,
            profileId = detail.summary.profileId,
            createdAt = iso(detail.summary.createdAtWallTimeMs),
            finishedAt = detail.summary.finishedWallTimeMs?.let(::iso),
            persistedActiveMs = detail.persistedActiveMs,
            itemCount = detail.summary.itemCount,
            completedItemCount = detail.summary.completedItemCount,
            queue = detail.queue.map { item ->
                QueueItemDto(
                    ordinal = item.ordinal,
                    subjectId = item.subjectId,
                    scenarioId = item.scenarioId,
                    state = item.state.name,
                    repeatIndex = item.repeatIndex,
                    role = item.role.name,
                    requestedBackend = item.requestedBackend.name,
                    attempts = item.attempts.map { attempt ->
                        attempt.toDto(metricsByAttempt[attempt.attemptId].orEmpty())
                    },
                )
            },
        )
    }

    private fun AttemptView.toDto(metrics: List<TelemetryView>): AttemptDto = AttemptDto(
        attemptId = attemptId,
        number = number,
        ownerInstanceId = ownerInstanceId,
        stage = stage.name,
        outcome = outcome?.name,
        validity = validity?.name,
        deadlineReason = deadlineReason?.name,
        startedWallTimeMs = startedWallTimeMs,
        finishedWallTimeMs = finishedWallTimeMs,
        elapsedMs = elapsedMs,
        failure = failure?.toDto(),
        metrics = metrics.map { metric ->
            MetricDto(
                name = metric.name,
                value = metric.value,
                unit = metric.unit,
                scope = metric.scope,
                origin = metric.origin.name,
                unavailableReason = metric.unavailableReason,
            )
        },
    )

    private fun FailureInfo.toDto(): FailureDto = FailureDto(
        code = code,
        domain = domain.name,
        confidence = confidence.name,
        detail = detail,
    )

    private fun buildEventsJsonl(
        detail: SessionDetail,
        omissions: MutableList<String>,
    ): ByteArray {
        val builder = StringBuilder()
        var bytes = 0
        var included = 0

        for (event in detail.events) {
            val line = ExportJson.event(
                EventDto(
                    seq = event.seq,
                    attemptId = event.attemptId,
                    stage = event.stage?.name,
                    code = event.code,
                    wallTimeMs = event.wallTimeMs,
                    elapsedTimeMs = event.elapsedTimeMs,
                    ownerInstanceId = event.ownerInstanceId,
                ),
            ) + "\n"
            val lineBytes = line.toByteArray(Charsets.UTF_8).size
            if (bytes + lineBytes > MAX_SECTION_BYTES) break
            builder.append(line)
            bytes += lineBytes
            included += 1
        }

        if (included < detail.events.size) {
            val firstOmitted = detail.events[included]
            omissions += "events.jsonl truncated: included $included of ${detail.events.size}; " +
                "seq ${firstOmitted.seq}..${detail.events.last().seq} omitted."
        }

        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildTelemetryCsv(
        detail: SessionDetail,
        omissions: MutableList<String>,
    ): ByteArray {
        val builder = StringBuilder()
        builder.append("name,attemptId,value,unit,scope,origin,unavailableReason,wallTimeMs\n")
        var bytes = 0
        var included = 0

        for (sample in detail.telemetry) {
            if (included >= MAX_TELEMETRY_ROWS) break
            val line = listOf(
                sample.name,
                sample.attemptId,
                sample.value?.toString(),
                sample.unit,
                sample.scope,
                sample.origin.name,
                sample.unavailableReason,
                sample.wallTimeMs.toString(),
            ).joinToString(separator = ",") { csv(it) } + "\n"
            val lineBytes = line.toByteArray(Charsets.UTF_8).size
            if (bytes + lineBytes > MAX_SECTION_BYTES) break
            builder.append(line)
            bytes += lineBytes
            included += 1
        }

        if (included < detail.telemetry.size) {
            omissions += "telemetry.csv truncated: included $included of ${detail.telemetry.size} samples."
        }

        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildRemoteEventsJsonl(
        snapshot: RealEvidenceExportSnapshot,
        omissions: MutableList<String>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        var included = 0
        for (event in snapshot.events) {
            val line = (RealEvidenceExportJson.remoteEvent(event) + "\n").toByteArray(Charsets.UTF_8)
            if (output.size() + line.size > MAX_SECTION_BYTES) {
                omissions += "remote-events.jsonl truncated at a record boundary after $included events."
                break
            }
            output.write(line)
            included += 1
        }
        return output.toByteArray()
    }

    private fun buildFailureFiles(
        detail: SessionDetail,
        omissions: MutableList<String>,
    ): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var skipped = 0
        var index = 0

        detail.queue.forEach { item: QueueItemView ->
            item.attempts.forEach attemptLoop@{ attempt: AttemptView ->
                val failure = attempt.failure ?: return@attemptLoop
                if (files.size >= MAX_FAILURE_FILES) {
                    skipped += 1
                    return@attemptLoop
                }
                val name = "failures/${safeAttemptId(attempt.attemptId, index)}.json"
                index += 1
                files[name] = ExportJson.failure(
                    FailureFileDto(
                        attemptId = attempt.attemptId,
                        queueItemOrdinal = item.ordinal,
                        scenarioId = item.scenarioId,
                        stage = attempt.stage.name,
                        outcome = attempt.outcome?.name,
                        validity = attempt.validity?.name,
                        deadlineReason = attempt.deadlineReason?.name,
                        startedWallTimeMs = attempt.startedWallTimeMs,
                        finishedWallTimeMs = attempt.finishedWallTimeMs,
                        elapsedMs = attempt.elapsedMs,
                        failure = failure.toDto(),
                    ),
                )
            }
        }

        if (skipped > 0) {
            omissions += "failures/: $skipped failure files omitted after the $MAX_FAILURE_FILES limit."
        }

        return files
    }

    private fun buildReproductionFiles(
        detail: SessionDetail,
        snapshot: RealEvidenceExportSnapshot?,
        omissions: MutableList<String>,
    ): Map<String, ByteArray> {
        val configurations = snapshot?.configurations.orEmpty().associateBy { it.queueItemId }
        val files = LinkedHashMap<String, ByteArray>()
        var skipped = 0
        var index = 0
        detail.queue.forEach { item ->
            item.attempts.forEach attemptLoop@{ attempt ->
                if (files.size >= MAX_REPRODUCTION_FILES) {
                    skipped += 1
                    return@attemptLoop
                }
                val id = safeAttemptId(attempt.attemptId, index++)
                files["reproduction/$id.json"] = RealEvidenceExportJson.reproduction(
                    item = item,
                    attempt = attempt,
                    origin = detail.summary.origin,
                    configuration = configurations[item.queueItemId],
                    snapshot = snapshot,
                )
            }
        }
        if (skipped > 0) omissions += "reproduction/: $skipped attempt descriptors omitted after the $MAX_REPRODUCTION_FILES limit."
        return files
    }

    private fun csv(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        // Neutralize spreadsheet formulas in text fields.
        val neutralized = if (value[0] == '=' || value[0] == '+' || value[0] == '-' || value[0] == '@') {
            "'$value"
        } else {
            value
        }
        val needsQuotes = neutralized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) {
            "\"" + neutralized.replace("\"", "\"\"") + "\""
        } else {
            neutralized
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun iso(wallTimeMs: Long): String = Instant.ofEpochMilli(wallTimeMs).toString()

    private fun safeSessionId(raw: String): String =
        if (UUID_PATTERN.matches(raw)) raw else "session"

    private fun safeAttemptId(raw: String, index: Int): String =
        if (UUID_PATTERN.matches(raw)) raw else "attempt-$index"

    private class ArchiveTooLargeException(message: String) : Exception(message)
    private class ExportSectionTooLargeException(message: String) : Exception(message)

    private fun SessionComparison.toReportSummary(previousSessionId: String): String = when (status) {
        ComparisonStatus.INCOMPARABLE -> "Previous session $previousSessionId was selected, but exact scope/policy checks failed: $reasonCode."
        ComparisonStatus.COMPATIBLE -> buildString {
            append("Compared with ").append(previousSessionId).append(" at the exact verified scope: ")
            append("new failures ").append(newFailures.size).append(", resolved ").append(resolvedFailures.size)
            append(", persistent ").append(persistentFailures.size).append(". ")
            val known = metricDeltas.filter { it.delta != null }
            append("Metric deltas: ").append(known.size).append(" known, ")
            append(metricDeltas.size - known.size).append(" unknown.")
            if (known.isNotEmpty()) {
                append(" Observed metric changes: ")
                append(known.take(MAX_REPORT_METRIC_DELTAS).joinToString("; ") { metric ->
                    val name = metric.key.name.filterNot { it.isISOControl() }.take(60).replace("`", "'")
                    "$name ${metric.delta} ${metric.key.unit.filterNot { it.isISOControl() }.take(20)}"
                })
                if (known.size > MAX_REPORT_METRIC_DELTAS) append("; additional values are in comparison.json")
                append('.')
            }
            append(" These are observed changes, not a regression verdict.")
        }
    }

    companion object {
        /** Single source of truth for the UI button and the exporter guard. */
        fun isExportable(state: SessionState): Boolean = state !in NON_EXPORTABLE_STATES

        const val SCHEMA_VERSION: Int = 2
        const val EXPORT_VERSION: Int = 2
        const val MAX_ARCHIVE_BYTES: Long = 32L * 1024L * 1024L
        const val SOURCE_REVISION_UNKNOWN: String = "not_recorded"
        const val PRIVACY_MODE: String = "local_only_no_network"
        const val MANIFEST_ENTRY: String = "manifest.json"
        const val CHECKSUMS_ENTRY: String = "checksums.sha256"
        const val REPORT_ENTRY: String = "report.md"

        private const val MAX_SECTION_BYTES: Int = 4 * 1024 * 1024
        private const val MAX_TELEMETRY_ROWS: Int = 5_000
        private const val MAX_FAILURE_FILES: Int = 200
        private const val MAX_REPRODUCTION_FILES: Int = 200
        private const val MAX_ARTIFACT_FILES: Int = 200
        private const val MAX_REMOTE_EVENT_ROWS: Int = 1_000
        private const val MAX_REPORT_METRIC_DELTAS: Int = 3
        private const val REAL_SUMMARY_RESERVE_BYTES: Int = 3 * 1024
        private const val ZIP_WRITE_CHUNK_BYTES: Int = 64 * 1024

        private val NON_EXPORTABLE_STATES = setOf(
            SessionState.RUNNING,
            SessionState.PAUSING,
            SessionState.STOPPING,
        )

        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }

    private fun appendRealEvidenceSummary(
        base: String,
        detail: SessionDetail,
        snapshot: RealEvidenceExportSnapshot?,
        outputPreviewCount: Int,
    ): String {
        if (snapshot == null) return base
        val binding = snapshot.binding
        return buildString {
            append(base.trimEnd())
            append("\n\n## Real-run reproduction data\n\n")
            append("The archive contains `real-plan.json` and `remote-events.jsonl` from the Lab Room snapshot. ")
            append("Their presence records a plan or imported remote facts; it does not establish that native inference completed.\n")
            val plannedItems = detail.queue.size
            val resolvedItems = detail.queue.count { it.state.name == "DONE" || it.state.name == "SKIPPED" }
            val attempts = detail.queue.sumOf { it.attempts.size }
            append("Coverage: ").append(resolvedItems).append("/").append(plannedItems)
                .append(" queue items resolved; ").append(attempts).append(" local attempts; ")
                .append(snapshot.events.size).append(" imported remote events; see coverage.json for the status and reason.\n")
            append(qualificationSummary(snapshot))
            if (binding?.serverEpoch == null || binding.remoteRunId == null) {
                append("Remote execution binding: unavailable (`REMOTE_RUN_NOT_ACCEPTED`).\n")
            } else {
                append("Remote execution binding: recorded; imported event cursor ")
                append(binding.highestImportedSequence)
                append(".\n")
            }
            append("Qualification records: ").append(snapshot.qualifications.size).append("; output artifact records: ")
            append(snapshot.artifacts.size).append(". `artifacts/index.json` contains bounded metadata only; ")
            append(outputPreviewCount).append(" fixed-fixture output previews are included when complete and hash-verified. ")
            append("Model weights are never included; opaque artifact transfer is unavailable.\n")
        }
    }

}

internal fun qualificationSummary(snapshot: RealEvidenceExportSnapshot): String = buildString {
    val knownStatuses = setOf(
        "UNTESTED", "PARTIAL", "QUALIFIED", "REJECTED_FOR_PROFILE", "INCONCLUSIVE", "STALE",
        "UNSUPPORTED_CONFIGURATION",
    )
    val counts = snapshot.qualifications.groupingBy { row ->
        row.status.takeIf { it in knownStatuses } ?: "UNKNOWN_STATUS"
    }.eachCount().toSortedMap()
    val excluded = snapshot.qualifications.filter { it.status == "REJECTED_FOR_PROFILE" }
    append("Exact-scope exclusions: ").append(excluded.size)
        .append(" (REJECTED_FOR_PROFILE only; unknown statuses are not exclusions).\n")
    excluded.take(10).forEach { row ->
        append("- scope ").append(row.scopeDigestSha256.safeReportToken(16))
            .append(" / policy ").append(row.policyDigestSha256.safeReportToken(12))
            .append("; retained session ").append(row.sessionId.safeReportToken(48)).append(".\n")
    }
    if (excluded.size > 10) {
        append("- ").append(excluded.size - 10)
            .append(" additional exact-scope exclusions are listed in qualification.json.\n")
    }
    append("Qualification status counts: ")
        .append(counts.entries.joinToString { (status, count) -> "$status=$count" }.ifEmpty { "none" })
        .append("; full evidence and reasons are in qualification.json.\n")
}

private fun String.safeReportToken(maxLength: Int): String =
    filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(maxLength).ifEmpty { "unknown" }
