package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.data.BeginAttempt
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.AttemptEventSink
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.BenchmarkEngine
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.TestStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface CoordinatorResult {
    data object Completed : CoordinatorResult
    data class Paused(val reason: String) : CoordinatorResult
    data class Cancelled(val reason: String) : CoordinatorResult
    data class Failed(val code: String, val detail: String) : CoordinatorResult
    data class Rejected(val reason: String) : CoordinatorResult
}

/**
 * Serialized queue execution owned by the foreground service.
 *
 * Persist-before-execute: `beginAttempt` commits BEFORE_EXECUTION before the engine runs.
 * Pause lets the bounded attempt finish. Stop cancels the cooperative attempt and then
 * transitions the session. Storage failures are never reported as model failures and stop
 * the queue instead of continuing.
 *
 * `withTimeout` is sufficient only for this cooperative fake engine. It cannot stop a native
 * JNI/GPU hang; that requires the supervisor planned for the Codex stage.
 */
class SessionCoordinator(
    private val repository: SessionRepository,
    private val engine: BenchmarkEngine,
    private val conditions: DeviceConditions,
    private val processInstanceId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val conditionPollMs: Long = DEFAULT_CONDITION_POLL_MS,
    private val minimumFreeBytes: Long = AndroidDeviceConditions.MINIMUM_FREE_BYTES_V0,
    additionalEngines: List<BenchmarkEngine> = emptyList(),
) {

    private val enginesById: Map<String, BenchmarkEngine> =
        (listOf(engine) + additionalEngines).associateBy { it.engineId }.also { engines ->
            require(engines.size == 1 + additionalEngines.size) { "DUPLICATE_ENGINE_ID" }
        }

    private val running = AtomicBoolean(false)
    private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var pauseRequested: Boolean = false

    @Volatile
    private var stopReason: String? = null

    @Volatile
    private var safetyStop: ConditionBlockReason? = null

    @Volatile
    private var attemptJob: Job? = null

    fun requestPause() {
        pauseRequested = true
    }

    fun requestStop(reason: String) {
        stopReason = reason
        attemptJob?.cancel()
    }

    /**
     * Service entry point: returns immediately after launching the run.
     *
     * Control flags are cleared here, synchronously, before the coroutine can start. Clearing
     * them inside [runSession] would drop a pause requested in the window between start and
     * the first suspension point.
     */
    fun start(scope: CoroutineScope, sessionId: String): Job {
        pauseRequested = false
        stopReason = null
        safetyStop = null
        return scope.launch(dispatcher) {
            runSession(sessionId)
        }
    }

    /**
     * Runs the queue until it is completed, paused, cancelled or failed.
     * A second concurrent call is rejected instead of starting a second attempt.
     */
    suspend fun runSession(sessionId: String): CoordinatorResult {
        if (!running.compareAndSet(false, true)) {
            return CoordinatorResult.Rejected("ALREADY_RUNNING")
        }
        return try {
            executeQueue(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // Last-resort net: a repository bug must stop the queue with a visible failure,
            // not kill the service job silently.
            failInfrastructure(
                sessionId = sessionId,
                code = "COORDINATOR_FAILURE",
                detail = failure.message ?: failure::class.java.simpleName,
            )
        } finally {
            running.set(false)
            attemptJob = null
        }
    }

    private suspend fun executeQueue(sessionId: String): CoordinatorResult {
        val loaded = try {
            repository.sessionDetail(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return failInfrastructure(
                sessionId = sessionId,
                code = "STORAGE_FAILURE",
                detail = failure.message ?: failure::class.java.simpleName,
            )
        }
        val detail = loaded ?: return CoordinatorResult.Rejected("SESSION_MISSING")

        val selectedEngine = enginesById[detail.summary.engineId]
        if (selectedEngine == null) {
            return failInfrastructure(
                sessionId = sessionId,
                code = "ENGINE_ID_MISMATCH",
                detail = "No registered engine can run ${detail.summary.engineId}.",
            )
        }

        val claimed = try {
            repository.claimSession(sessionId, processInstanceId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return failInfrastructure(
                sessionId = sessionId,
                code = "STORAGE_FAILURE",
                detail = failure.message ?: failure::class.java.simpleName,
            )
        }
        if (!claimed) {
            return CoordinatorResult.Rejected("SESSION_NOT_STARTABLE")
        }

        // Rebuild the guard from committed attempt rows so process/session resume cannot
        // silently reset a consecutive harness-failure streak.
        var harnessBreaker = HarnessFaultCircuitBreaker()
        detail.queue.asSequence()
            .flatMap { it.attempts.asSequence() }
            .filter { it.outcome != null }
            .forEach { attempt ->
                harnessBreaker = when {
                    attempt.outcome == AttemptOutcome.FAILED && attempt.failure?.domain == FailureDomain.HARNESS ->
                        harnessBreaker.recordHarnessFault()
                    attempt.outcome == AttemptOutcome.PASSED && attempt.validity == ResultValidity.VALID ->
                        harnessBreaker.recordValidControlSuccess()
                    else -> harnessBreaker
                }
            }

        if (harnessBreaker.shouldPauseForReview) {
            return pauseSession(
                sessionId = sessionId,
                eventCode = "CIRCUIT_BREAKER_PAUSE_AFTER_${HarnessFaultCircuitBreaker.FAULTS_BEFORE_PAUSE}_HARNESS_FAILURES",
                reason = "REPEATED_HARNESS_FAILURES",
            )
        }

        while (true) {
            stopReason?.let { reason -> return stopSession(sessionId, reason) }

            if (pauseRequested) {
                return pauseSession(sessionId, "USER_PAUSE_APPLIED", "USER_PAUSE")
            }

            blockingCondition(detail.requireCharging)?.let { reason ->
                return pauseSession(sessionId, "CONDITION_PAUSE_${reason.name}", reason.name)
            }

            val begun = try {
                repository.beginAttempt(sessionId, processInstanceId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                return failInfrastructure(
                    sessionId = sessionId,
                    code = "STORAGE_FAILURE",
                    detail = failure.message ?: failure::class.java.simpleName,
                )
            }

            when (begun) {
                BeginAttempt.NoWork -> return finishQueue(
                    sessionId = sessionId,
                    rejectedDetail = "The queue is empty but the session did not become COMPLETED.",
                )

                is BeginAttempt.Blocked -> return when (begun.reason) {
                    "SESSION_BUDGET_EXHAUSTED" -> {
                        try {
                            repository.skipRemaining(sessionId, "SESSION_BUDGET_EXHAUSTED")
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Exception) {
                            return failInfrastructure(
                                sessionId = sessionId,
                                code = "STORAGE_FAILURE",
                                detail = failure.message ?: failure::class.java.simpleName,
                            )
                        }
                        finishQueue(
                            sessionId = sessionId,
                            rejectedDetail = "Budget was exhausted but the session did not " +
                                "become COMPLETED.",
                        )
                    }

                    "NEEDS_DECISION" -> pauseSession(
                        sessionId = sessionId,
                        eventCode = "PAUSED_FOR_NEEDS_DECISION",
                        reason = "NEEDS_DECISION",
                    )

                    else -> failInfrastructure(
                        sessionId = sessionId,
                        code = "RUNNER_BLOCKED_${begun.reason}",
                        detail = "The queue cannot continue: ${begun.reason}.",
                    )
                }

                is BeginAttempt.Ready -> {
                    val attemptRun = try {
                        runAttempt(sessionId, begun, detail.requireCharging, selectedEngine)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        AttemptRun.StorageFailure(failure)
                    }

                    when (attemptRun) {
                        is AttemptRun.Continue -> {
                            harnessBreaker = when {
                                attemptRun.harnessFault -> harnessBreaker.recordHarnessFault()
                                attemptRun.validControlSuccess -> harnessBreaker.recordValidControlSuccess()
                                else -> harnessBreaker
                            }
                            if (harnessBreaker.shouldPauseForReview) {
                                return pauseSession(
                                    sessionId = sessionId,
                                    eventCode = "CIRCUIT_BREAKER_PAUSE_AFTER_${HarnessFaultCircuitBreaker.FAULTS_BEFORE_PAUSE}_HARNESS_FAILURES",
                                    reason = "REPEATED_HARNESS_FAILURES",
                                )
                            }
                        }
                        is AttemptRun.StorageFailure -> return failInfrastructure(
                            sessionId = sessionId,
                            code = "STORAGE_FAILURE",
                            detail = attemptRun.cause.message ?: attemptRun.cause::class.java.simpleName,
                        )

                        is AttemptRun.ConditionStop -> return pauseSession(
                            sessionId = sessionId,
                            eventCode = "CONDITION_PAUSE_${attemptRun.reason.name}",
                            reason = attemptRun.reason.name,
                        )

                        is AttemptRun.StopRequested -> return stopSession(sessionId, attemptRun.reason)
                        is AttemptRun.OriginMismatch -> return failInfrastructure(
                            sessionId = sessionId,
                            code = "ORIGIN_MISMATCH",
                            detail = attemptRun.detail,
                        )

                        AttemptRun.UnexpectedCancellation -> return stopSession(
                            sessionId = sessionId,
                            reason = "ENGINE_CANCELLED",
                        )
                    }

                    if (pauseRequested) {
                        return pauseSession(sessionId, "USER_PAUSE_APPLIED", "USER_PAUSE")
                    }
                }
            }
        }
    }

    private suspend fun runAttempt(
        sessionId: String,
        begun: BeginAttempt.Ready,
        requireCharging: Boolean,
        selectedEngine: BenchmarkEngine,
    ): AttemptRun {
        val request = begun.request
        val sink = RecordingSink(
            AttemptEventSink { stage, code ->
                repository.recordStage(request.attemptId, stage, code)
            },
        )

        val snapshot = conditions.snapshot()
        recordAttemptTelemetry(sessionId, request, snapshot)
        // A condition captured by a previous attempt must not be attributed to this one.
        safetyStop = null

        val wakeLock = conditions.acquireAttemptWakeLock(
            request.caseTimeoutMs + WAKE_LOCK_GRACE_MS,
        )
        val watchdog = launchConditionWatchdog(requireCharging)

        val execution = try {
            executeEngine(selectedEngine, request, sink)
        } finally {
            watchdog.cancel()
            wakeLock.release()
        }

        val conditionStop = safetyStop
        safetyStop = null

        return when (execution) {
            is EngineExecution.Finished -> {
                val result = execution.result
                if (result.origin != request.origin || result.origin != selectedEngine.origin) {
                    val invalid = AttemptResult(
                        outcome = AttemptOutcome.FAILED,
                        validity = ResultValidity.INVALID,
                        origin = request.origin,
                        failure = FailureInfo(
                            code = "ORIGIN_MISMATCH",
                            domain = FailureDomain.HARNESS,
                            confidence = EvidenceConfidence.OBSERVED,
                            detail = "Engine reported ${result.origin}; request is ${request.origin}.",
                        ),
                    )
                    val storageProblem = finishAttemptOrStorage(request.attemptId, invalid)
                    storageProblem ?: AttemptRun.OriginMismatch(
                        "Engine reported ${result.origin} for a ${request.origin} request.",
                    )
                } else {
                    finishAttemptOrStorage(request.attemptId, result) ?: AttemptRun.Continue(
                        harnessFault = result.outcome == AttemptOutcome.FAILED &&
                            result.failure?.domain == FailureDomain.HARNESS,
                        validControlSuccess = result.outcome == AttemptOutcome.PASSED &&
                            result.validity == ResultValidity.VALID,
                    )
                }
            }

            EngineExecution.TimedOut -> finishAttemptOrStorage(
                attemptId = request.attemptId,
                result = AttemptResult(
                    outcome = AttemptOutcome.TIMED_OUT,
                    validity = ResultValidity.VALID,
                    origin = request.origin,
                ),
            ) ?: AttemptRun.Continue()

            EngineExecution.Cancelled -> {
                val reasonCode = when {
                    conditionStop != null -> conditionStop.name
                    stopReason != null -> stopReason ?: "ENGINE_CANCELLED"
                    else -> "ENGINE_CANCELLED"
                }
                val domain = when (conditionStop) {
                    ConditionBlockReason.POWER_CONSTRAINT,
                    ConditionBlockReason.THERMAL_CONSTRAINT,
                    ConditionBlockReason.INSUFFICIENT_STORAGE,
                    -> FailureDomain.ENVIRONMENT

                    null -> FailureDomain.HARNESS
                }
                val cancelled = AttemptResult(
                    outcome = AttemptOutcome.CANCELLED,
                    validity = ResultValidity.INVALID,
                    origin = request.origin,
                    failure = FailureInfo(
                        code = reasonCode,
                        domain = domain,
                        confidence = EvidenceConfidence.OBSERVED,
                        detail = "Cancelled before completion; not evidence that a model is unsuitable.",
                    ),
                )
                finishAttemptOrStorage(request.attemptId, cancelled)?.let { return it }

                when {
                    conditionStop != null -> AttemptRun.ConditionStop(conditionStop)
                    stopReason != null -> AttemptRun.StopRequested(stopReason ?: "USER_STOP")
                    else -> AttemptRun.UnexpectedCancellation
                }
            }

            is EngineExecution.StorageFailure -> AttemptRun.StorageFailure(execution.cause)

            is EngineExecution.EngineFailure -> {
                val failure = AttemptResult(
                    outcome = AttemptOutcome.FAILED,
                    validity = ResultValidity.UNKNOWN,
                    origin = request.origin,
                    failure = FailureInfo(
                        code = "ENGINE_EXCEPTION",
                        domain = FailureDomain.UNKNOWN,
                        confidence = EvidenceConfidence.OBSERVED,
                        detail = (execution.cause.message ?: execution.cause::class.java.simpleName)
                            .take(MAX_DETAIL_CHARS),
                    ),
                )
                finishAttemptOrStorage(request.attemptId, failure) ?: AttemptRun.Continue()
            }
        }
    }

    private suspend fun executeEngine(
        selectedEngine: BenchmarkEngine,
        request: BenchmarkRequest,
        sink: RecordingSink,
    ): EngineExecution {
        val deferred = engineScope.async {
            if (selectedEngine.managesOwnDeadline) {
                selectedEngine.execute(request, sink)
            } else {
                withTimeout(request.caseTimeoutMs) {
                    selectedEngine.execute(request, sink)
                }
            }
        }
        attemptJob = deferred

        return try {
            EngineExecution.Finished(deferred.await())
        } catch (timeout: TimeoutCancellationException) {
            EngineExecution.TimedOut
        } catch (cancelled: CancellationException) {
            // The engine runs in its own scope so requestStop can cancel it from another
            // thread. If the host job is cancelled instead, cancel the engine explicitly:
            // otherwise an orphan attempt would keep writing after the service is gone.
            deferred.cancel()
            if (!currentCoroutineContext().isActive) throw cancelled
            EngineExecution.Cancelled
        } catch (failure: Exception) {
            if (sink.storageFailure != null) {
                EngineExecution.StorageFailure(failure)
            } else {
                EngineExecution.EngineFailure(failure)
            }
        } finally {
            attemptJob = null
        }
    }

    private fun launchConditionWatchdog(requireCharging: Boolean): Job = engineScope.launch {
        while (isActive) {
            delay(conditionPollMs)
            val snapshot = conditions.snapshot()
            val reason = when {
                requireCharging && snapshot.charging == false -> ConditionBlockReason.POWER_CONSTRAINT
                snapshot.thermalLevel.isBlocking -> ConditionBlockReason.THERMAL_CONSTRAINT
                else -> null
            }
            if (reason != null) {
                safetyStop = reason
                attemptJob?.cancel()
                return@launch
            }
        }
    }

    private suspend fun recordAttemptTelemetry(
        sessionId: String,
        request: BenchmarkRequest,
        snapshot: ConditionSnapshot,
    ) {
        repository.recordTelemetry(
            sessionId = sessionId,
            attemptId = request.attemptId,
            name = "free_storage",
            value = snapshot.freeStorageBytes?.toDouble(),
            unit = "bytes",
            scope = TELEMETRY_SCOPE_DEVICE,
            origin = request.origin,
            unavailableReason = if (snapshot.freeStorageBytes == null) "STATFS_UNAVAILABLE" else null,
        )
        repository.recordTelemetry(
            sessionId = sessionId,
            attemptId = request.attemptId,
            name = "charging",
            value = snapshot.charging?.let { if (it) 1.0 else 0.0 },
            unit = "boolean",
            scope = TELEMETRY_SCOPE_DEVICE,
            origin = request.origin,
            unavailableReason = if (snapshot.charging == null) "BATTERY_STATUS_UNAVAILABLE" else null,
        )
    }

    private fun blockingCondition(requireCharging: Boolean): ConditionBlockReason? {
        val snapshot = conditions.snapshot()
        if (requireCharging && snapshot.charging == false) {
            return ConditionBlockReason.POWER_CONSTRAINT
        }
        // v0 has no manual confirmation flow, so MODERATE is treated as blocking.
        if (snapshot.thermalLevel.isModerateOrWorse) {
            return ConditionBlockReason.THERMAL_CONSTRAINT
        }
        val freeStorage = snapshot.freeStorageBytes
        if (freeStorage != null && freeStorage < minimumFreeBytes) {
            return ConditionBlockReason.INSUFFICIENT_STORAGE
        }
        return null
    }

    private suspend fun pauseSession(
        sessionId: String,
        eventCode: String,
        reason: String,
    ): CoordinatorResult = try {
        repository.recordSessionEvent(sessionId, eventCode)
        if (!repository.requestPause(sessionId, processInstanceId)) {
            CoordinatorResult.Failed("PAUSE_REJECTED", "The session could not enter PAUSING.")
        } else {
            when (repository.enterPaused(sessionId, processInstanceId)) {
                SessionState.PAUSED -> CoordinatorResult.Paused(reason)
                SessionState.COMPLETED -> CoordinatorResult.Completed
                else -> CoordinatorResult.Failed(
                    "PAUSE_NOT_APPLIED",
                    "The session did not reach PAUSED.",
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        CoordinatorResult.Failed(
            "STORAGE_FAILURE",
            failure.message ?: failure::class.java.simpleName,
        )
    }

    private suspend fun stopSession(sessionId: String, reason: String): CoordinatorResult = try {
        repository.requestStop(sessionId, processInstanceId)
        if (repository.enterCancelled(sessionId, processInstanceId, reason) == SessionState.CANCELLED) {
            CoordinatorResult.Cancelled(reason)
        } else {
            CoordinatorResult.Failed("STOP_NOT_APPLIED", "The session did not reach CANCELLED.")
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        CoordinatorResult.Failed(
            "STORAGE_FAILURE",
            failure.message ?: failure::class.java.simpleName,
        )
    }

    private suspend fun failInfrastructure(
        sessionId: String,
        code: String,
        detail: String,
    ): CoordinatorResult {
        val bounded = detail.take(MAX_DETAIL_CHARS)
        return try {
            repository.enterFailed(sessionId, code, bounded)
            CoordinatorResult.Failed(code, bounded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CoordinatorResult.Failed(
                code,
                "$bounded; session state was not saved: ${failure.message ?: "storage failure"}",
            )
        }
    }

    private suspend fun finishAttemptOrStorage(
        attemptId: String,
        result: AttemptResult,
    ): AttemptRun? = try {
        repository.finishAttempt(attemptId, result)
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        AttemptRun.StorageFailure(failure)
    }

    private sealed interface Completion {
        data class Applied(val state: SessionState?) : Completion
        data class StorageFailure(val cause: Exception) : Completion
    }

    private suspend fun completeSession(sessionId: String): Completion = try {
        Completion.Applied(repository.enterCompleted(sessionId, processInstanceId))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Completion.StorageFailure(failure)
    }

    private suspend fun finishQueue(
        sessionId: String,
        rejectedDetail: String,
    ): CoordinatorResult = when (val completion = completeSession(sessionId)) {
        is Completion.StorageFailure -> failInfrastructure(
            sessionId = sessionId,
            code = "STORAGE_FAILURE",
            detail = completion.cause.message ?: completion.cause::class.java.simpleName,
        )

        is Completion.Applied -> if (completion.state == SessionState.COMPLETED) {
            CoordinatorResult.Completed
        } else {
            CoordinatorResult.Failed("COMPLETION_REJECTED", rejectedDetail)
        }
    }

    private sealed interface EngineExecution {
        data class Finished(val result: AttemptResult) : EngineExecution
        data object TimedOut : EngineExecution
        data object Cancelled : EngineExecution
        data class StorageFailure(val cause: Exception) : EngineExecution
        data class EngineFailure(val cause: Exception) : EngineExecution
    }

    private sealed interface AttemptRun {
        data class Continue(
            val harnessFault: Boolean = false,
            val validControlSuccess: Boolean = false,
        ) : AttemptRun
        data class ConditionStop(val reason: ConditionBlockReason) : AttemptRun
        data class StopRequested(val reason: String) : AttemptRun
        data class OriginMismatch(val detail: String) : AttemptRun
        data class StorageFailure(val cause: Exception) : AttemptRun
        data object UnexpectedCancellation : AttemptRun
    }

    private class RecordingSink(
        private val delegate: AttemptEventSink,
    ) : AttemptEventSink {

        @Volatile
        var storageFailure: Exception? = null
            private set

        override suspend fun record(stage: TestStage, code: String) {
            try {
                delegate.record(stage, code)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                storageFailure = failure
                throw failure
            }
        }
    }

    private companion object {
        const val DEFAULT_CONDITION_POLL_MS = 1_000L
        const val WAKE_LOCK_GRACE_MS = 10_000L
        const val TELEMETRY_SCOPE_DEVICE = "device"
        const val MAX_DETAIL_CHARS = 512
    }
}
