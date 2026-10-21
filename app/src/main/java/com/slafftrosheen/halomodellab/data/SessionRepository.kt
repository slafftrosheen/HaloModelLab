package com.slafftrosheen.halomodellab.data

import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.DeadlineReason
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.domain.AttemptResult
import kotlinx.coroutines.flow.Flow

sealed interface CreateSessionResult {
    data class Created(val sessionId: String) : CreateSessionResult

    /** Another non-terminal session exists; the caller must finish it first. */
    data class Blocked(
        val existingSessionId: String,
        val existingState: SessionState,
    ) : CreateSessionResult
}

sealed interface BeginAttempt {
    data class Ready(
        val request: BenchmarkRequest,
        val queueItemId: String,
        val attemptNumber: Int,
        val deadlineReason: DeadlineReason,
    ) : BeginAttempt

    /** Every queue item is resolved. */
    data object NoWork : BeginAttempt

    /** The session may not continue; the reason is a stable code, not a localized message. */
    data class Blocked(val reason: String) : BeginAttempt
}

data class RecoveryReport(
    val recoveredSessionIds: List<String>,
    val interruptedAttemptIds: List<String>,
    val needsDecisionItemIds: List<String>,
)

/**
 * Authoritative session store.
 *
 * Every method that changes state commits its state change, its event and its queue item
 * update in one transaction before returning. A storage exception propagates: callers must
 * stop the queue instead of reporting success.
 */
interface SessionRepository {
    fun observeOpenSession(): Flow<SessionDetail?>
    fun observeHistory(): Flow<List<SessionSummary>>
    fun observeSession(sessionId: String): Flow<SessionDetail?>

    /** One consistent snapshot for the runner and, later, the exporter. */
    suspend fun sessionDetail(sessionId: String): SessionDetail?

    /** Records a diagnostic event that is not tied to an attempt (conditions, recovery). */
    suspend fun recordSessionEvent(sessionId: String, code: String)

    /** A null measurement must carry a reason; it is never replaced by zero. */
    suspend fun recordTelemetry(
        sessionId: String,
        attemptId: String?,
        name: String,
        value: Double?,
        unit: String,
        scope: String,
        origin: EvidenceOrigin,
        unavailableReason: String?,
    )

    suspend fun createSession(plan: SessionPlan): CreateSessionResult

    /** Claims the session slot and moves the session to RUNNING. Idempotent for the owner. */
    suspend fun claimSession(sessionId: String, ownerInstanceId: String): Boolean

    suspend fun beginAttempt(sessionId: String, ownerInstanceId: String): BeginAttempt

    suspend fun recordStage(attemptId: String, stage: TestStage, code: String)

    /** Guarded terminal write. Returns false for a late duplicate, without changing the result. */
    suspend fun finishAttempt(attemptId: String, result: AttemptResult): Boolean

    suspend fun requestPause(sessionId: String, ownerInstanceId: String): Boolean
    suspend fun enterPaused(sessionId: String, ownerInstanceId: String): SessionState?
    suspend fun requestStop(sessionId: String, ownerInstanceId: String): Boolean
    suspend fun enterCancelled(sessionId: String, ownerInstanceId: String, reasonCode: String): SessionState?
    suspend fun enterCompleted(sessionId: String, ownerInstanceId: String): SessionState?
    suspend fun enterFailed(sessionId: String, code: String, detail: String): SessionState?

    /** Cancels a DRAFT, PAUSED or INTERRUPTED session the user refuses to continue. */
    suspend fun cancelSession(sessionId: String, reasonCode: String): SessionState?

    /** Marks still-pending items SKIPPED with a stable reason; returns the number of items. */
    suspend fun skipRemaining(sessionId: String, reasonCode: String): Int

    suspend fun retryItem(queueItemId: String): Boolean
    suspend fun skipItem(queueItemId: String): Boolean

    /** One-time startup recovery that must run before any start command is accepted. */
    suspend fun recoverInterruptedOwner(newOwnerInstanceId: String): RecoveryReport
}
