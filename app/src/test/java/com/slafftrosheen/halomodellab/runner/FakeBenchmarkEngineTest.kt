package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.domain.AttemptEventSink
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.TestStage
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level checks for the demo engine.
 *
 * Added in HML-L2, NOT executed: build and test runs stay NOT_RUN.
 */
class FakeBenchmarkEngineTest {

    private val engine = FakeBenchmarkEngine()

    private class RecordingSink : AttemptEventSink {
        val codes = mutableListOf<String>()
        override suspend fun record(stage: TestStage, code: String) {
            codes += code
        }
    }

    private fun request(
        scenario: String,
        origin: EvidenceOrigin = EvidenceOrigin.SYNTHETIC,
        subjectId: String = "demo/subject",
    ): BenchmarkRequest = BenchmarkRequest(
        sessionId = "session",
        attemptId = "attempt",
        subjectId = subjectId,
        caseId = scenario,
        caseTimeoutMs = 5_000L,
        origin = origin,
        role = ModelRole.CHAT,
        requestedBackend = ComputeBackend.UNSPECIFIED,
        scenarioId = scenario,
    )

    @Test
    fun successIsExplicitlySynthetic() = runTest {
        val sink = RecordingSink()

        val result = engine.execute(request(DemoCatalog.SUCCESS), sink)

        assertEquals(AttemptOutcome.PASSED, result.outcome)
        assertEquals(ResultValidity.VALID, result.validity)
        assertEquals(EvidenceOrigin.SYNTHETIC, result.origin)
        assertNull(result.failure)
        assertTrue(sink.codes.contains("SIMULATION_PREFLIGHT"))
        assertTrue(sink.codes.contains("SIMULATION_LOAD_STARTED"))
        assertTrue(sink.codes.contains("SIMULATION_EXECUTE_STARTED"))
        assertTrue(sink.codes.contains("SIMULATION_FINISHED"))
    }

    @Test
    fun loadFailureIsObservedButNotAModelVerdict() = runTest {
        val result = engine.execute(request(DemoCatalog.LOAD_FAILURE), RecordingSink())

        assertEquals(AttemptOutcome.FAILED, result.outcome)
        assertEquals(ResultValidity.VALID, result.validity)
        assertEquals("SIMULATED_ADAPTER_LOAD_FAILURE", result.failure?.code)
        assertEquals(FailureDomain.ADAPTER, result.failure?.domain)
        assertEquals(EvidenceOrigin.SYNTHETIC, result.origin)
    }

    @Test
    fun emptyOutputStaysUnknown() = runTest {
        val result = engine.execute(request(DemoCatalog.EMPTY_OUTPUT), RecordingSink())

        assertEquals(AttemptOutcome.FAILED, result.outcome)
        assertEquals("SIMULATED_EMPTY_OUTPUT", result.failure?.code)
        assertEquals(FailureDomain.UNKNOWN, result.failure?.domain)
    }

    @Test
    fun timeoutIsCooperativeCancellation() = runTest {
        val sink = RecordingSink()

        val job = launch { engine.execute(request(DemoCatalog.TIMEOUT), sink) }
        advanceTimeBy(500L)
        runCurrent()

        assertTrue("A cooperative fixture waits for cancellation", job.isActive)
        job.cancelAndJoin()
        assertTrue(sink.codes.contains("SIMULATION_EXECUTE_STARTED"))
    }

    @Test
    fun engineRefusesForeignOrigin() = runTest {
        val failure = runCatching {
            engine.execute(
                request(DemoCatalog.SUCCESS, origin = EvidenceOrigin.DEVICE_MEASUREMENT),
                RecordingSink(),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun engineRefusesASubjectOutsideTheDemoNamespace() = runTest {
        val failure = runCatching {
            engine.execute(
                request(DemoCatalog.SUCCESS, subjectId = "qwen-real-model"),
                RecordingSink(),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
