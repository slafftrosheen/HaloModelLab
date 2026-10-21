package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.domain.AttemptEventSink
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.BenchmarkEngine
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.TestStage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * Controlled synthetic engine.
 *
 * It creates no model, loads no weights and measures nothing. Every delay here is a fixture,
 * not model latency. It is cooperative on purpose: cancellation only works because this code
 * suspends; a native JNI hang cannot be stopped this way.
 */
class FakeBenchmarkEngine : BenchmarkEngine {

    override val engineId: String = DemoCatalog.DEMO_ENGINE_ID

    override val origin: EvidenceOrigin = EvidenceOrigin.SYNTHETIC

    override suspend fun execute(
        request: BenchmarkRequest,
        events: AttemptEventSink,
    ): AttemptResult {
        require(request.origin == origin) {
            "Demo engine refuses non-synthetic origin ${request.origin}"
        }
        require(request.subjectId.startsWith(SUBJECT_PREFIX)) {
            "Demo engine refuses a subject outside $SUBJECT_PREFIX"
        }
        val scenario = requireNotNull(request.scenarioId) { "Demo scenario is required" }
        require(scenario in DemoCatalog.scenarioIds) { "Unknown demo scenario $scenario" }

        events.record(TestStage.PREFLIGHT, "SIMULATION_PREFLIGHT")
        delay(PREFLIGHT_FIXTURE_MS)
        events.record(TestStage.LOAD, "SIMULATION_LOAD_STARTED")
        delay(LOAD_FIXTURE_MS)

        if (scenario == DemoCatalog.LOAD_FAILURE) {
            return failure(
                code = "SIMULATED_ADAPTER_LOAD_FAILURE",
                domain = FailureDomain.ADAPTER,
                detail = "Controlled fixture: no real adapter or model was loaded.",
            )
        }

        events.record(TestStage.EXECUTE, "SIMULATION_EXECUTE_STARTED")
        when (scenario) {
            DemoCatalog.TIMEOUT -> awaitCancellation()
            DemoCatalog.EMPTY_OUTPUT -> return failure(
                code = "SIMULATED_EMPTY_OUTPUT",
                domain = FailureDomain.UNKNOWN,
                detail = "Controlled fixture: empty output, no real inference.",
            )

            else -> delay(EXECUTE_FIXTURE_MS)
        }

        events.record(TestStage.UNLOAD, "SIMULATION_FINISHED")
        return AttemptResult(
            outcome = AttemptOutcome.PASSED,
            validity = ResultValidity.VALID,
            origin = origin,
        )
    }

    private fun failure(
        code: String,
        domain: FailureDomain,
        detail: String,
    ): AttemptResult = AttemptResult(
        outcome = AttemptOutcome.FAILED,
        validity = ResultValidity.VALID,
        origin = origin,
        failure = FailureInfo(
            code = code,
            domain = domain,
            confidence = EvidenceConfidence.OBSERVED,
            detail = detail,
        ),
    )

    private companion object {
        const val SUBJECT_PREFIX = "demo/"
        const val PREFLIGHT_FIXTURE_MS = 20L
        const val LOAD_FIXTURE_MS = 50L
        const val EXECUTE_FIXTURE_MS = 100L
    }
}
