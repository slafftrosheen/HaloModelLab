package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import com.slafftrosheen.halomodellab.data.RemoteEventEntity
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.squareup.moshi.JsonWriter
import okio.Buffer

/** Serializes Lab-owned evidence only; event payloads remain strings for safe bounded parsing. */
internal object RealEvidenceExportJson {
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
                else -> "REMOTE_FACTS_IMPORTED_EXECUTION_COVERAGE_UNVERIFIED"
            },
        )
        name("reasonCode").value(
            when {
                detail.summary.origin.name == "SYNTHETIC" -> "SYNTHETIC_EVIDENCE_NOT_QUALIFYING"
                snapshot?.events.isNullOrEmpty() -> "REAL_EXECUTION_NOT_ESTABLISHED"
                else -> "REMOTE_TERMINAL_RECONCILIATION_NOT_IMPLEMENTED"
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

    fun comparison(): ByteArray = build {
        beginObject()
        name("schemaVersion").value(1)
        name("status").value("NOT_AVAILABLE")
        name("reasonCode").value("NO_COMPATIBLE_PREVIOUS_SESSION_SELECTED")
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

    private const val MAX_FAILURE_INDEX_ROWS = 200
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
