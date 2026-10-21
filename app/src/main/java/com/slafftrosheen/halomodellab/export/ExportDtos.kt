package com.slafftrosheen.halomodellab.export

/**
 * Export-only DTOs. They are deliberately separate from domain types and Room entities:
 * a schema change here never changes execution, and origin is copied, never recomputed.
 */
data class ManifestDto(
    val schemaVersion: Int,
    val exportVersion: Int,
    val application: ApplicationDto,
    val sessionId: String,
    val sessionState: String,
    val sessionOrigin: String,
    val createdAt: String,
    val snapshotEventSeq: Long,
    val runtime: RuntimeDto,
    val plan: PlanDto,
    val planSnapshot: String,
    val provenance: ProvenanceDto,
    val omissions: List<String>,
    val privacyMode: String,
    val files: List<FileEntryDto>,
)

data class ApplicationDto(
    val packageName: String,
    val versionName: String,
    val sourceRevision: String,
)

data class RuntimeDto(
    val bridgeStatus: String,
    val inferenceRuntime: String?,
)

data class PlanDto(
    val profileId: String,
    val engineId: String,
    val caseTimeoutMs: Long,
    val budgetMs: Long,
    val repeatCount: Int,
    val requireCharging: Boolean,
    val itemCount: Int,
)

data class ProvenanceDto(
    val evidenceOrigin: String,
    val engineId: String,
    val synthetic: Boolean,
)

data class FileEntryDto(
    val name: String,
    val sha256: String,
    val sizeBytes: Int,
)

data class ResultsDto(
    val schemaVersion: Int,
    val sessionId: String,
    val state: String,
    val origin: String,
    val engineId: String,
    val profileId: String,
    val createdAt: String,
    val finishedAt: String?,
    val persistedActiveMs: Long,
    val itemCount: Int,
    val completedItemCount: Int,
    val queue: List<QueueItemDto>,
)

data class QueueItemDto(
    val ordinal: Int,
    val subjectId: String,
    val scenarioId: String?,
    val state: String,
    val repeatIndex: Int,
    val role: String,
    val requestedBackend: String,
    val attempts: List<AttemptDto>,
)

data class AttemptDto(
    val attemptId: String,
    val number: Int,
    val ownerInstanceId: String,
    val stage: String,
    val outcome: String?,
    val validity: String?,
    val deadlineReason: String?,
    val startedWallTimeMs: Long,
    val finishedWallTimeMs: Long?,
    val elapsedMs: Long?,
    val failure: FailureDto?,
    val metrics: List<MetricDto>,
)

data class FailureDto(
    val code: String,
    val domain: String,
    val confidence: String,
    val detail: String,
)

data class MetricDto(
    val name: String,
    val value: Double?,
    val unit: String,
    val scope: String,
    val origin: String,
    val unavailableReason: String?,
)

data class FailureFileDto(
    val attemptId: String,
    val queueItemOrdinal: Int,
    val scenarioId: String?,
    val stage: String,
    val outcome: String?,
    val validity: String?,
    val deadlineReason: String?,
    val startedWallTimeMs: Long,
    val finishedWallTimeMs: Long?,
    val elapsedMs: Long?,
    val failure: FailureDto,
)

data class EventDto(
    val seq: Long,
    val attemptId: String?,
    val stage: String?,
    val code: String,
    val wallTimeMs: Long,
    val elapsedTimeMs: Long?,
    val ownerInstanceId: String,
)
