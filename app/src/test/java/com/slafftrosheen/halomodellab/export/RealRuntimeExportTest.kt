package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import com.slafftrosheen.halomodellab.data.RemoteEventEntity
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealRuntimeExportTest {
    @Test
    fun runtimeIsExportedOnlyWhenAllTerminalFactsAreHashValidAndAgree() {
        val snapshot = snapshot(
            event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"litert-1.2\"}", 1),
            event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"litert-1.2\"}", 2),
        )
        assertEquals("litert-1.2", RealEvidenceExportJson.consistentTerminalRuntimeVersion(snapshot))
    }

    @Test
    fun missingConflictingOrTamperedRuntimeEvidenceRemainsUnknown() {
        assertNull(RealEvidenceExportJson.consistentTerminalRuntimeVersion(snapshot(
            event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":null}", 1),
        )))
        assertNull(RealEvidenceExportJson.consistentTerminalRuntimeVersion(snapshot(
            event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"one\"}", 1),
            event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"two\"}", 2),
        )))
        val tampered = event("{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"actual\"}", 1)
            .copy(payloadJson = "{\"kind\":\"ATTEMPT_TERMINAL\",\"runtimeVersion\":\"changed\"}")
        assertNull(RealEvidenceExportJson.consistentTerminalRuntimeVersion(snapshot(tampered)))
    }

    private fun snapshot(vararg events: RemoteEventEntity) = RealEvidenceExportSnapshot(
        plan = null, models = emptyList(), configurations = emptyList(), binding = null,
        events = events.toList(), artifacts = emptyList(), qualifications = emptyList(), truncatedSections = emptySet(),
    )

    private fun event(payload: String, sequence: Long) = RemoteEventEntity(
        serverEpoch = "epoch-1", remoteRunId = "run-1", remoteSequence = sequence,
        sessionId = "11111111-2222-4333-8444-555555555555", attemptId = null,
        payloadHashSha256 = sha256(payload), payloadJson = payload, receivedWallTimeMs = sequence,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
