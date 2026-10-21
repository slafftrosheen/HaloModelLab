package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.data.BeginAttempt
import com.slafftrosheen.halomodellab.data.CreateSessionResult
import com.slafftrosheen.halomodellab.data.RecoveryReport
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.DeadlineReason
import com.slafftrosheen.halomodellab.domain.EventView
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionCommand
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.SessionStateMachine
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.domain.TelemetryView
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.domain.isTerminal
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for the coordinator only. It mirrors the repository contract, not Room.
 * Timing that the real repository derives from the monotonic clock is not simulated here.
 */
class FakeSessionRepository : SessionRepository {

    var sessionId: String = "session-1"
    var state: SessionState = SessionState.DRAFT
        private set
    var owner: String? = null
        private set
    var plan: SessionPlan? = null
        private set

    var failRecordStage: Boolean = false
    var failFinishAttempt: Boolean = false
    var blockBudgetAfterFirstAttempt: Boolean = false

    val items = linkedMapOf<String, FakeItem>()
    val attempts = mutableListOf<FakeAttempt>()
    val events = mutableListOf<EventView>()
    val telemetry = mutableListOf<TelemetryView>()

    private var created: Boolean = false
    private var sequence: Long = 0L

    data class FakeItem(
        val id: String,
        val ordinal: Int,
        val scenarioId: String?,
        val role: ModelRole,
        val backend: ComputeBackend,
        val repeatIndex: Int,
        var state: QueueItemState,
    )

    data class FakeAttempt(
        val id: String,
        val queueItemId: String,
        val number: Int,
        val ownerInstanceId: String,
        var stage: TestStage,
        var outcome: AttemptOutcome?,
        var validity: ResultValidity?,
        var failure: FailureInfo?,
        var deadlineReason: DeadlineReason?,
        var elapsedMs: Long?,
    )

    fun codes(): List<String> = events.map { it.code }

    fun itemStates(): List<QueueItemState> = items.values.map { it.state }

    override suspend fun createSession(plan: SessionPlan): CreateSessionResult {
        if (created && !state.isTerminal()) {
            return CreateSessionResult.Blocked(sessionId, state)
        }
        this.plan = plan
        state = SessionState.DRAFT
        owner = null
        created = true
        items.clear()
        attempts.clear()
        events.clear()
        telemetry.clear()
        sequence = 0L
        plan.items.forEachIndexed { index, planned ->
            val id = "item-$index"
            items[id] = FakeItem(
                id = id,
                ordinal = index,
                scenarioId = planned.scenarioId,
                role = planned.role,
                backend = planned.requestedBackend,
                repeatIndex = planned.repeatIndex,
                state = QueueItemState.PENDING,
            )
        }
        appendEvent("SESSION_CREATED", null, null)
        return CreateSessionResult.Created(sessionId)
    }

    override suspend fun claimSession(sessionId: String, ownerInstanceId: String): Boolean {
        if (sessionId != this.sessionId) return false
        if (state != SessionState.DRAFT && state != SessionState.PAUSED && state != SessionState.INTERRUPTED) {
            return false
        }
        state = SessionState.RUNNING
        owner = ownerInstanceId
        appendEvent("SESSION_CLAIMED", null, null)
        return true
    }

    override suspend fun beginAttempt(sessionId: String, ownerInstanceId: String): BeginAttempt {
        if (sessionId != this.sessionId) return BeginAttempt.Blocked("SESSION_MISSING")
        if (state != SessionState.RUNNING) return BeginAttempt.Blocked("SESSION_NOT_RUNNING")
        if (owner != ownerInstanceId) return BeginAttempt.Blocked("OWNER_MISMATCH")
        if (blockBudgetAfterFirstAttempt && attempts.isNotEmpty()) {
            return BeginAttempt.Blocked("SESSION_BUDGET_EXHAUSTED")
        }

        val item = items.values.firstOrNull { it.state == QueueItemState.PENDING }
        if (item == null) {
            return if (items.values.any { it.state == QueueItemState.NEEDS_DECISION }) {
                BeginAttempt.Blocked("NEEDS_DECISION")
            } else {
                BeginAttempt.NoWork
            }
        }

        item.state = QueueItemState.ACTIVE
        val attempt = FakeAttempt(
            id = UUID.randomUUID().toString(),
            queueItemId = item.id,
            number = attempts.count { it.queueItemId == item.id } + 1,
            ownerInstanceId = ownerInstanceId,
            stage = TestStage.PREFLIGHT,
            outcome = null,
            validity = null,
            failure = null,
            deadlineReason = DeadlineReason.CASE_TIMEOUT,
            elapsedMs = null,
        )
        attempts += attempt
        appendEvent("BEFORE_EXECUTION", attempt.id, TestStage.PREFLIGHT)

        val activePlan = checkNotNull(plan)
        return BeginAttempt.Ready(
            request = BenchmarkRequest(
                sessionId = sessionId,
                attemptId = attempt.id,
                subjectId = item.scenarioId ?: "demo/success",
                caseId = item.scenarioId ?: "case",
                caseTimeoutMs = activePlan.caseTimeoutMs,
                origin = activePlan.origin,
                role = item.role,
                requestedBackend = item.backend,
                scenarioId = item.scenarioId,
            ),
            queueItemId = item.id,
            attemptNumber = attempt.number,
            deadlineReason = attempt.deadlineReason ?: DeadlineReason.CASE_TIMEOUT,
        )
    }

    override suspend fun recordStage(attemptId: String, stage: TestStage, code: String) {
        if (failRecordStage) throw IllegalStateException("Fake storage failure")
        val attempt = attempts.firstOrNull { it.id == attemptId } ?: error("attempt missing")
        if (attempt.outcome != null) {
            appendEvent("LATE_STAGE_IGNORED", attemptId, stage)
            return
        }
        attempt.stage = stage
        appendEvent(code, attemptId, stage)
    }

    override suspend fun finishAttempt(attemptId: String, result: AttemptResult): Boolean {
        if (failFinishAttempt) throw IllegalStateException("Fake finish failure")
        val attempt = attempts.firstOrNull { it.id == attemptId } ?: return false
        if (attempt.outcome != null) {
            appendEvent("LATE_RESULT_IGNORED", attemptId, null)
            return false
        }
        attempt.outcome = result.outcome
        attempt.validity = result.validity
        attempt.failure = result.failure
        items[attempt.queueItemId]?.state = SessionStateMachine.queueItemStateFor(result.outcome)
        appendEvent("ATTEMPT_FINISHED_${result.outcome.name}", attemptId, null)
        return true
    }

    override suspend fun requestPause(sessionId: String, ownerInstanceId: String): Boolean =
        transition(sessionId, ownerInstanceId, SessionCommand.PAUSE_REQUESTED, "SESSION_PAUSE_REQUESTED")

    override suspend fun enterPaused(sessionId: String, ownerInstanceId: String): SessionState? {
        if (sessionId != this.sessionId || owner != ownerInstanceId) return null
        val allResolved = items.values.all {
            it.state == QueueItemState.DONE || it.state == QueueItemState.SKIPPED
        }
        val next = SessionStateMachine.next(state, SessionCommand.PAUSE_APPLIED, allResolved) ?: return null
        state = next
        owner = null
        appendEvent(if (next == SessionState.COMPLETED) "SESSION_COMPLETED" else "SESSION_PAUSED", null, null)
        return next
    }

    override suspend fun requestStop(sessionId: String, ownerInstanceId: String): Boolean =
        transition(sessionId, ownerInstanceId, SessionCommand.STOP_REQUESTED, "SESSION_STOP_REQUESTED")

    override suspend fun enterCancelled(
        sessionId: String,
        ownerInstanceId: String,
        reasonCode: String,
    ): SessionState? {
        if (sessionId != this.sessionId || owner != ownerInstanceId) return null
        val next = SessionStateMachine.next(state, SessionCommand.STOP_APPLIED) ?: return null
        closeOpenAttempts(AttemptOutcome.CANCELLED, reasonCode)
        state = next
        owner = null
        appendEvent("SESSION_CANCELLED_$reasonCode", null, null)
        return next
    }

    override suspend fun enterCompleted(sessionId: String, ownerInstanceId: String): SessionState? {
        if (sessionId != this.sessionId || owner != ownerInstanceId) return null
        val next = SessionStateMachine.next(state, SessionCommand.QUEUE_EXHAUSTED) ?: return null
        state = next
        owner = null
        appendEvent("SESSION_COMPLETED", null, null)
        return next
    }

    override suspend fun enterFailed(sessionId: String, code: String, detail: String): SessionState? {
        if (sessionId != this.sessionId) return null
        val next = SessionStateMachine.next(state, SessionCommand.INFRASTRUCTURE_FAILURE) ?: return null
        closeOpenAttempts(AttemptOutcome.INTERRUPTED, code)
        state = next
        owner = null
        appendEvent("SESSION_FAILED_$code", null, null)
        return next
    }

    override suspend fun cancelSession(sessionId: String, reasonCode: String): SessionState? {
        if (sessionId != this.sessionId) return null
        val next = SessionStateMachine.next(state, SessionCommand.CANCEL) ?: return null
        state = next
        owner = null
        appendEvent("SESSION_CANCELLED_$reasonCode", null, null)
        return next
    }

    override suspend fun skipRemaining(sessionId: String, reasonCode: String): Int {
        if (sessionId != this.sessionId) return 0
        var skipped = 0
        items.values.filter { it.state == QueueItemState.PENDING }.forEach {
            it.state = QueueItemState.SKIPPED
            skipped += 1
        }
        if (skipped > 0) appendEvent("ITEMS_SKIPPED_$reasonCode", null, null)
        return skipped
    }

    override suspend fun retryItem(queueItemId: String): Boolean {
        val item = items[queueItemId] ?: return false
        if (item.state != QueueItemState.NEEDS_DECISION) return false
        item.state = QueueItemState.PENDING
        appendEvent("QUEUE_ITEM_RETRY_PLANNED", null, null)
        return true
    }

    override suspend fun skipItem(queueItemId: String): Boolean {
        val item = items[queueItemId] ?: return false
        if (item.state != QueueItemState.NEEDS_DECISION) return false
        item.state = QueueItemState.SKIPPED
        appendEvent("QUEUE_ITEM_SKIPPED_AFTER_INTERRUPTION", null, null)
        return true
    }

    override suspend fun recoverInterruptedOwner(newOwnerInstanceId: String): RecoveryReport =
        RecoveryReport(emptyList(), emptyList(), emptyList())

    override suspend fun sessionDetail(sessionId: String): SessionDetail? {
        if (sessionId != this.sessionId) return null
        val activePlan = plan ?: return null
        val queue = items.values.map { item ->
            QueueItemView(
                queueItemId = item.id,
                ordinal = item.ordinal,
                subjectId = item.scenarioId ?: "demo/success",
                scenarioId = item.scenarioId,
                state = item.state,
                repeatIndex = item.repeatIndex,
                role = item.role,
                requestedBackend = item.backend,
                attempts = attempts.filter { it.queueItemId == item.id }.map { it.toView() },
            )
        }
        return SessionDetail(
            summary = SessionSummary(
                sessionId = sessionId,
                state = state,
                origin = activePlan.origin,
                engineId = activePlan.engineId,
                profileId = activePlan.profileId,
                createdAtWallTimeMs = 0L,
                finishedWallTimeMs = null,
                itemCount = items.size,
                completedItemCount = items.values.count {
                    it.state == QueueItemState.DONE || it.state == QueueItemState.SKIPPED
                },
            ),
            queue = queue,
            events = events.toList(),
            telemetry = telemetry.toList(),
            caseTimeoutMs = activePlan.caseTimeoutMs,
            budgetMs = activePlan.budgetMs,
            repeatCount = activePlan.repeatCount,
            requireCharging = activePlan.requireCharging,
            persistedActiveMs = 0L,
            sessionFailure = null,
            planJson = activePlan.encode(),
            openAttemptId = attempts.firstOrNull { it.outcome == null }?.id,
        )
    }

    override suspend fun recordSessionEvent(sessionId: String, code: String) {
        appendEvent(code, null, null)
    }

    override suspend fun recordTelemetry(
        sessionId: String,
        attemptId: String?,
        name: String,
        value: Double?,
        unit: String,
        scope: String,
        origin: EvidenceOrigin,
        unavailableReason: String?,
    ) {
        telemetry += TelemetryView(
            attemptId = attemptId,
            name = name,
            value = value,
            unit = unit,
            scope = scope,
            origin = origin,
            unavailableReason = unavailableReason,
            wallTimeMs = 0L,
        )
    }

    override fun observeOpenSession(): Flow<SessionDetail?> = flowOf(null)

    override fun observeHistory(): Flow<List<SessionSummary>> = flowOf(emptyList())

    override fun observeSession(sessionId: String): Flow<SessionDetail?> = flowOf(null)

    private fun transition(
        sessionId: String,
        ownerInstanceId: String,
        command: SessionCommand,
        code: String,
    ): Boolean {
        if (sessionId != this.sessionId || owner != ownerInstanceId) return false
        val next = SessionStateMachine.next(state, command) ?: return false
        state = next
        appendEvent(code, null, null)
        return true
    }

    private fun closeOpenAttempts(outcome: AttemptOutcome, reasonCode: String) {
        attempts.filter { it.outcome == null }.forEach { attempt ->
            attempt.outcome = outcome
            attempt.validity = ResultValidity.INVALID
            attempt.failure = FailureInfo(
                code = reasonCode,
                domain = com.slafftrosheen.halomodellab.domain.FailureDomain.HARNESS,
                confidence = com.slafftrosheen.halomodellab.domain.EvidenceConfidence.OBSERVED,
                detail = "Closed by the repository double.",
            )
            items[attempt.queueItemId]?.state = SessionStateMachine.queueItemStateFor(outcome)
        }
    }

    private fun appendEvent(code: String, attemptId: String?, stage: TestStage?) {
        sequence += 1
        events += EventView(
            seq = sequence,
            attemptId = attemptId,
            stage = stage,
            code = code,
            wallTimeMs = 0L,
            elapsedTimeMs = null,
            ownerInstanceId = owner ?: "fake",
        )
    }

    private fun FakeAttempt.toView(): AttemptView = AttemptView(
        attemptId = id,
        number = number,
        stage = stage,
        outcome = outcome,
        validity = validity,
        failure = failure,
        deadlineReason = deadlineReason,
        ownerInstanceId = ownerInstanceId,
        startedWallTimeMs = 0L,
        finishedWallTimeMs = null,
        elapsedMs = elapsedMs,
    )
}
