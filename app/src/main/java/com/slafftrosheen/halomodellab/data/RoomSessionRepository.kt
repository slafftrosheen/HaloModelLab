package com.slafftrosheen.halomodellab.data

import androidx.room.withTransaction
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.DeadlineReason
import com.slafftrosheen.halomodellab.domain.EventView
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
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
import com.slafftrosheen.halomodellab.domain.SessionStates
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.domain.TelemetryView
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.runner.LabClock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation.
 *
 * Invariants enforced here, not in the UI:
 * - one open session and one runner slot at a time;
 * - BEFORE_EXECUTION is committed before the engine is called;
 * - terminal attempt results are compare-and-set and are never rewritten;
 * - event sequence numbers are allocated inside the writing transaction;
 * - session duration uses the monotonic clock and survives a pause.
 */
class RoomSessionRepository(
    private val database: LabDatabase,
    private val clock: LabClock,
    private val processInstanceId: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {

    private val dao: LabDao
        get() = database.labDao()

    override fun observeHistory(): Flow<List<SessionSummary>> =
        dao.observeSummaryRows()
            .map { rows -> rows.map(::toSummary) }
            .flowOn(ioDispatcher)

    override fun observeSession(sessionId: String): Flow<SessionDetail?> {
        val dao = database.labDao()
        return combine(
            dao.observeSession(sessionId),
            dao.observeQueueItems(sessionId),
            dao.observeAttempts(sessionId),
            dao.observeEvents(sessionId),
            dao.observeTelemetry(sessionId),
        ) { session, items, attempts, events, telemetry ->
            session?.let { buildDetail(it, items, attempts, events, telemetry) }
        }.flowOn(ioDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpenSession(): Flow<SessionDetail?> =
        database.labDao()
            .observeOpenSession(SessionStates.TERMINAL_NAMES)
            .flatMapLatest { session ->
                if (session == null) flowOf(null) else observeSession(session.id)
            }
            .flowOn(ioDispatcher)

    override suspend fun sessionDetail(sessionId: String): SessionDetail? = withContext(ioDispatcher) {
        val session = dao.sessionById(sessionId) ?: return@withContext null
        buildDetail(
            session = session,
            items = dao.queueItems(sessionId),
            attempts = dao.attemptsForSession(sessionId),
            events = dao.events(sessionId),
            telemetry = dao.telemetry(sessionId),
        )
    }

    override suspend fun recordSessionEvent(sessionId: String, code: String) {
        withContext(ioDispatcher) {
            database.withTransaction {
                val owner = dao.sessionById(sessionId)?.ownerInstanceId ?: processInstanceId
                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = code,
                    ownerInstanceId = owner,
                )
            }
        }
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
        require(value != null || !unavailableReason.isNullOrBlank()) {
            "A missing telemetry value must carry a reason"
        }
        withContext(ioDispatcher) {
            dao.insertTelemetry(
                TelemetryEntity(
                    sessionId = sessionId,
                    attemptId = attemptId,
                    name = name,
                    value = value,
                    unit = unit,
                    scope = scope,
                    origin = origin.name,
                    unavailableReason = unavailableReason?.take(MAX_FAILURE_DETAIL_CHARS),
                    wallTimeMs = clock.wallTimeMs(),
                ),
            )
        }
    }

    override suspend fun createSession(plan: SessionPlan): CreateSessionResult =
        withContext(ioDispatcher) {
            database.withTransaction {
                dao.insertRunnerSlotIfMissing(RunnerSlotEntity.empty())

                val existing = dao.findOpenSession(SessionStates.OPEN_NAMES)
                if (existing != null) {
                    return@withTransaction CreateSessionResult.Blocked(
                        existingSessionId = existing.id,
                        existingState = SessionState.valueOf(existing.state),
                    )
                }

                val sessionId = UUID.randomUUID().toString()
                val session = SessionEntity(
                    id = sessionId,
                    state = SessionState.DRAFT.name,
                    planJson = plan.encode(),
                    origin = plan.origin.name,
                    profileId = plan.profileId,
                    engineId = plan.engineId,
                    caseTimeoutMs = plan.caseTimeoutMs,
                    budgetMs = plan.budgetMs,
                    repeatCount = plan.repeatCount,
                    requireCharging = plan.requireCharging,
                    createdAtWallTimeMs = clock.wallTimeMs(),
                    startedWallTimeMs = null,
                    finishedWallTimeMs = null,
                    persistedActiveMs = 0L,
                    activeStartedElapsedMs = null,
                    lastSeq = 0L,
                    failureCode = null,
                    failureDomain = null,
                    failureDetail = null,
                    ownerInstanceId = null,
                )

                val items = plan.items.mapIndexed { index, planned ->
                    QueueItemEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        ordinal = index,
                        subjectId = planned.subjectId,
                        scenarioId = planned.scenarioId,
                        role = planned.role.name,
                        requestedBackend = planned.requestedBackend.name,
                        state = QueueItemState.PENDING.name,
                        repeatIndex = planned.repeatIndex,
                    )
                }

                dao.insertSession(session)
                dao.insertQueueItems(items)
                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = "SESSION_CREATED",
                    ownerInstanceId = processInstanceId,
                )

                CreateSessionResult.Created(sessionId)
            }
        }

    override suspend fun claimSession(sessionId: String, ownerInstanceId: String): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                dao.insertRunnerSlotIfMissing(RunnerSlotEntity.empty())

                val session = dao.sessionById(sessionId) ?: return@withTransaction false
                val current = SessionState.valueOf(session.state)
                val startable = current == SessionState.DRAFT ||
                    current == SessionState.PAUSED ||
                    current == SessionState.INTERRUPTED
                if (!startable) return@withTransaction false

                if (dao.claimRunnerSlot(sessionId, ownerInstanceId) != 1) return@withTransaction false

                if (dao.updateSessionState(sessionId, session.state, SessionState.RUNNING.name) != 1) {
                    dao.releaseRunnerSlot(sessionId)
                    return@withTransaction false
                }

                val nowElapsed = clock.elapsedRealtimeMs()
                dao.updateOwner(sessionId, ownerInstanceId)
                dao.markSessionStarted(sessionId, clock.wallTimeMs(), nowElapsed)
                dao.ensureActiveStarted(sessionId, nowElapsed)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = "SESSION_CLAIMED",
                    ownerInstanceId = ownerInstanceId,
                )
                true
            }
        }

    override suspend fun beginAttempt(sessionId: String, ownerInstanceId: String): BeginAttempt =
        withContext(ioDispatcher) {
            database.withTransaction {
                val session = dao.sessionById(sessionId)
                    ?: return@withTransaction BeginAttempt.Blocked("SESSION_MISSING")
                if (session.state != SessionState.RUNNING.name) {
                    return@withTransaction BeginAttempt.Blocked("SESSION_NOT_RUNNING")
                }
                if (session.ownerInstanceId != ownerInstanceId) {
                    return@withTransaction BeginAttempt.Blocked("OWNER_MISMATCH")
                }
                val slot = dao.runnerSlot()
                if (slot?.sessionId != sessionId || slot.ownerInstanceId != ownerInstanceId) {
                    return@withTransaction BeginAttempt.Blocked("RUNNER_SLOT_NOT_OWNED")
                }

                val nowElapsed = clock.elapsedRealtimeMs()
                val remainingBudgetMs = (session.budgetMs - activeMs(session, nowElapsed)).coerceAtLeast(0L)
                if (remainingBudgetMs <= 0L) {
                    return@withTransaction BeginAttempt.Blocked("SESSION_BUDGET_EXHAUSTED")
                }

                val item = dao.findFirstItemInState(sessionId, QueueItemState.PENDING.name)
                if (item == null) {
                    val waitingForDecision = dao.countItemsInStates(
                        sessionId = sessionId,
                        states = listOf(QueueItemState.NEEDS_DECISION.name),
                    ) > 0
                    return@withTransaction if (waitingForDecision) {
                        BeginAttempt.Blocked("NEEDS_DECISION")
                    } else {
                        BeginAttempt.NoWork
                    }
                }

                if (dao.updateQueueItemState(
                        itemId = item.id,
                        expectedStates = listOf(QueueItemState.PENDING.name),
                        newState = QueueItemState.ACTIVE.name,
                    ) != 1
                ) {
                    return@withTransaction BeginAttempt.Blocked("ITEM_CLAIM_CONFLICT")
                }

                val effectiveTimeoutMs = minOf(session.caseTimeoutMs, remainingBudgetMs)
                val deadlineReason = if (remainingBudgetMs < session.caseTimeoutMs) {
                    DeadlineReason.SESSION_BUDGET
                } else {
                    DeadlineReason.CASE_TIMEOUT
                }

                val attemptNumber = (dao.latestAttempt(item.id)?.number ?: 0) + 1
                val attemptId = UUID.randomUUID().toString()
                dao.insertAttempt(
                    AttemptEntity(
                        id = attemptId,
                        queueItemId = item.id,
                        number = attemptNumber,
                        ownerInstanceId = ownerInstanceId,
                        stage = TestStage.PREFLIGHT.name,
                        outcome = null,
                        validity = null,
                        failureCode = null,
                        failureDomain = null,
                        failureConfidence = null,
                        failureDetail = null,
                        deadlineReason = deadlineReason.name,
                        startedWallTimeMs = clock.wallTimeMs(),
                        startedElapsedMs = nowElapsed,
                        finishedWallTimeMs = null,
                        elapsedMs = null,
                    ),
                )
                dao.ensureActiveStarted(sessionId, nowElapsed)
                dao.markSessionStarted(sessionId, clock.wallTimeMs(), nowElapsed)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = attemptId,
                    stage = TestStage.PREFLIGHT,
                    code = "BEFORE_EXECUTION",
                    ownerInstanceId = ownerInstanceId,
                )

                val request = BenchmarkRequest(
                    sessionId = sessionId,
                    attemptId = attemptId,
                    subjectId = item.subjectId,
                    caseId = item.scenarioId ?: item.subjectId,
                    caseTimeoutMs = effectiveTimeoutMs,
                    origin = EvidenceOrigin.valueOf(session.origin),
                    role = ModelRole.valueOf(item.role),
                    requestedBackend = ComputeBackend.valueOf(item.requestedBackend),
                    scenarioId = item.scenarioId,
                )

                BeginAttempt.Ready(
                    request = request,
                    queueItemId = item.id,
                    attemptNumber = attemptNumber,
                    deadlineReason = deadlineReason,
                )
            }
        }

    override suspend fun recordStage(attemptId: String, stage: TestStage, code: String) {
        withContext(ioDispatcher) {
            database.withTransaction {
                val attempt = dao.attemptById(attemptId) ?: error("attempt_missing")
                val sessionId = dao.sessionIdForAttempt(attemptId) ?: error("session_missing_for_attempt")

                if (attempt.outcome != null || dao.updateAttemptStage(attemptId, stage.name) != 1) {
                    appendEvent(
                        sessionId = sessionId,
                        attemptId = attemptId,
                        stage = stage,
                        code = "LATE_STAGE_IGNORED",
                        ownerInstanceId = attempt.ownerInstanceId,
                    )
                    return@withTransaction
                }

                appendEvent(
                    sessionId = sessionId,
                    attemptId = attemptId,
                    stage = stage,
                    code = code,
                    ownerInstanceId = attempt.ownerInstanceId,
                )
            }
        }
    }

    override suspend fun finishAttempt(attemptId: String, result: AttemptResult): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                val attempt = dao.attemptById(attemptId) ?: return@withTransaction false
                val sessionId = dao.sessionIdForAttempt(attemptId) ?: return@withTransaction false
                val item = dao.queueItemById(attempt.queueItemId) ?: return@withTransaction false

                val nowElapsed = clock.elapsedRealtimeMs()
                val elapsedMs = attempt.startedElapsedMs
                    ?.let { (nowElapsed - it).coerceAtLeast(0L) }

                val written = dao.finishAttempt(
                    attemptId = attemptId,
                    outcome = result.outcome.name,
                    validity = result.validity.name,
                    failureCode = result.failure?.code,
                    failureDomain = result.failure?.domain?.name,
                    failureConfidence = result.failure?.confidence?.name,
                    failureDetail = result.failure?.detail?.take(MAX_FAILURE_DETAIL_CHARS),
                    finishedWallTimeMs = clock.wallTimeMs(),
                    elapsedMs = elapsedMs,
                )

                if (written != 1) {
                    appendEvent(
                        sessionId = sessionId,
                        attemptId = attemptId,
                        stage = null,
                        code = "LATE_RESULT_IGNORED",
                        ownerInstanceId = attempt.ownerInstanceId,
                    )
                    return@withTransaction false
                }

                val itemState = SessionStateMachine.queueItemStateFor(result.outcome)
                if (dao.updateQueueItemState(
                        itemId = item.id,
                        expectedStates = listOf(QueueItemState.ACTIVE.name),
                        newState = itemState.name,
                    ) != 1
                ) {
                    appendEvent(
                        sessionId = sessionId,
                        attemptId = attemptId,
                        stage = null,
                        code = "QUEUE_ITEM_STATE_CONFLICT",
                        ownerInstanceId = attempt.ownerInstanceId,
                    )
                }

                appendEvent(
                    sessionId = sessionId,
                    attemptId = attemptId,
                    stage = null,
                    code = "ATTEMPT_FINISHED_${result.outcome.name}",
                    ownerInstanceId = attempt.ownerInstanceId,
                )
                true
            }
        }

    override suspend fun requestPause(sessionId: String, ownerInstanceId: String): Boolean =
        transitionSession(
            sessionId = sessionId,
            ownerInstanceId = ownerInstanceId,
            command = SessionCommand.PAUSE_REQUESTED,
            eventCode = "SESSION_PAUSE_REQUESTED",
        )

    override suspend fun requestStop(sessionId: String, ownerInstanceId: String): Boolean =
        transitionSession(
            sessionId = sessionId,
            ownerInstanceId = ownerInstanceId,
            command = SessionCommand.STOP_REQUESTED,
            eventCode = "SESSION_STOP_REQUESTED",
        )

    override suspend fun enterPaused(sessionId: String, ownerInstanceId: String): SessionState? =
        withContext(ioDispatcher) {
            database.withTransaction {
                val session = dao.sessionById(sessionId) ?: return@withTransaction null
                if (session.ownerInstanceId != ownerInstanceId) return@withTransaction null

                val allResolved = dao.countItemsInStates(sessionId, SessionStates.RESOLVED_ITEM_NAMES) ==
                    dao.countItems(sessionId)
                val next = SessionStateMachine.next(
                    current = SessionState.valueOf(session.state),
                    command = SessionCommand.PAUSE_APPLIED,
                    allItemsResolved = allResolved,
                ) ?: return@withTransaction null

                val active = activeMs(session, clock.elapsedRealtimeMs())
                if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                    return@withTransaction null
                }

                if (next == SessionState.COMPLETED) {
                    dao.markSessionFinished(sessionId, clock.wallTimeMs(), active)
                } else {
                    dao.updateActiveAccounting(sessionId, active, null)
                    dao.updateOwner(sessionId, null)
                }
                dao.releaseRunnerSlot(sessionId)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = if (next == SessionState.COMPLETED) "SESSION_COMPLETED" else "SESSION_PAUSED",
                    ownerInstanceId = ownerInstanceId,
                )
                next
            }
        }

    override suspend fun enterCompleted(sessionId: String, ownerInstanceId: String): SessionState? =
        withContext(ioDispatcher) {
            database.withTransaction {
                val session = dao.sessionById(sessionId) ?: return@withTransaction null
                if (session.ownerInstanceId != ownerInstanceId) return@withTransaction null

                val next = SessionStateMachine.next(
                    current = SessionState.valueOf(session.state),
                    command = SessionCommand.QUEUE_EXHAUSTED,
                ) ?: return@withTransaction null

                val active = activeMs(session, clock.elapsedRealtimeMs())
                if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                    return@withTransaction null
                }
                dao.markSessionFinished(sessionId, clock.wallTimeMs(), active)
                dao.releaseRunnerSlot(sessionId)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = "SESSION_COMPLETED",
                    ownerInstanceId = ownerInstanceId,
                )
                next
            }
        }

    override suspend fun enterCancelled(
        sessionId: String,
        ownerInstanceId: String,
        reasonCode: String,
    ): SessionState? = withContext(ioDispatcher) {
        database.withTransaction {
            val session = dao.sessionById(sessionId) ?: return@withTransaction null
            if (session.ownerInstanceId != ownerInstanceId) return@withTransaction null

            val next = SessionStateMachine.next(
                current = SessionState.valueOf(session.state),
                command = SessionCommand.STOP_APPLIED,
            ) ?: return@withTransaction null

            closeOpenAttempts(
                sessionId = sessionId,
                outcome = AttemptOutcome.CANCELLED,
                validity = ResultValidity.INVALID,
                code = reasonCode,
                domain = FailureDomain.HARNESS,
                detail = "Attempt cancelled by an explicit user or condition stop.",
                elapsedMs = null,
            )

            val active = activeMs(session, clock.elapsedRealtimeMs())
            if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                return@withTransaction null
            }
            dao.markSessionFinished(sessionId, clock.wallTimeMs(), active)
            dao.releaseRunnerSlot(sessionId)

            appendEvent(
                sessionId = sessionId,
                attemptId = null,
                stage = null,
                code = "SESSION_CANCELLED_$reasonCode",
                ownerInstanceId = ownerInstanceId,
            )
            next
        }
    }

    override suspend fun enterFailed(sessionId: String, code: String, detail: String): SessionState? =
        withContext(ioDispatcher) {
            database.withTransaction {
                val session = dao.sessionById(sessionId) ?: return@withTransaction null
                val next = SessionStateMachine.next(
                    current = SessionState.valueOf(session.state),
                    command = SessionCommand.INFRASTRUCTURE_FAILURE,
                ) ?: return@withTransaction null

                closeOpenAttempts(
                    sessionId = sessionId,
                    outcome = AttemptOutcome.INTERRUPTED,
                    validity = ResultValidity.UNKNOWN,
                    code = code,
                    domain = FailureDomain.HARNESS,
                    detail = detail,
                    elapsedMs = null,
                )

                if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                    return@withTransaction null
                }
                dao.recordSessionFailure(sessionId, code, FailureDomain.HARNESS.name, detail)
                dao.markSessionFinished(
                    sessionId = sessionId,
                    wallTimeMs = clock.wallTimeMs(),
                    activeMs = activeMs(session, clock.elapsedRealtimeMs()),
                )
                dao.releaseRunnerSlot(sessionId)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = "SESSION_FAILED_$code",
                    ownerInstanceId = session.ownerInstanceId ?: processInstanceId,
                )
                next
            }
        }

    override suspend fun cancelSession(sessionId: String, reasonCode: String): SessionState? =
        withContext(ioDispatcher) {
            database.withTransaction {
                val session = dao.sessionById(sessionId) ?: return@withTransaction null
                val next = SessionStateMachine.next(
                    current = SessionState.valueOf(session.state),
                    command = SessionCommand.CANCEL,
                ) ?: return@withTransaction null

                closeOpenAttempts(
                    sessionId = sessionId,
                    outcome = AttemptOutcome.CANCELLED,
                    validity = ResultValidity.INVALID,
                    code = reasonCode,
                    domain = FailureDomain.HARNESS,
                    detail = "Session cancelled before continuation.",
                    elapsedMs = null,
                )

                val active = activeMs(session, clock.elapsedRealtimeMs())
                if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                    return@withTransaction null
                }
                dao.markSessionFinished(sessionId, clock.wallTimeMs(), active)
                dao.releaseRunnerSlot(sessionId)

                appendEvent(
                    sessionId = sessionId,
                    attemptId = null,
                    stage = null,
                    code = "SESSION_CANCELLED_$reasonCode",
                    ownerInstanceId = session.ownerInstanceId ?: processInstanceId,
                )
                next
            }
        }

    override suspend fun skipRemaining(sessionId: String, reasonCode: String): Int =
        withContext(ioDispatcher) {
            database.withTransaction {
                val items = dao.queueItems(sessionId)
                var skipped = 0
                items.filter { it.state == QueueItemState.PENDING.name }.forEach { item ->
                    if (dao.updateQueueItemState(
                            itemId = item.id,
                            expectedStates = listOf(QueueItemState.PENDING.name),
                            newState = QueueItemState.SKIPPED.name,
                        ) == 1
                    ) {
                        skipped += 1
                    }
                }
                if (skipped > 0) {
                    appendEvent(
                        sessionId = sessionId,
                        attemptId = null,
                        stage = null,
                        code = "ITEMS_SKIPPED_$reasonCode",
                        ownerInstanceId = dao.sessionById(sessionId)?.ownerInstanceId ?: processInstanceId,
                    )
                }
                skipped
            }
        }

    override suspend fun retryItem(queueItemId: String): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                val item = dao.queueItemById(queueItemId) ?: return@withTransaction false
                if (dao.updateQueueItemState(
                        itemId = queueItemId,
                        expectedStates = listOf(QueueItemState.NEEDS_DECISION.name),
                        newState = QueueItemState.PENDING.name,
                    ) != 1
                ) {
                    return@withTransaction false
                }
                appendEvent(
                    sessionId = item.sessionId,
                    attemptId = null,
                    stage = null,
                    code = "QUEUE_ITEM_RETRY_PLANNED",
                    ownerInstanceId = dao.sessionById(item.sessionId)?.ownerInstanceId ?: processInstanceId,
                )
                true
            }
        }

    override suspend fun skipItem(queueItemId: String): Boolean =
        withContext(ioDispatcher) {
            database.withTransaction {
                val item = dao.queueItemById(queueItemId) ?: return@withTransaction false
                if (dao.updateQueueItemState(
                        itemId = queueItemId,
                        expectedStates = listOf(QueueItemState.NEEDS_DECISION.name),
                        newState = QueueItemState.SKIPPED.name,
                    ) != 1
                ) {
                    return@withTransaction false
                }
                appendEvent(
                    sessionId = item.sessionId,
                    attemptId = null,
                    stage = null,
                    code = "QUEUE_ITEM_SKIPPED_AFTER_INTERRUPTION",
                    ownerInstanceId = dao.sessionById(item.sessionId)?.ownerInstanceId ?: processInstanceId,
                )
                true
            }
        }

    override suspend fun recoverInterruptedOwner(newOwnerInstanceId: String): RecoveryReport =
        withContext(ioDispatcher) {
            database.withTransaction {
                dao.insertRunnerSlotIfMissing(RunnerSlotEntity.empty())

                val recoveredSessions = mutableListOf<String>()
                val interruptedAttempts = mutableListOf<String>()
                val needsDecisionItems = mutableListOf<String>()

                dao.sessionsInStates(SessionStates.LIVE_NAMES).forEach { session ->
                    val openAttempt = dao.openAttemptForSession(session.id)
                    val nowElapsed = clock.elapsedRealtimeMs()

                    if (openAttempt != null) {
                        val written = dao.finishAttempt(
                            attemptId = openAttempt.id,
                            outcome = AttemptOutcome.INTERRUPTED.name,
                            validity = ResultValidity.UNKNOWN.name,
                            failureCode = "PROCESS_DISAPPEARED",
                            failureDomain = FailureDomain.UNKNOWN.name,
                            failureConfidence = EvidenceConfidence.UNKNOWN.name,
                            failureDetail = "Process disappeared during the attempt; the cause is unknown.",
                            finishedWallTimeMs = clock.wallTimeMs(),
                            elapsedMs = null,
                        )
                        if (written == 1) {
                            interruptedAttempts += openAttempt.id
                            if (dao.updateQueueItemState(
                                    itemId = openAttempt.queueItemId,
                                    expectedStates = listOf(QueueItemState.ACTIVE.name),
                                    newState = QueueItemState.NEEDS_DECISION.name,
                                ) == 1
                            ) {
                                needsDecisionItems += openAttempt.queueItemId
                            }
                        }
                    }

                    dao.queueItems(session.id)
                        .filter { it.state == QueueItemState.ACTIVE.name }
                        .forEach { item ->
                            if (dao.updateQueueItemState(
                                    itemId = item.id,
                                    expectedStates = listOf(QueueItemState.ACTIVE.name),
                                    newState = QueueItemState.NEEDS_DECISION.name,
                                ) == 1
                            ) {
                                needsDecisionItems += item.id
                            }
                        }

                    val next = SessionStateMachine.next(
                        current = SessionState.valueOf(session.state),
                        command = SessionCommand.PROCESS_LOST,
                    ) ?: return@forEach

                    if (dao.updateSessionState(session.id, session.state, next.name) == 1) {
                        dao.updateActiveAccounting(
                            sessionId = session.id,
                            activeMs = activeMs(session, nowElapsed),
                            startedElapsedMs = null,
                        )
                        dao.updateOwner(session.id, null)
                        dao.releaseRunnerSlot(session.id)
                        appendEvent(
                            sessionId = session.id,
                            attemptId = openAttempt?.id,
                            stage = null,
                            code = "SESSION_INTERRUPTED_PROCESS_DISAPPEARED",
                            ownerInstanceId = newOwnerInstanceId,
                        )
                        recoveredSessions += session.id
                    }
                }

                dao.releaseRunnerSlotAlways()

                RecoveryReport(
                    recoveredSessionIds = recoveredSessions,
                    interruptedAttemptIds = interruptedAttempts,
                    needsDecisionItemIds = needsDecisionItems,
                )
            }
        }

    private suspend fun transitionSession(
        sessionId: String,
        ownerInstanceId: String,
        command: SessionCommand,
        eventCode: String,
    ): Boolean = withContext(ioDispatcher) {
        database.withTransaction {
            val session = dao.sessionById(sessionId) ?: return@withTransaction false
            if (session.ownerInstanceId != ownerInstanceId) return@withTransaction false

            val next = SessionStateMachine.next(
                current = SessionState.valueOf(session.state),
                command = command,
            ) ?: return@withTransaction false

            if (dao.updateSessionState(sessionId, session.state, next.name) != 1) {
                return@withTransaction false
            }
            appendEvent(
                sessionId = sessionId,
                attemptId = null,
                stage = null,
                code = eventCode,
                ownerInstanceId = ownerInstanceId,
            )
            true
        }
    }

    private suspend fun closeOpenAttempts(
        sessionId: String,
        outcome: AttemptOutcome,
        validity: ResultValidity,
        code: String,
        domain: FailureDomain,
        detail: String,
        elapsedMs: Long?,
    ) {
        val openAttempt = dao.openAttemptForSession(sessionId) ?: return
        val written = dao.finishAttempt(
            attemptId = openAttempt.id,
            outcome = outcome.name,
            validity = validity.name,
            failureCode = code,
            failureDomain = domain.name,
            failureConfidence = EvidenceConfidence.OBSERVED.name,
            failureDetail = detail,
            finishedWallTimeMs = clock.wallTimeMs(),
            elapsedMs = elapsedMs,
        )
        if (written == 1) {
            dao.updateQueueItemState(
                itemId = openAttempt.queueItemId,
                expectedStates = listOf(QueueItemState.ACTIVE.name),
                newState = SessionStateMachine.queueItemStateFor(outcome).name,
            )
        }
    }

    private suspend fun appendEvent(
        sessionId: String,
        attemptId: String?,
        stage: TestStage?,
        code: String,
        ownerInstanceId: String,
    ) {
        if (dao.incrementSeq(sessionId) != 1) {
            throw IllegalStateException("Cannot append an event for a missing session")
        }
        val seq = dao.lastSeq(sessionId)
            ?: throw IllegalStateException("Event sequence was not allocated")

        dao.insertEvent(
            EventEntity(
                sessionId = sessionId,
                seq = seq,
                attemptId = attemptId,
                stage = stage?.name,
                code = code,
                boundedPayloadJson = null,
                wallTimeMs = clock.wallTimeMs(),
                elapsedTimeMs = clock.elapsedRealtimeMs(),
                ownerInstanceId = ownerInstanceId,
            ),
        )
    }

    private fun activeMs(session: SessionEntity, nowElapsedMs: Long): Long {
        val started = session.activeStartedElapsedMs ?: return session.persistedActiveMs
        val delta = (nowElapsedMs - started).coerceAtLeast(0L)
        return session.persistedActiveMs + delta
    }

    private fun toSummary(row: SessionSummaryRow): SessionSummary = SessionSummary(
        sessionId = row.id,
        state = SessionState.valueOf(row.state),
        origin = EvidenceOrigin.valueOf(row.origin),
        engineId = row.engineId,
        profileId = row.profileId,
        createdAtWallTimeMs = row.createdAtWallTimeMs,
        finishedWallTimeMs = row.finishedWallTimeMs,
        itemCount = row.itemCount,
        completedItemCount = row.completedCount,
    )

    private fun buildDetail(
        session: SessionEntity,
        items: List<QueueItemEntity>,
        attempts: List<AttemptEntity>,
        events: List<EventEntity>,
        telemetry: List<TelemetryEntity>,
    ): SessionDetail {
        val attemptsByItem = attempts.groupBy { it.queueItemId }
        val queue = items.map { item ->
            QueueItemView(
                queueItemId = item.id,
                ordinal = item.ordinal,
                subjectId = item.subjectId,
                scenarioId = item.scenarioId,
                state = QueueItemState.valueOf(item.state),
                repeatIndex = item.repeatIndex,
                role = ModelRole.valueOf(item.role),
                requestedBackend = ComputeBackend.valueOf(item.requestedBackend),
                attempts = attemptsByItem[item.id].orEmpty().map(::toAttemptView),
            )
        }

        return SessionDetail(
            summary = SessionSummary(
                sessionId = session.id,
                state = SessionState.valueOf(session.state),
                origin = EvidenceOrigin.valueOf(session.origin),
                engineId = session.engineId,
                profileId = session.profileId,
                createdAtWallTimeMs = session.createdAtWallTimeMs,
                finishedWallTimeMs = session.finishedWallTimeMs,
                itemCount = items.size,
                completedItemCount = items.count { it.state == QueueItemState.DONE.name || it.state == QueueItemState.SKIPPED.name },
            ),
            queue = queue,
            events = events.map(::toEventView),
            telemetry = telemetry.map(::toTelemetryView),
            caseTimeoutMs = session.caseTimeoutMs,
            budgetMs = session.budgetMs,
            repeatCount = session.repeatCount,
            requireCharging = session.requireCharging,
            persistedActiveMs = session.persistedActiveMs,
            sessionFailure = failureInfo(
                code = session.failureCode,
                domainName = session.failureDomain,
                confidenceName = null,
                detail = session.failureDetail,
            ),
            planJson = session.planJson,
            openAttemptId = attempts.firstOrNull { it.outcome == null }?.id,
        )
    }

    private fun toAttemptView(attempt: AttemptEntity): AttemptView = AttemptView(
        attemptId = attempt.id,
        number = attempt.number,
        stage = TestStage.valueOf(attempt.stage),
        outcome = attempt.outcome?.let { AttemptOutcome.valueOf(it) },
        validity = attempt.validity?.let { ResultValidity.valueOf(it) },
        failure = failureInfo(
            code = attempt.failureCode,
            domainName = attempt.failureDomain,
            confidenceName = attempt.failureConfidence,
            detail = attempt.failureDetail,
        ),
        deadlineReason = attempt.deadlineReason?.let { DeadlineReason.valueOf(it) },
        ownerInstanceId = attempt.ownerInstanceId,
        startedWallTimeMs = attempt.startedWallTimeMs,
        finishedWallTimeMs = attempt.finishedWallTimeMs,
        elapsedMs = attempt.elapsedMs,
    )

    private fun toEventView(event: EventEntity): EventView = EventView(
        seq = event.seq,
        attemptId = event.attemptId,
        stage = event.stage?.let { TestStage.valueOf(it) },
        code = event.code,
        wallTimeMs = event.wallTimeMs,
        elapsedTimeMs = event.elapsedTimeMs,
        ownerInstanceId = event.ownerInstanceId,
    )

    private fun toTelemetryView(telemetry: TelemetryEntity): TelemetryView = TelemetryView(
        name = telemetry.name,
        value = telemetry.value,
        unit = telemetry.unit,
        scope = telemetry.scope,
        origin = EvidenceOrigin.valueOf(telemetry.origin),
        unavailableReason = telemetry.unavailableReason,
        wallTimeMs = telemetry.wallTimeMs,
    )

    private fun failureInfo(
        code: String?,
        domainName: String?,
        confidenceName: String?,
        detail: String?,
    ): FailureInfo? {
        if (code == null) return null
        return FailureInfo(
            code = code,
            domain = FailureDomain.valueOf(domainName ?: FailureDomain.UNKNOWN.name),
            confidence = EvidenceConfidence.valueOf(confidenceName ?: EvidenceConfidence.UNKNOWN.name),
            detail = detail.orEmpty(),
        )
    }

    private companion object {
        const val MAX_FAILURE_DETAIL_CHARS = 4_096
    }
}
