package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.bridge.BridgeEventKind
import com.slafftrosheen.halomodellab.bridge.BridgeEventPayload
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.ResultValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualificationObservationMapperTest {
    @Test fun completeFixtureMismatchIsAValidModelAssertion() {
        val fixture = com.slafftrosheen.halomodellab.profiles.TextProfileRegistry.resolve("fast-smoke", "1")!!
        val event = terminalEvent(
            outcome = AttemptOutcome.PASSED,
            validity = ResultValidity.VALID,
            preview = "NOT READY",
            truncated = false,
        )

        val observation = QualificationObservationMapper.fromTerminal(
            event = event,
            casePlan = casePlan(fixture.fixtureManifestSha256()),
            scope = null,
            expectedText = "READY",
        )

        assertEquals(CheckAssertion.FAIL, observation?.assertion)
        assertEquals(AttemptOutcome.FAILED, observation?.outcome)
        assertEquals(FailureDomain.MODEL, observation?.failureDomain)
    }

    @Test fun truncatedMismatchRemainsUnknown() {
        val fixture = com.slafftrosheen.halomodellab.profiles.TextProfileRegistry.resolve("fast-smoke", "1")!!
        val observation = QualificationObservationMapper.fromTerminal(
            event = terminalEvent(AttemptOutcome.FAILED, ResultValidity.VALID, "NOT", true),
            casePlan = casePlan(fixture.fixtureManifestSha256()),
            scope = null,
            expectedText = "READY",
        )

        assertEquals(CheckAssertion.UNKNOWN, observation?.assertion)
    }

    @Test fun missingExecutionScopeKeepsIdentityAndAssertionUnknown() {
        val case = RealCasePlan(
            queueItemId = "11111111-1111-4111-8111-111111111111",
            providerId = "provider.litert.local",
            modelId = "model-a",
            artifactGenerationId = "generation-a",
            artifactManifest = null,
            artifactUnavailableReason = "MANIFEST_UNKNOWN",
            role = ModelRole.FAST,
            requestedBackend = RealBackend.CPU,
            requestedConfiguration = emptyList(),
            suiteId = "fast-smoke",
            suiteVersion = "1",
            caseId = "short-ready",
            fixtureManifestSha256 = null,
            repetitionIndex = 0,
            maximumOutputTokens = 8,
        )
        val event = BridgeEventPayload(
            kind = BridgeEventKind.ATTEMPT_TERMINAL,
            attemptId = "22222222-2222-4222-8222-222222222222",
            stage = null,
            eventCode = "ATTEMPT_FINISHED",
            outcome = AttemptOutcome.PASSED,
            validity = ResultValidity.UNKNOWN,
            failureCode = null,
            failureDomain = null,
            stopDisposition = null,
            workerInstanceId = null,
            artifactId = null,
            artifactBytes = null,
            artifactSha256 = null,
            unavailableReason = null,
        )

        val observation = QualificationObservationMapper.fromTerminal(event, case, null, expectedText = "READY")

        assertEquals(EvidenceOrigin.DEVICE_MEASUREMENT, observation?.origin)
        assertNull(observation?.scopeDigestSha256)
        assertEquals(CheckAssertion.UNKNOWN, observation?.assertion)
    }

    private fun casePlan(fixtureSha256: String) = RealCasePlan(
        queueItemId = "11111111-1111-4111-8111-111111111111",
        providerId = "provider.litert.local",
        modelId = "model-a",
        artifactGenerationId = "generation-a",
        artifactManifest = null,
        artifactUnavailableReason = "MANIFEST_UNKNOWN",
        role = ModelRole.FAST,
        requestedBackend = RealBackend.CPU,
        requestedConfiguration = emptyList(),
        suiteId = "fast-smoke",
        suiteVersion = "1",
        caseId = "short-ready",
        fixtureManifestSha256 = fixtureSha256,
        repetitionIndex = 0,
        maximumOutputTokens = 8,
    )

    private fun terminalEvent(
        outcome: AttemptOutcome,
        validity: ResultValidity,
        preview: String,
        truncated: Boolean,
    ) = BridgeEventPayload(
        kind = BridgeEventKind.ATTEMPT_TERMINAL,
        attemptId = "22222222-2222-4222-8222-222222222222",
        stage = null,
        eventCode = "ATTEMPT_FINISHED",
        outcome = outcome,
        validity = validity,
        failureCode = if (outcome == AttemptOutcome.FAILED) "MODEL_OUTPUT_MISMATCH" else null,
        failureDomain = if (outcome == AttemptOutcome.FAILED) FailureDomain.MODEL else null,
        stopDisposition = null,
        workerInstanceId = "worker-1",
        artifactId = null,
        artifactBytes = null,
        artifactSha256 = null,
        unavailableReason = null,
        outputPreview = preview,
        outputBytes = preview.toByteArray().size.toLong(),
        outputSha256 = "0".repeat(64),
        outputTruncated = truncated,
    )
}
