package com.slafftrosheen.halomodellab.domain

/**
 * Read models for UI and later export. Framework-free on purpose.
 *
 * A null measurement stays null together with its reason; it is never replaced by zero.
 */
data class AttemptView(
    val attemptId: String,
    val number: Int,
    val stage: TestStage,
    val outcome: AttemptOutcome?,
    val validity: ResultValidity?,
    val failure: FailureInfo?,
    val deadlineReason: DeadlineReason?,
    val ownerInstanceId: String,
    val startedWallTimeMs: Long,
    val finishedWallTimeMs: Long?,
    val elapsedMs: Long?,
)

data class QueueItemView(
    val queueItemId: String,
    val ordinal: Int,
    val subjectId: String,
    val scenarioId: String?,
    val state: QueueItemState,
    val repeatIndex: Int,
    val role: ModelRole,
    val requestedBackend: ComputeBackend,
    val attempts: List<AttemptView>,
)

data class EventView(
    val seq: Long,
    val attemptId: String?,
    val stage: TestStage?,
    val code: String,
    val wallTimeMs: Long,
    val elapsedTimeMs: Long?,
    val ownerInstanceId: String,
)

data class TelemetryView(
    val attemptId: String?,
    val name: String,
    val value: Double?,
    val unit: String,
    val scope: String,
    val origin: EvidenceOrigin,
    val unavailableReason: String?,
    val wallTimeMs: Long,
)

data class SessionSummary(
    val sessionId: String,
    val state: SessionState,
    val origin: EvidenceOrigin,
    val engineId: String,
    val profileId: String,
    val createdAtWallTimeMs: Long,
    val finishedWallTimeMs: Long?,
    val itemCount: Int,
    val completedItemCount: Int,
)

data class SessionDetail(
    val summary: SessionSummary,
    val queue: List<QueueItemView>,
    val events: List<EventView>,
    val telemetry: List<TelemetryView>,
    val caseTimeoutMs: Long,
    val budgetMs: Long,
    val repeatCount: Int,
    val requireCharging: Boolean,
    val persistedActiveMs: Long,
    val sessionFailure: FailureInfo?,
    val planJson: String,
    val openAttemptId: String?,
)
