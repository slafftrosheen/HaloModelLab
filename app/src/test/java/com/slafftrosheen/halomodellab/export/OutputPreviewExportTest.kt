package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.bridge.BridgeEventKind
import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import com.slafftrosheen.halomodellab.data.RealPlanSnapshotEntity
import com.slafftrosheen.halomodellab.data.RemoteEventEntity
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.profiles.TextProfileRegistry
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputPreviewExportTest {
    @Test
    fun includesOnlyCompleteHashVerifiedFixedSmokeOutput() {
        val payload = eventJson(text = "READY", truncated = false)
        val previews = RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(
            detail(),
            realSnapshot(payload),
        )

        assertEquals(1, previews.size)
        assertEquals("READY", previews.single().text)
        assertEquals(sha256("READY".toByteArray()), previews.single().sha256)
    }

    @Test
    fun rejectsTruncatedOrTamperedOutputAndNonDeviceOrigins() {
        assertTrue(RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(
            detail(), realSnapshot(eventJson(text = "READY", truncated = true)),
        ).isEmpty())

        val payload = eventJson(text = "READY", truncated = false)
        assertTrue(RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(
            detail(), realSnapshot(payload, eventHash = "f".repeat(64)),
        ).isEmpty())
        assertTrue(RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(
            detail(), realSnapshot(eventJson(text = "READY", truncated = false, outputHash = "e".repeat(64))),
        ).isEmpty())
        assertTrue(RealEvidenceExportJson.verifiedFixedSmokeOutputPreviews(
            detail(origin = EvidenceOrigin.SYNTHETIC), realSnapshot(payload),
        ).isEmpty())
    }

    private fun realSnapshot(payload: String, eventHash: String = sha256(payload.toByteArray())): RealEvidenceExportSnapshot {
        val fixture = requireNotNull(TextProfileRegistry.resolve("fast-smoke", "1"))
        return RealEvidenceExportSnapshot(
            plan = RealPlanSnapshotEntity(
                sessionId = SESSION_ID,
                planSchemaVersion = 1,
                exportSchemaVersion = 1,
                suiteVersion = "1",
                fixtureManifestSha256 = fixture.fixtureManifestSha256(),
                qualificationPolicyVersion = 1,
                qualificationPolicySha256 = "a".repeat(64),
                protocolMajor = 1,
                protocolMinor = 0,
                planDigestSha256 = "b".repeat(64),
                capabilitySnapshotSha256 = null,
                capabilitySnapshotJson = null,
            ),
            models = emptyList(),
            configurations = emptyList(),
            binding = null,
            events = listOf(RemoteEventEntity(
                serverEpoch = "epoch",
                remoteRunId = "run",
                remoteSequence = 1,
                sessionId = SESSION_ID,
                attemptId = ATTEMPT_ID,
                payloadHashSha256 = eventHash,
                payloadJson = payload,
                receivedWallTimeMs = 1,
            )),
            artifacts = emptyList(),
            qualifications = emptyList(),
            truncatedSections = emptySet(),
        )
    }

    private fun detail(origin: EvidenceOrigin = EvidenceOrigin.DEVICE_MEASUREMENT): SessionDetail {
        val attempt = AttemptView(
            attemptId = ATTEMPT_ID,
            number = 1,
            stage = TestStage.EXECUTE,
            outcome = AttemptOutcome.PASSED,
            validity = ResultValidity.VALID,
            failure = null,
            deadlineReason = null,
            ownerInstanceId = "owner",
            startedWallTimeMs = 1,
            finishedWallTimeMs = 2,
            elapsedMs = 1,
        )
        return SessionDetail(
            summary = SessionSummary(SESSION_ID, SessionState.COMPLETED, origin,
                "halo", "halo-fast-smoke-cpu-v1", 1, 2, 1, 1),
            queue = listOf(QueueItemView(
                queueItemId = ITEM_ID,
                ordinal = 0,
                subjectId = "provider.litert.local/model/generation",
                scenarioId = "short-ready",
                state = QueueItemState.DONE,
                repeatIndex = 0,
                role = ModelRole.FAST,
                requestedBackend = ComputeBackend.CPU,
                attempts = listOf(attempt),
            )),
            events = emptyList(),
            telemetry = emptyList(),
            caseTimeoutMs = 10_000,
            budgetMs = 20_000,
            repeatCount = 1,
            requireCharging = true,
            persistedActiveMs = 1,
            sessionFailure = null,
            planJson = "{}",
            openAttemptId = null,
        )
    }

    private fun eventJson(
        text: String,
        truncated: Boolean,
        outputHash: String = sha256(text.toByteArray()),
    ): String =
        """{"kind":"${BridgeEventKind.ATTEMPT_TERMINAL.name}","attemptId":"$ATTEMPT_ID","outputPreview":"$text","outputBytes":${text.toByteArray().size},"outputSha256":"$outputHash","outputTruncated":$truncated}"""

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val SESSION_ID = "00000000-0000-0000-0000-000000000001"
        private const val ITEM_ID = "00000000-0000-0000-0000-000000000002"
        private const val ATTEMPT_ID = "00000000-0000-0000-0000-000000000003"
    }
}
