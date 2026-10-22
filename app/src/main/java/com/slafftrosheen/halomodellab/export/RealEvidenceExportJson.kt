package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import com.slafftrosheen.halomodellab.data.RemoteEventEntity
import com.slafftrosheen.halomodellab.data.ConfigurationSnapshotEntity
import com.slafftrosheen.halomodellab.bridge.BridgeEventKind
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.catalog.FastSmokePlanFactory
import com.slafftrosheen.halomodellab.profiles.TextProfileRegistry
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.security.MessageDigest

/** Serializes Lab-owned evidence only; event payloads remain strings for safe bounded parsing. */
internal object RealEvidenceExportJson {
    data class VerifiedOutputPreview(val attemptId: String, val text: String, val sha256: String)

    /** Returns a runtime only when every terminal event is hash-valid and agrees. */
    fun consistentTerminalRuntimeVersion(snapshot: RealEvidenceExportSnapshot?): String? {
        val events = snapshot?.events.orEmpty()
        if (events.any { sha256(it.payloadJson.toByteArray(Charsets.UTF_8)) != it.payloadHashSha256 }) return null
        val parsedEvents = events.mapNotNull { event ->
            runCatching {
                val reader = JsonReader.of(Buffer().writeUtf8(event.payloadJson))
                reader.isLenient = false
                reader.beginObject()
                var kind: String? = null
                var runtime: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "kind" -> kind = reader.nextString()
                        "runtimeVersion" -> if (reader.peek() == JsonReader.Token.STRING) runtime = reader.nextString() else reader.skipValue()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonReader.Token.END_DOCUMENT)
                kind to runtime
            }.getOrNull()
        }
        if (parsedEvents.size != events.size) return null
        val terminalFacts = parsedEvents.filter { it.first == BridgeEventKind.ATTEMPT_TERMINAL.name }.map { it.second }
        if (terminalFacts.isEmpty() || terminalFacts.any { it.isNullOrBlank() }) return null
        return terminalFacts.filterNotNull().distinct().singleOrNull()
    }

    fun plan(snapshot: RealEvidenceExportSnapshot, canonicalPlanJson: String): ByteArray = build {
        beginObject()
        name("schemaVersion").value(1)
        name("availabilityCode").value(if (snapshot.plan == null) "PLAN_SNAPSHOT_MISSING" else "PLAN_SNAPSHOT_ONLY")
        name("planDigestSha256").value(snapshot.plan?.planDigestSha256)
        name("planCanonicalJson").value(canonicalPlanJson.takeIf { snapshot.plan != null })
        name("planSchemaVersion").value(snapshot.plan?.planSchemaVersion?.toLong())
        name("protocolMajor").value(snapshot.plan?.protocolMajor?.toLong())
        name("protocolMinor").value(snapshot.plan?.protocolMinor?.toLong())
        name("fixtureManifestSha256").value(snapshot.plan?.fixtureManifestSha256)
        name("qualificationPolicySha256").value(snapshot.plan?.qualificationPolicySha256)
        name("capabilitySnapshotJson").value(snapshot.plan?.capabilitySnapshotJson)
        name("models").beginArray()
        snapshot.models.forEach { model ->
            beginObject()
            name("providerId").value(model.providerId)
            name("modelId").value(model.modelId)
            name("generationId").value(model.generationId)
            name("manifestSha256").value(model.manifestSha256)
            name("manifestJson").value(model.manifestJson)
            name("availabilityCode").value(model.availabilityCode)
            endObject()
        }
        endArray()
        name("configurations").beginArray()
        snapshot.configurations.forEach { configuration ->
            beginObject()
            name("queueItemId").value(configuration.queueItemId)
            name("requestedBackend").value(configuration.requestedBackend)
            name("requestedConfigurationJson").value(configuration.requestedConfigurationJson)
            name("effectiveBackend").value(configuration.effectiveBackend)
            name("executionScopeJson").value(configuration.executionScopeJson)
            name("scopeDigestSha256").value(configuration.scopeDigestSha256)
            endObject()
        }
        endArray()
        name("remoteBinding").beginObject()
        name("clientRequestId").value(snapshot.binding?.clientRequestId)
        name("requestHashSha256").value(snapshot.binding?.requestHashSha256)
        name("serverEpoch").value(snapshot.binding?.serverEpoch)
        name("remoteRunId").value(snapshot.binding?.remoteRunId)
        name("highestImportedSequence").value(snapshot.binding?.highestImportedSequence)
        endObject()
        name("artifacts").beginArray()
        snapshot.artifacts.forEach { artifact ->
            beginObject()
            name("attemptId").value(artifact.attemptId)
            name("artifactKind").value(artifact.artifactKind)
            name("mimeType").value(artifact.mimeType)
            name("byteCount").value(artifact.byteCount)
            name("sha256").value(artifact.sha256)
            name("availabilityCode").value(artifact.availabilityCode)
            name("reasonCode").value(artifact.reasonCode)
            endObject()
        }
        endArray()
        name("qualifications").beginArray()
        snapshot.qualifications.forEach { qualification ->
            beginObject()
            name("scopeDigestSha256").value(qualification.scopeDigestSha256)
            name("policyDigestSha256").value(qualification.policyDigestSha256)
            name("status").value(qualification.status)
            name("evidenceJson").value(qualification.evidenceJson)
            endObject()
        }
        endArray()
        name("truncatedSections").beginArray()
        snapshot.truncatedSections.sorted().forEach { value(it) }
        endArray()
        endObject()
    }

    fun remoteEvent(event: RemoteEventEntity): String = buildString {
        append('{')
        append("\"serverEpoch\":").append(quote(event.serverEpoch)).append(',')
        append("\"remoteRunId\":").append(quote(event.remoteRunId)).append(',')
        append("\"sequence\":").append(event.remoteSequence).append(',')
        append("\"attemptId\":").append(event.attemptId?.let(::quote) ?: "null").append(',')
        append("\"payloadSha256\":").append(quote(event.payloadHashSha256)).append(',')
        append("\"payloadJson\":").append(quote(event.payloadJson)).append('}')
    }

    fun coverage(detail: SessionDetail, snapshot: RealEvidenceExportSnapshot?): ByteArray = build {
        val planned = detail.queue.size
        val resolved = detail.queue.count { it.state.name == "DONE" || it.state.name == "SKIPPED" }
        val attempts = detail.queue.sumOf { it.attempts.size }
        beginObject()
        name("schemaVersion").value(1)
        name("origin").value(detail.summary.origin.name)
        name("planItems").value(planned.toLong())
        name("resolvedItems").value(resolved.toLong())
        name("attempts").value(attempts.toLong())
        name("coverageStatus").value(
            when {
                detail.summary.origin.name == "SYNTHETIC" -> "SYNTHETIC_ONLY"
                snapshot == null || snapshot.events.isEmpty() -> "UNKNOWN_NO_REMOTE_EVENTS"
                else -> "REMOTE_EVIDENCE_AND_LOCAL_ATTEMPT_STATUS_EXPORTED"
            },
        )
        name("reasonCode").value(
            when {
                detail.summary.origin.name == "SYNTHETIC" -> "SYNTHETIC_EVIDENCE_NOT_QUALIFYING"
                snapshot?.events.isNullOrEmpty() -> "REAL_EXECUTION_NOT_ESTABLISHED"
                else -> "REMOTE_EVENTS_AND_LOCAL_ATTEMPT_OUTCOME_RETAINED_SEPARATELY"
            },
        )
        endObject()
    }

    fun qualifications(snapshot: RealEvidenceExportSnapshot?): ByteArray = build {
        beginObject()
        name("schemaVersion").value(1)
        name("availabilityCode").value(if (snapshot == null || snapshot.qualifications.isEmpty()) "NO_QUALIFICATION_RECORDS" else "LOCAL_RECORDS_ONLY")
        name("records").beginArray()
        snapshot?.qualifications?.forEach { qualification ->
            beginObject()
            name("scopeDigestSha256").value(qualification.scopeDigestSha256)
            name("policyDigestSha256").value(qualification.policyDigestSha256)
            name("status").value(qualification.status)
            name("evidenceJson").value(qualification.evidenceJson)
            endObject()
        }
        endArray()
        endObject()
    }

    fun artifactIndex(
        snapshot: RealEvidenceExportSnapshot?,
        outputPreviews: List<VerifiedOutputPreview>,
        omittedOutputPreviewCount: Int,
    ): ByteArray = build {
        beginObject()
        name("schemaVersion").value(1)
        name("payloadsIncluded").value(outputPreviews.isNotEmpty())
        name("opaqueArtifactPayloadsIncluded").value(false)
        name("opaqueArtifactUnavailableReason").value(if (snapshot == null) "NO_DEVICE_EVIDENCE_SNAPSHOT" else "BRIDGE_ARTIFACT_TRANSFER_UNAVAILABLE")
        name("outputPreviewUnavailableReason").value(if (outputPreviews.isEmpty()) "NO_HASH_VERIFIED_ALLOWLISTED_OUTPUT_PREVIEW" else null)
        name("entries").beginArray()
        outputPreviews.take(MAX_ARTIFACT_INDEX_ROWS).forEachIndexed { index, preview ->
            beginObject()
            name("generatedArtifactId").value("output-$index")
            name("attemptId").value(preview.attemptId)
            name("kind").value("FIXED_SMOKE_OUTPUT_PREVIEW")
            name("mimeType").value("text/plain; charset=utf-8")
            name("byteCount").value(preview.text.toByteArray(Charsets.UTF_8).size.toLong())
            name("sha256").value(preview.sha256)
            name("availabilityCode").value("INCLUDED_HASH_VERIFIED")
            name("reasonCode").nullValue()
            name("payloadFile").value("artifacts/output-$index.txt")
            endObject()
        }
        snapshot?.artifacts.orEmpty().take(MAX_ARTIFACT_INDEX_ROWS).forEachIndexed { index, artifact ->
            beginObject()
            name("generatedArtifactId").value("artifact-$index")
            name("attemptId").value(artifact.attemptId)
            name("kind").value(artifact.artifactKind.take(80))
            name("mimeType").value(artifact.mimeType.take(120))
            name("byteCount").value(artifact.byteCount)
            name("sha256").value(artifact.sha256)
            name("availabilityCode").value(artifact.availabilityCode.take(80))
            name("reasonCode").value(artifact.reasonCode?.take(80))
            name("payloadFile").nullValue()
            endObject()
        }
        endArray()
        name("truncated").value(
            (snapshot?.artifacts?.size ?: 0) > MAX_ARTIFACT_INDEX_ROWS ||
                omittedOutputPreviewCount > 0 || outputPreviews.size > MAX_ARTIFACT_INDEX_ROWS,
        )
        name("omittedOutputPreviewCount").value(omittedOutputPreviewCount.toLong())
        endObject()
    }

    /** Exports only the complete, hash-verified output of the packaged fixed FAST smoke fixture. */
    fun verifiedFixedSmokeOutputPreviews(
        detail: SessionDetail,
        snapshot: RealEvidenceExportSnapshot?,
    ): List<VerifiedOutputPreview> {
        if (detail.summary.origin != EvidenceOrigin.DEVICE_MEASUREMENT ||
            detail.summary.profileId != "halo-fast-smoke-cpu-v1" || snapshot == null
        ) return emptyList()
        if (!hasFixedSmokeFixture(snapshot)) return emptyList()
        val onlyCase = detail.queue.singleOrNull()?.takeIf { item ->
            item.scenarioId == FastSmokePlanFactory.CASE_ID && item.role.name == "FAST" &&
                item.requestedBackend.name == "CPU" && item.subjectId.startsWith("provider.litert.local/")
        } ?: return emptyList()

        val allowedAttemptIds = onlyCase.attempts.map { it.attemptId }.toSet()
        return snapshot.events.mapNotNull { event ->
            if (event.attemptId !in allowedAttemptIds ||
                sha256(event.payloadJson.toByteArray(Charsets.UTF_8)) != event.payloadHashSha256
            ) return@mapNotNull null
            runCatching {
                val reader = JsonReader.of(Buffer().writeUtf8(event.payloadJson))
                reader.isLenient = false
                reader.beginObject()
                var kind: String? = null
                var attemptId: String? = null
                var preview: String? = null
                var outputBytes: Long? = null
                var outputSha256: String? = null
                var truncated: Boolean? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "kind" -> kind = reader.nextString()
                        "attemptId" -> attemptId = if (reader.peek() == JsonReader.Token.NULL) { reader.nextNull<Unit>(); null } else reader.nextString()
                        "outputPreview" -> preview = if (reader.peek() == JsonReader.Token.NULL) { reader.nextNull<Unit>(); null } else reader.nextString()
                        "outputBytes" -> outputBytes = if (reader.peek() == JsonReader.Token.NULL) { reader.nextNull<Unit>(); null } else reader.nextLong()
                        "outputSha256" -> outputSha256 = if (reader.peek() == JsonReader.Token.NULL) { reader.nextNull<Unit>(); null } else reader.nextString()
                        "outputTruncated" -> truncated = if (reader.peek() == JsonReader.Token.NULL) { reader.nextNull<Unit>(); null } else reader.nextBoolean()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonReader.Token.END_DOCUMENT)
                val text = requireNotNull(preview)
                val bytes = text.toByteArray(Charsets.UTF_8)
                require(kind == BridgeEventKind.ATTEMPT_TERMINAL.name)
                require(attemptId == event.attemptId)
                require(truncated == false && text.length <= MAX_OUTPUT_PREVIEW_CHARS)
                require(bytes.size <= MAX_OUTPUT_PREVIEW_BYTES && outputBytes == bytes.size.toLong())
                val digest = requireNotNull(outputSha256)
                require(SHA256.matches(digest) && sha256(bytes) == digest)
                VerifiedOutputPreview(attemptId, text, digest)
            }.getOrNull()
        }.groupBy { it.attemptId }
            .mapNotNull { (_, candidates) -> candidates.distinctBy { it.sha256 }.singleOrNull() }
            .sortedBy { it.attemptId }
            .take(MAX_ARTIFACT_INDEX_ROWS)
    }

    fun hasFixedSmokeFixture(snapshot: RealEvidenceExportSnapshot?): Boolean {
        val fixture = TextProfileRegistry.resolve(FastSmokePlanFactory.SUITE_ID, FastSmokePlanFactory.SUITE_VERSION)
            ?: return false
        return snapshot?.plan?.fixtureManifestSha256 == fixture.fixtureManifestSha256()
    }

    fun comparison(
        previousSessionId: String? = null,
        comparison: SessionComparison? = null,
        unavailableReason: String = "NO_COMPATIBLE_PREVIOUS_SESSION_SELECTED",
    ): ByteArray = build {
        beginObject()
        name("schemaVersion").value(2)
        name("status").value(comparison?.status?.name ?: "NOT_AVAILABLE")
        name("reasonCode").value(comparison?.reasonCode ?: unavailableReason)
        name("previousSessionId").value(previousSessionId)
        name("newFailures").beginArray()
        comparison?.newFailures.orEmpty().sortedWith(FAILURE_ORDER).forEach { failure ->
            beginObject(); name("code").value(failure.code); name("domain").value(failure.domain)
            name("stage").value(failure.stage); endObject()
        }
        endArray()
        name("resolvedFailures").beginArray()
        comparison?.resolvedFailures.orEmpty().sortedWith(FAILURE_ORDER).forEach { failure ->
            beginObject(); name("code").value(failure.code); name("domain").value(failure.domain)
            name("stage").value(failure.stage); endObject()
        }
        endArray()
        name("persistentFailures").beginArray()
        comparison?.persistentFailures.orEmpty().sortedWith(FAILURE_ORDER).forEach { failure ->
            beginObject(); name("code").value(failure.code); name("domain").value(failure.domain)
            name("stage").value(failure.stage); endObject()
        }
        endArray()
        val metricRows = comparison?.metricDeltas.orEmpty()
        name("metricDeltasTruncated").value(metricRows.size > MAX_METRIC_DELTA_ROWS)
        name("metricDeltas").beginArray()
        metricRows.take(MAX_METRIC_DELTA_ROWS).forEach { metric ->
            beginObject()
            name("name").value(metric.key.name.take(120))
            name("unit").value(metric.key.unit.take(40))
            name("scope").value(metric.key.scope.take(120))
            name("subjectId").value(metric.key.subjectId.take(240))
            name("scenarioId").value(metric.key.scenarioId.take(120))
            name("role").value(metric.key.role.take(40))
            name("requestedBackend").value(metric.key.requestedBackend.take(40))
            name("currentValue").nullableDouble(metric.currentValue)
            name("previousValue").nullableDouble(metric.previousValue)
            name("delta").nullableDouble(metric.delta)
            name("unavailableReason").value(metric.unavailableReason)
            endObject()
        }
        endArray()
        endObject()
    }

    fun failureIndex(detail: SessionDetail): ByteArray = build {
        val failures = detail.queue.flatMap { item ->
            item.attempts.filter { it.failure != null }.map { attempt -> item to attempt }
        }
        beginObject()
        name("schemaVersion").value(1)
        name("entries").beginArray()
        failures.take(MAX_FAILURE_INDEX_ROWS).forEachIndexed { index, (item, attempt) ->
            beginObject()
            name("attemptId").value(attempt.attemptId)
            name("queueItemOrdinal").value(item.ordinal.toLong())
            name("stage").value(attempt.stage.name)
            name("failureCode").value(attempt.failure?.code)
            name("failureDomain").value(attempt.failure?.domain?.name)
            name("scopeDigestSha256").nullValue()
            val safeId = if (UUID_PATTERN.matches(attempt.attemptId)) attempt.attemptId else "attempt-$index"
            name("evidenceFile").value("failures/$safeId.json")
            endObject()
        }
        endArray()
        name("truncated").value(failures.size > MAX_FAILURE_INDEX_ROWS)
        endObject()
    }

    /** Minimal safe reproduction metadata; intentionally excludes raw paths and model bytes. */
    fun reproduction(
        item: QueueItemView,
        attempt: AttemptView,
        origin: EvidenceOrigin,
        configuration: ConfigurationSnapshotEntity?,
        snapshot: RealEvidenceExportSnapshot?,
    ): ByteArray {
        val subjectId = item.subjectId.take(MAX_REPRO_SUBJECT_CHARS)
        val scenarioId = item.scenarioId?.take(MAX_REPRO_SCENARIO_CHARS)
        val failureCode = attempt.failure?.code?.take(MAX_REPRO_CODE_CHARS)
        val fieldsTruncated = subjectId.length != item.subjectId.length ||
            scenarioId?.length != item.scenarioId?.length ||
            failureCode?.length != attempt.failure?.code?.length
        return build {
        beginObject()
        name("schemaVersion").value(1)
        name("sessionOrigin").value(origin.name)
        name("attemptId").value(attempt.attemptId)
        name("queueItemOrdinal").value(item.ordinal.toLong())
        name("subjectId").value(subjectId)
        name("scenarioId").value(scenarioId)
        name("role").value(item.role.name)
        name("requestedBackend").value(item.requestedBackend.name)
        name("repeatIndex").value(item.repeatIndex.toLong())
        name("stage").value(attempt.stage.name)
        name("outcome").value(attempt.outcome?.name)
        name("validity").value(attempt.validity?.name)
        name("failureCode").value(failureCode)
        name("failureDomain").value(attempt.failure?.domain?.name)
        name("startedWallTimeMs").value(attempt.startedWallTimeMs)
        name("finishedWallTimeMs").value(attempt.finishedWallTimeMs)
        name("elapsedMs").value(attempt.elapsedMs)
        name("configuredRequestedBackend").value(configuration?.requestedBackend)
        name("effectiveBackend").value(configuration?.effectiveBackend)
        name("executionScopeSha256").value(configuration?.scopeDigestSha256)
        name("planDigestSha256").value(snapshot?.plan?.planDigestSha256)
        name("fixtureManifestSha256").value(snapshot?.plan?.fixtureManifestSha256)
        name("qualificationPolicySha256").value(snapshot?.plan?.qualificationPolicySha256)
        name("modelArtifactPayloadStatus").value("MODEL_BYTES_NEVER_INCLUDED; FIXED_SMOKE_PREVIEW_IS_SEPARATE")
        name("fieldsTruncated").value(fieldsTruncated)
        endObject()
        }
    }

    fun failureClusters(detail: SessionDetail): ByteArray = build {
        beginObject()
        name("schemaVersion").value(1)
        name("grouping").value("code+domain+stage+subjectId+role+requestedBackend; current session only")
        name("clusters").beginArray()
        FailureClusterer.cluster(detail).forEach { cluster ->
            beginObject()
            name("failureCode").value(cluster.code)
            name("failureDomain").value(cluster.domain)
            name("stage").value(cluster.stage)
            name("subjectId").value(cluster.subjectId)
            name("role").value(cluster.role.name)
            name("requestedBackend").value(cluster.requestedBackend.name)
            name("count").value(cluster.attemptIds.size.toLong())
            name("firstObservedWallTimeMs").value(cluster.firstObservedWallTimeMs)
            name("lastObservedWallTimeMs").value(cluster.lastObservedWallTimeMs)
            name("attemptIds").beginArray()
            cluster.attemptIds.take(MAX_FAILURE_INDEX_ROWS).forEach { value(it) }
            endArray()
            name("attemptIdsTruncated").value(cluster.attemptIds.size > MAX_FAILURE_INDEX_ROWS)
            name("exampleAttemptId").value(cluster.exampleAttemptId)
            name("exampleDetail").value(cluster.exampleDetail)
            endObject()
        }
        endArray()
        endObject()
    }

    private inline fun build(block: JsonWriter.() -> Unit): ByteArray {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.serializeNulls = true
        writer.setIndent("  ")
        writer.block()
        writer.close()
        return buffer.readByteArray()
    }

    private fun quote(text: String): String {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.value(text)
        writer.close()
        return buffer.readUtf8()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun JsonWriter.nullableDouble(value: Double?) {
        if (value != null && value.isFinite()) value(value) else nullValue()
    }

    private const val MAX_FAILURE_INDEX_ROWS = 200
    private const val MAX_ARTIFACT_INDEX_ROWS = 200
    private const val MAX_OUTPUT_PREVIEW_CHARS = 512
    private const val MAX_OUTPUT_PREVIEW_BYTES = 2 * 1024
    private const val MAX_METRIC_DELTA_ROWS = 5_000
    private const val MAX_REPRO_SUBJECT_CHARS = 240
    private const val MAX_REPRO_SCENARIO_CHARS = 120
    private const val MAX_REPRO_CODE_CHARS = 80
    private val FAILURE_ORDER = compareBy<FailureSignature> { it.code }.thenBy { it.domain }.thenBy { it.stage }
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
