package com.slafftrosheen.halomodellab.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.PlannedItem
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.RealRunPlan
import com.slafftrosheen.halomodellab.domain.StageBudgets
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.runner.LabClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation checks for transactional invariants.
 *
 * Added in HML-L1, NOT executed: build, instrumentation and device runs stay NOT_RUN.
 */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var database: LabDatabase
    private lateinit var clock: FakeClock
    private lateinit var repository: RoomSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = FakeClock()
        repository = RoomSessionRepository(
            database = database,
            clock = clock,
            processInstanceId = "owner-1",
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun secondOpenSessionIsRejected() = runTest {
        val first = repository.createSession(plan()) as CreateSessionResult.Created
        val second = repository.createSession(plan())

        assertTrue(second is CreateSessionResult.Blocked)
        assertEquals(first.sessionId, (second as CreateSessionResult.Blocked).existingSessionId)
        assertEquals(1, database.labDao().observeSummaryRows().first().size)
    }

    @Test
    fun realPlanIsPersistedWithDeviceOriginAndUnavailableArtifactReason() = runTest {
        val plan = realPlan()
        val created = repository.createRealSession(plan) as CreateSessionResult.Created

        val detail = detail(created.sessionId)
        val snapshot = database.labDao().realPlanSnapshot(created.sessionId)
        val model = database.labDao().modelSnapshotsForSession(created.sessionId).single()

        assertEquals(EvidenceOrigin.DEVICE_MEASUREMENT, detail.summary.origin)
        assertEquals(plan.canonicalJson(), detail.planJson)
        assertEquals(plan.digestSha256(), snapshot?.planDigestSha256)
        assertEquals("BRIDGE_NOT_CONNECTED", model.availabilityCode)
        assertNull(model.manifestSha256)
    }

    @Test
    fun concurrentOpenSessionCreationAdmitsOnlyOne() = runTest {
        val results = listOf(
            async(Dispatchers.IO) { repository.createSession(plan()) },
            async(Dispatchers.IO) { repository.createSession(plan()) },
        ).awaitAll()

        assertEquals(1, results.count { it is CreateSessionResult.Created })
        assertEquals(1, results.count { it is CreateSessionResult.Blocked })
        assertEquals(1, database.labDao().observeSummaryRows().first().size)
    }

    @Test
    fun repeatedStartDoesNotCreateASecondAttempt() = runTest {
        val sessionId = createSession()
        assertTrue(repository.claimSession(sessionId, "owner-1"))
        assertFalse(repository.claimSession(sessionId, "owner-1"))

        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        assertFalse(repository.claimSession(sessionId, "owner-1"))

        val detail = detail(sessionId)
        assertEquals(1, detail.queue.sumOf { it.attempts.size })
        assertEquals(begun.request.attemptId, detail.openAttemptId)
    }

    @Test
    fun beginAttemptWritesBeforeExecutionEvent() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        repository.beginAttempt(sessionId, "owner-1")

        val detail = detail(sessionId)
        assertTrue(detail.events.any { it.code == "BEFORE_EXECUTION" })
        assertEquals(QueueItemState.ACTIVE, detail.queue.single().state)
    }

    @Test
    fun terminalResultIsWrittenOnceAndLateRepeatIsIgnored() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready

        assertTrue(repository.finishAttempt(begun.request.attemptId, passed()))
        assertFalse(repository.finishAttempt(begun.request.attemptId, failed()))

        val attempt = detail(sessionId).queue.single().attempts.single()
        assertEquals(AttemptOutcome.PASSED, attempt.outcome)
        assertEquals(ResultValidity.VALID, attempt.validity)
        assertTrue(detail(sessionId).events.any { it.code == "LATE_RESULT_IGNORED" })
    }

    @Test
    fun eventSequenceIsUniqueAndIncreasing() = runTest {
        val sessionId = createSession(repeats = 2)
        repository.claimSession(sessionId, "owner-1")

        repeat(2) {
            val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
            repository.recordStage(begun.request.attemptId, TestStage.LOAD, "SIMULATION_LOAD_STARTED")
            repository.finishAttempt(begun.request.attemptId, passed())
        }

        val sequences = detail(sessionId).events.map { it.seq }
        assertEquals(sequences.sorted(), sequences)
        assertEquals(sequences.distinct().size, sequences.size)
    }

    @Test
    fun secondAttemptUsesANewAttemptId() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")

        val first = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        repository.finishAttempt(first.request.attemptId, failed())
        val second = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready

        assertNotEquals(first.request.attemptId, second.request.attemptId)
        assertEquals(2, second.attemptNumber)
    }

    @Test
    fun pauseAfterTheLastAttemptCompletesTheSession() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        repository.finishAttempt(begun.request.attemptId, passed())

        assertTrue(repository.requestPause(sessionId, "owner-1"))
        assertEquals(SessionState.COMPLETED, repository.enterPaused(sessionId, "owner-1"))
    }

    @Test
    fun completedSessionMayContainFailedAttempts() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        repository.finishAttempt(begun.request.attemptId, failed())

        assertEquals(SessionState.COMPLETED, repository.enterCompleted(sessionId, "owner-1"))
        val detail = detail(sessionId)
        assertEquals(SessionState.COMPLETED, detail.summary.state)
        assertEquals(AttemptOutcome.FAILED, detail.queue.single().attempts.single().outcome)
    }

    @Test
    fun recoveryInterruptsTheOpenAttemptAndKeepsCompletedResults() = runTest {
        val sessionId = createSession(repeats = 2)
        repository.claimSession(sessionId, "owner-1")

        val first = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        repository.finishAttempt(first.request.attemptId, passed())
        val second = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready

        val recovery = repository.recoverInterruptedOwner("owner-2")
        assertEquals(listOf(sessionId), recovery.recoveredSessionIds)
        assertEquals(listOf(second.request.attemptId), recovery.interruptedAttemptIds)

        val detail = detail(sessionId)
        assertEquals(SessionState.INTERRUPTED, detail.summary.state)
        val firstAttempt = detail.queue[0].attempts.single()
        val secondAttempt = detail.queue[1].attempts.single()
        assertEquals(AttemptOutcome.PASSED, firstAttempt.outcome)
        assertEquals(AttemptOutcome.INTERRUPTED, secondAttempt.outcome)
        assertEquals(ResultValidity.UNKNOWN, secondAttempt.validity)
        assertEquals(QueueItemState.NEEDS_DECISION, detail.queue[1].state)
        assertNull(secondAttempt.elapsedMs)
    }

    @Test
    fun recoveryDoesNotRewriteACompletedSession() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        repository.finishAttempt(begun.request.attemptId, passed())
        repository.enterCompleted(sessionId, "owner-1")

        val recovery = repository.recoverInterruptedOwner("owner-2")

        assertTrue(recovery.recoveredSessionIds.isEmpty())
        assertEquals(SessionState.COMPLETED, detail(sessionId).summary.state)
    }

    @Test
    fun retryAfterRecoveryStartsANewAttemptOfTheSameItem() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val first = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
        val recovery = repository.recoverInterruptedOwner("owner-2")
        val itemId = recovery.needsDecisionItemIds.single()

        assertTrue(repository.retryItem(itemId))
        assertTrue(repository.claimSession(sessionId, "owner-2"))
        val retry = repository.beginAttempt(sessionId, "owner-2") as BeginAttempt.Ready

        assertEquals(first.queueItemId, retry.queueItemId)
        assertNotEquals(first.request.attemptId, retry.request.attemptId)
        assertEquals(2, retry.attemptNumber)
    }

    @Test
    fun cancelledSessionSkipsFurtherAttempts() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        repository.requestStop(sessionId, "owner-1")
        assertEquals(SessionState.CANCELLED, repository.enterCancelled(sessionId, "owner-1", "USER_STOP"))

        assertTrue(repository.beginAttempt(sessionId, "owner-1") is BeginAttempt.Blocked)
        assertEquals(SessionState.CANCELLED, detail(sessionId).summary.state)
    }

    @Test
    fun storageFailurePropagatesInsteadOfReportingSuccess() = runTest {
        val sessionId = createSession()
        repository.claimSession(sessionId, "owner-1")
        val begun = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready

        database.close()

        try {
            repository.recordStage(begun.request.attemptId, TestStage.LOAD, "SIMULATION_LOAD_STARTED")
            fail("A storage failure must propagate to the caller")
        } catch (expected: Exception) {
            assertNotNull(expected.message)
        }
    }

    private suspend fun createSession(repeats: Int = 1): String {
        val result = repository.createSession(plan(repeats))
        return (result as CreateSessionResult.Created).sessionId
    }

    private suspend fun detail(sessionId: String): SessionDetail =
        repository.observeSession(sessionId).first()!!

    private fun plan(repeats: Int = 1): SessionPlan = SessionPlan(
        profileId = "DEMO_INFRASTRUCTURE_V1",
        origin = EvidenceOrigin.SYNTHETIC,
        engineId = "demo-engine-v1",
        caseTimeoutMs = 5_000L,
        budgetMs = 60_000L,
        repeatCount = repeats,
        requireCharging = true,
        items = (0 until repeats).map { index ->
            PlannedItem(
                subjectId = "demo/success",
                scenarioId = "demo/success",
                role = ModelRole.CHAT,
                requestedBackend = ComputeBackend.UNSPECIFIED,
                repeatIndex = index,
            )
        },
    )

    private fun realPlan(): RealRunPlan = RealRunPlan(
        sessionId = "00000000-0000-0000-0000-000000000031",
        profileId = "text-smoke-v1",
        suiteId = "text-smoke",
        suiteVersion = "1",
        fixtureManifestSha256 = null,
        qualificationPolicySha256 = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
        stageBudgets = StageBudgets(),
        sessionBudgetMs = 600_000L,
        requireCharging = true,
        capabilitySnapshot = null,
        cases = listOf(
            RealCasePlan(
                queueItemId = "00000000-0000-0000-0000-000000000032",
                providerId = "local-provider",
                modelId = "model-1",
                artifactGenerationId = "generation-1",
                artifactManifest = null,
                artifactUnavailableReason = "BRIDGE_NOT_CONNECTED",
                role = ModelRole.CHAT,
                requestedBackend = RealBackend.AUTO,
                requestedConfiguration = emptyList(),
                suiteId = "text-smoke",
                suiteVersion = "1",
                caseId = "fixed-smoke-1",
                fixtureManifestSha256 = null,
                repetitionIndex = 0,
                maximumOutputTokens = 64,
            ),
        ),
    )

    private fun passed(): AttemptResult = AttemptResult(
        outcome = AttemptOutcome.PASSED,
        validity = ResultValidity.VALID,
        origin = EvidenceOrigin.SYNTHETIC,
    )

    private fun failed(): AttemptResult = AttemptResult(
        outcome = AttemptOutcome.FAILED,
        validity = ResultValidity.VALID,
        origin = EvidenceOrigin.SYNTHETIC,
        failure = FailureInfo(
            code = "SIMULATED_ADAPTER_LOAD_FAILURE",
            domain = FailureDomain.ADAPTER,
            confidence = EvidenceConfidence.OBSERVED,
            detail = "Controlled fixture: no real adapter was loaded.",
        ),
    )

    private class FakeClock(
        var wall: Long = 1_000_000L,
        var elapsed: Long = 10_000L,
    ) : LabClock {
        override fun wallTimeMs(): Long = wall
        override fun elapsedRealtimeMs(): Long = elapsed
    }
}
