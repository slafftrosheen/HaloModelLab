package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.TelemetryView
import com.slafftrosheen.halomodellab.runner.LabClock
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    suspend fun export(sessionId: String): ExportResult = withContext(ioDispatcher) {
        val detail = repository.sessionDetail(sessionId)
            ?: return@withContext ExportResult.MissingSession(sessionId)

        if (detail.summary.state in NON_EXPORTABLE_STATES) {
            return@withContext ExportResult.NotExportable(detail.summary.state)
        }

        try {
            ExportResult.Success(writeArchive(detail))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ArchiveTooLargeException) {
            ExportResult.Failure(
                code = "EXPORT_TOO_LARGE",
                detail = tooLarge.message ?: "The archive exceeds the v0 limit.",
            )
        } catch (failure: Exception) {
            ExportResult.Failure(
                code = "EXPORT_FAILED",
                detail = failure.message ?: failure::class.java.simpleName,
            )
        }
    }

    private fun writeArchive(detail: SessionDetail): File {
        val omissions = mutableListOf<String>()
        val generatedAt = iso(clock.wallTimeMs())

        val payload = LinkedHashMap<String, ByteArray>()
        payload["results.json"] = ExportJson.results(toResults(detail))
        payload["events.jsonl"] = buildEventsJsonl(detail, omissions)
        payload["telemetry.csv"] = buildTelemetryCsv(detail, omissions)
        buildFailureFiles(detail, omissions).forEach { (name, bytes) -> payload[name] = bytes }
        payload["report.md"] = ReportFormatter
            .format(detail, generatedAt, omissions)
            .toByteArray(Charsets.UTF_8)

        val payloadBytes = payload.values.sumOf { it.size.toLong() }
        if (payloadBytes > MAX_ARCHIVE_BYTES) {
            throw ArchiveTooLargeException(
                "Payload is $payloadBytes bytes; the v0 archive limit is $MAX_ARCHIVE_BYTES.",
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
                bridgeStatus = "NOT_IMPLEMENTED",
                inferenceRuntime = null,
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
                "Archive is $totalBytes bytes; the v0 archive limit is $MAX_ARCHIVE_BYTES.",
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

    private fun writeZip(
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
                    val bytes = when (name) {
                        MANIFEST_ENTRY -> manifestBytes
                        CHECKSUMS_ENTRY -> checksumBytes
                        else -> payload.getValue(name)
                    }
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
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

    companion object {
        /** Single source of truth for the UI button and the exporter guard. */
        fun isExportable(state: SessionState): Boolean = state !in NON_EXPORTABLE_STATES

        const val SCHEMA_VERSION: Int = 1
        const val EXPORT_VERSION: Int = 1
        const val MAX_ARCHIVE_BYTES: Long = 32L * 1024L * 1024L
        const val SOURCE_REVISION_UNKNOWN: String = "not_recorded"
        const val PRIVACY_MODE: String = "local_only_no_network"
        const val MANIFEST_ENTRY: String = "manifest.json"
        const val CHECKSUMS_ENTRY: String = "checksums.sha256"
        const val REPORT_ENTRY: String = "report.md"

        private const val MAX_SECTION_BYTES: Int = 4 * 1024 * 1024
        private const val MAX_TELEMETRY_ROWS: Int = 5_000
        private const val MAX_FAILURE_FILES: Int = 200

        private val NON_EXPORTABLE_STATES = setOf(
            SessionState.RUNNING,
            SessionState.PAUSING,
            SessionState.STOPPING,
        )

        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }
}
