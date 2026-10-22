package com.slafftrosheen.halomodellab.bridge

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.ResultValidity
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeEventPayloadTest {
    @Test
    fun terminalEvidenceHasStableNullableFieldsAndBoundedOutput() {
        val event = BridgeEventPayload(
            kind = BridgeEventKind.ATTEMPT_TERMINAL,
            attemptId = "00000000-0000-0000-0000-000000000001",
            stage = null,
            eventCode = "TERMINAL",
            outcome = AttemptOutcome.PASSED,
            validity = ResultValidity.VALID,
            failureCode = null,
            failureDomain = null,
            stopDisposition = null,
            workerInstanceId = null,
            artifactId = null,
            artifactBytes = null,
            artifactSha256 = null,
            unavailableReason = null,
            outputPreview = "READY",
            outputBytes = 5,
            outputSha256 = "a".repeat(64),
            outputTruncated = false,
            loadMs = 11,
            timeToFirstTokenMs = null,
            generationMs = 17,
            effectiveBackendCode = "CPU",
            runtimeVersion = "litert-v1",
        )

        val canonical = event.canonicalJson()
        assertTrue(canonical.contains("\"outputPreview\":\"READY\""))
        assertTrue(canonical.contains("\"outputBytes\":5"))
        assertTrue(canonical.contains("\"outputTruncated\":false"))
        assertTrue(canonical.contains("\"timeToFirstTokenMs\":null"))
        assertTrue(canonical.contains("\"generationMs\":17"))
        assertTrue(canonical.toByteArray(Charsets.UTF_8).size <= BridgeLimits.MAX_EVENT_PAYLOAD_BYTES)
    }
}
