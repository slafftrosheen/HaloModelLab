package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.domain.AttemptEventSink
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.BenchmarkEngine
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.PlannedItem
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level checks for pause, stop, budget, condition and storage handling.
 *
 * Added in HML-L2, NOT executed: build and test runs stay NOT_RUN.
 */
class SessionCoordinatorTest {

    @Test
    fun completesAQueueOfDemoScenarios() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS, DemoCatalog.LOAD_FAILURE))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Completed, result)
        assertEquals(SessionState.COMPLETED, repository.state)
        assertEquals(2, repository.attempts.size)
        assertEquals(AttemptOutcome.PASSED, repository.attempts[0].outcome)
        assertEquals(AttemptOutcome.FAILED, repository.attempts[1].outcome)
        assertEquals("SIMULATED_ADAPTER_LOAD_FAILURE", repository.attempts[1].failure?.code)
        assertTrue(repository.codes().contains("BEFORE_EXECUTION"))
    }

    @Test
    fun timeoutBecomesTimedOutAndTheQueueContinues() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.TIMEOUT, caseTimeoutMs = 1_000L))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Completed, result)
        assertEquals(AttemptOutcome.TIMED_OUT, repository.attempts.single().outcome)
        assertEquals(ResultValidity.VALID, repository.attempts.single().validity)
    }

    @Test
    fun pauseFinishesTheCurrentAttemptAndStartsNothingElse() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS, DemoCatalog.SUCCESS))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val run = async { coordinator.runSession(repository.sessionId) }
        advanceTimeBy(100L)
        runCurrent()
        coordinator.requestPause()
        advanceUntilIdle()

        assertEquals(CoordinatorResult.Paused("USER_PAUSE"), run.await())
        assertEquals(SessionState.PAUSED, repository.state)
        assertEquals(1, repository.attempts.size)
        assertEquals(QueueItemState.DONE, repository.itemStates()[0])
        assertEquals(QueueItemState.PENDING, repository.itemStates()[1])
    }

    @Test
    fun pauseAfterTheLastAttemptCompletesTheSession() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val run = async { coordinator.runSession(repository.sessionId) }
        advanceTimeBy(30L)
        runCurrent()
        coordinator.requestPause()
        advanceUntilIdle()

        assertEquals(CoordinatorResult.Completed, run.await())
        assertEquals(SessionState.COMPLETED, repository.state)
    }

    @Test
    fun stopCancelsTheAttemptAndTheSession() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.TIMEOUT, caseTimeoutMs = 60_000L))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val run = async { coordinator.runSession(repository.sessionId) }
        advanceTimeBy(500L)
        runCurrent()
        coordinator.requestStop("USER_STOP")
        advanceUntilIdle()

        assertEquals(CoordinatorResult.Cancelled("USER_STOP"), run.await())
        assertEquals(SessionState.CANCELLED, repository.state)
        assertEquals(AttemptOutcome.CANCELLED, repository.attempts.single().outcome)
        assertEquals(ResultValidity.INVALID, repository.attempts.single().validity)
    }

    @Test
    fun aSecondConcurrentRunIsRejected() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val first = async { coordinator.runSession(repository.sessionId) }
        runCurrent()

        assertEquals(
            CoordinatorResult.Rejected("ALREADY_RUNNING"),
            coordinator.runSession(repository.sessionId),
        )
        advanceUntilIdle()
        assertEquals(CoordinatorResult.Completed, first.await())
        assertEquals(1, repository.attempts.size)
    }

    @Test
    fun dischargingDevicePausesInsteadOfFailingTheModel() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val conditions = FakeConditions(
            ConditionSnapshot(
                charging = false,
                thermalLevel = ThermalLevel.NONE,
                freeStorageBytes = 1_000_000_000L,
            ),
        )
        val coordinator = coordinator(repository, conditions, testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Paused("POWER_CONSTRAINT"), result)
        assertEquals(SessionState.PAUSED, repository.state)
        assertTrue(repository.attempts.isEmpty())
        assertTrue(repository.codes().contains("CONDITION_PAUSE_POWER_CONSTRAINT"))
    }

    @Test
    fun thermalConstraintPauses() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val conditions = FakeConditions(
            ConditionSnapshot(
                charging = true,
                thermalLevel = ThermalLevel.SEVERE,
                freeStorageBytes = 1_000_000_000L,
            ),
        )
        val coordinator = coordinator(repository, conditions, testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Paused("THERMAL_CONSTRAINT"), result)
        assertEquals(SessionState.PAUSED, repository.state)
    }

    @Test
    fun budgetExhaustionSkipsTheRestAndCompletesTheQueue() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS, DemoCatalog.SUCCESS))
        repository.blockBudgetAfterFirstAttempt = true
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Completed, result)
        assertEquals(SessionState.COMPLETED, repository.state)
        assertEquals(listOf(QueueItemState.DONE, QueueItemState.SKIPPED), repository.itemStates())
        assertTrue(repository.codes().contains("ITEMS_SKIPPED_SESSION_BUDGET_EXHAUSTED"))
    }

    @Test
    fun storageFailureStopsTheQueueInsteadOfFailingTheModel() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        repository.failRecordStage = true
        val coordinator = coordinator(repository, FakeConditions(), testScheduler)

        val result = coordinator.runSession(repository.sessionId)

        assertTrue(result is CoordinatorResult.Failed)
        assertEquals("STORAGE_FAILURE", (result as CoordinatorResult.Failed).code)
        assertEquals(SessionState.FAILED, repository.state)
        assertEquals(AttemptOutcome.INTERRUPTED, repository.attempts.single().outcome)
    }

    @Test
    fun deviceMeasurementClaimedByASyntheticPlanIsRejected() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val engine = object : BenchmarkEngine {
            override val engineId: String = DemoCatalog.DEMO_ENGINE_ID
            override val origin: EvidenceOrigin = EvidenceOrigin.DEVICE_MEASUREMENT
            override suspend fun execute(
                request: BenchmarkRequest,
                events: AttemptEventSink,
            ): AttemptResult = AttemptResult(
                outcome = AttemptOutcome.PASSED,
                validity = ResultValidity.VALID,
                origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            )
        }
        val coordinator = coordinator(repository, FakeConditions(), testScheduler, engine)

        val result = coordinator.runSession(repository.sessionId)

        assertTrue(result is CoordinatorResult.Failed)
        assertEquals("ORIGIN_MISMATCH", (result as CoordinatorResult.Failed).code)
        assertEquals(SessionState.FAILED, repository.state)
        assertEquals(ResultValidity.INVALID, repository.attempts.single().validity)
    }

    @Test
    fun engineExceptionIsAnAttemptFailureNotAnInfrastructureFailure() = runTest {
        val repository = FakeSessionRepository()
        repository.createSession(plan(DemoCatalog.SUCCESS))
        val engine = object : BenchmarkEngine {
            override val engineId: String = DemoCatalog.DEMO_ENGINE_ID
            override val origin: EvidenceOrigin = EvidenceOrigin.SYNTHETIC
            override suspend fun execute(
                request: BenchmarkRequest,
                events: AttemptEventSink,
            ): AttemptResult = throw IllegalStateException("fixture engine exploded")
        }
        val coordinator = coordinator(repository, FakeConditions(), testScheduler, engine)

        val result = coordinator.runSession(repository.sessionId)

        assertEquals(CoordinatorResult.Completed, result)
        assertEquals(AttemptOutcome.FAILED, repository.attempts.single().outcome)
        assertEquals("ENGINE_EXCEPTION", repository.attempts.single().failure?.code)
    }

    private fun coordinator(
        repository: FakeSessionRepository,
        conditions: DeviceConditions,
        scheduler: TestCoroutineScheduler,
        engine: BenchmarkEngine = FakeBenchmarkEngine(),
    ): SessionCoordinator = SessionCoordinator(
        repository = repository,
        engine = engine,
        conditions = conditions,
        processInstanceId = "owner-1",
        dispatcher = StandardTestDispatcher(scheduler),
        conditionPollMs = 100L,
    )

    private fun plan(vararg scenarios: String, caseTimeoutMs: Long = 2_000L): SessionPlan =
        SessionPlan(
            profileId = DemoCatalog.PROFILE_ID,
            origin = EvidenceOrigin.SYNTHETIC,
            engineId = DemoCatalog.DEMO_ENGINE_ID,
            caseTimeoutMs = caseTimeoutMs,
            budgetMs = 60_000L,
            repeatCount = 1,
            requireCharging = true,
            items = scenarios.map { scenario ->
                PlannedItem(
                    subjectId = scenario,
                    scenarioId = scenario,
                    role = ModelRole.CHAT,
                    requestedBackend = ComputeBackend.UNSPECIFIED,
                    // One repetition of every scenario: repeatIndex is the repetition number,
                    // not the queue ordinal.
                    repeatIndex = 0,
                )
            },
        )

    private class FakeConditions(
        var value: ConditionSnapshot = ConditionSnapshot(
            charging = true,
            thermalLevel = ThermalLevel.NONE,
            freeStorageBytes = 1_000_000_000L,
        ),
    ) : DeviceConditions {
        override fun snapshot(): ConditionSnapshot = value

        override fun acquireAttemptWakeLock(timeoutMs: Long): WakeLockHandle =
            object : WakeLockHandle {
                override fun release() = Unit
            }
    }
}
