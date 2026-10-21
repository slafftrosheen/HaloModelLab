package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.RemoteEventEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealEvidenceExportJsonTest {
    @Test
    fun remoteEventEnvelopeEscapesPayloadAsDataAndRetainsExactIdentity() {
        val line = RealEvidenceExportJson.remoteEvent(
            RemoteEventEntity(
                serverEpoch = "epoch-1",
                remoteRunId = "run-1",
                remoteSequence = 4L,
                sessionId = "session-1",
                attemptId = "attempt-1",
                payloadHashSha256 = "a".repeat(64),
                payloadJson = "{\"eventCode\":\"OUTPUT\",\"text\":\"\\\"quoted\\\"\"}",
                receivedWallTimeMs = 100L,
            ),
        )

        assertTrue(line.startsWith("{\"serverEpoch\":\"epoch-1\""))
        assertTrue(line.contains("\"sequence\":4"))
        assertTrue(line.contains("\\\"eventCode\\\""))
        assertFalse(line.contains("\"payloadJson\":{\"eventCode\""))
    }
}
