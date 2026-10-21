package com.slafftrosheen.halomodellab.export

import com.squareup.moshi.JsonWriter
import okio.Buffer

/**
 * Explicit serializers for the export DTOs.
 *
 * They use Moshi's JsonWriter (correct escaping, stable field order) instead of reflecting
 * over Kotlin classes, so archive bytes do not depend on kotlin-reflect metadata.
 * Field names and order are part of schemaVersion 1; change both together.
 */
internal object ExportJson {

    fun manifest(dto: ManifestDto): ByteArray = build {
        beginObject()
        name("schemaVersion").value(dto.schemaVersion)
        name("exportVersion").value(dto.exportVersion)
        name("application").valueApplication(dto.application)
        name("sessionId").value(dto.sessionId)
        name("sessionState").value(dto.sessionState)
        name("sessionOrigin").value(dto.sessionOrigin)
        name("createdAt").value(dto.createdAt)
        name("snapshotEventSeq").value(dto.snapshotEventSeq)
        name("runtime").valueRuntime(dto.runtime)
        name("plan").valuePlan(dto.plan)
        name("planSnapshot").value(dto.planSnapshot)
        name("provenance").valueProvenance(dto.provenance)
        name("omissions").valueStrings(dto.omissions)
        name("privacyMode").value(dto.privacyMode)
        name("files").valueFiles(dto.files)
        endObject()
    }

    fun results(dto: ResultsDto): ByteArray = build {
        beginObject()
        name("schemaVersion").value(dto.schemaVersion)
        name("sessionId").value(dto.sessionId)
        name("state").value(dto.state)
        name("origin").value(dto.origin)
        name("engineId").value(dto.engineId)
        name("profileId").value(dto.profileId)
        name("createdAt").value(dto.createdAt)
        name("finishedAt").value(dto.finishedAt)
        name("persistedActiveMs").value(dto.persistedActiveMs)
        name("itemCount").value(dto.itemCount)
        name("completedItemCount").value(dto.completedItemCount)
        name("queue").valueQueue(dto.queue)
        endObject()
    }

    fun failure(dto: FailureFileDto): ByteArray = build {
        beginObject()
        name("attemptId").value(dto.attemptId)
        name("queueItemOrdinal").value(dto.queueItemOrdinal)
        name("scenarioId").value(dto.scenarioId)
        name("stage").value(dto.stage)
        name("outcome").value(dto.outcome)
        name("validity").value(dto.validity)
        name("deadlineReason").value(dto.deadlineReason)
        name("startedWallTimeMs").value(dto.startedWallTimeMs)
        name("finishedWallTimeMs").value(dto.finishedWallTimeMs)
        name("elapsedMs").value(dto.elapsedMs)
        name("failure").valueFailure(dto.failure)
        endObject()
    }

    fun event(dto: EventDto): String {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.beginObject()
        writer.name("seq").value(dto.seq)
        writer.name("attemptId").value(dto.attemptId)
        writer.name("stage").value(dto.stage)
        writer.name("code").value(dto.code)
        writer.name("wallTimeMs").value(dto.wallTimeMs)
        writer.name("elapsedTimeMs").value(dto.elapsedTimeMs)
        writer.name("ownerInstanceId").value(dto.ownerInstanceId)
        writer.endObject()
        writer.close()
        // Buffer.close() is a no-op in okio; the written bytes stay readable.
        return buffer.readUtf8()
    }

    private inline fun build(block: JsonWriter.() -> Unit): ByteArray {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.setIndent("  ")
        writer.block()
        writer.close()
        return buffer.readByteArray()
    }

    private fun JsonWriter.valueApplication(dto: ApplicationDto) {
        beginObject()
        name("packageName").value(dto.packageName)
        name("versionName").value(dto.versionName)
        name("sourceRevision").value(dto.sourceRevision)
        endObject()
    }

    private fun JsonWriter.valueRuntime(dto: RuntimeDto) {
        beginObject()
        name("bridgeStatus").value(dto.bridgeStatus)
        name("inferenceRuntime").value(dto.inferenceRuntime)
        endObject()
    }

    private fun JsonWriter.valuePlan(dto: PlanDto) {
        beginObject()
        name("profileId").value(dto.profileId)
        name("engineId").value(dto.engineId)
        name("caseTimeoutMs").value(dto.caseTimeoutMs)
        name("budgetMs").value(dto.budgetMs)
        name("repeatCount").value(dto.repeatCount)
        name("requireCharging").value(dto.requireCharging)
        name("itemCount").value(dto.itemCount)
        endObject()
    }

    private fun JsonWriter.valueProvenance(dto: ProvenanceDto) {
        beginObject()
        name("evidenceOrigin").value(dto.evidenceOrigin)
        name("engineId").value(dto.engineId)
        name("synthetic").value(dto.synthetic)
        endObject()
    }

    private fun JsonWriter.valueFiles(entries: List<FileEntryDto>) {
        beginArray()
        entries.forEach { entry ->
            beginObject()
            name("name").value(entry.name)
            name("sha256").value(entry.sha256)
            name("sizeBytes").value(entry.sizeBytes)
            endObject()
        }
        endArray()
    }

    private fun JsonWriter.valueQueue(entries: List<QueueItemDto>) {
        beginArray()
        entries.forEach { item ->
            beginObject()
            name("ordinal").value(item.ordinal)
            name("subjectId").value(item.subjectId)
            name("scenarioId").value(item.scenarioId)
            name("state").value(item.state)
            name("repeatIndex").value(item.repeatIndex)
            name("role").value(item.role)
            name("requestedBackend").value(item.requestedBackend)
            name("attempts").valueAttempts(item.attempts)
            endObject()
        }
        endArray()
    }

    private fun JsonWriter.valueAttempts(attempts: List<AttemptDto>) {
        beginArray()
        attempts.forEach { attempt ->
            beginObject()
            name("attemptId").value(attempt.attemptId)
            name("number").value(attempt.number)
            name("ownerInstanceId").value(attempt.ownerInstanceId)
            name("stage").value(attempt.stage)
            name("outcome").value(attempt.outcome)
            name("validity").value(attempt.validity)
            name("deadlineReason").value(attempt.deadlineReason)
            name("startedWallTimeMs").value(attempt.startedWallTimeMs)
            name("finishedWallTimeMs").value(attempt.finishedWallTimeMs)
            name("elapsedMs").value(attempt.elapsedMs)
            name("failure").valueFailure(attempt.failure)
            name("metrics").valueMetrics(attempt.metrics)
            endObject()
        }
        endArray()
    }

    private fun JsonWriter.valueFailure(dto: FailureDto?) {
        if (dto == null) {
            nullValue()
            return
        }
        beginObject()
        name("code").value(dto.code)
        name("domain").value(dto.domain)
        name("confidence").value(dto.confidence)
        name("detail").value(dto.detail)
        endObject()
    }

    private fun JsonWriter.valueMetrics(metrics: List<MetricDto>) {
        beginArray()
        metrics.forEach { metric ->
            beginObject()
            name("name").value(metric.name)
            name("value").value(metric.value)
            name("unit").value(metric.unit)
            name("scope").value(metric.scope)
            name("origin").value(metric.origin)
            name("unavailableReason").value(metric.unavailableReason)
            endObject()
        }
        endArray()
    }

    private fun JsonWriter.valueStrings(values: List<String>) {
        beginArray()
        values.forEach { value(it) }
        endArray()
    }
}
