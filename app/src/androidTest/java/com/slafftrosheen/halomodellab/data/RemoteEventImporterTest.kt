package com.slafftrosheen.halomodellab.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slafftrosheen.halomodellab.bridge.BridgeEventKind
import com.slafftrosheen.halomodellab.bridge.BridgeEventPayload
import com.slafftrosheen.halomodellab.bridge.BridgeRemoteEvent
import com.slafftrosheen.halomodellab.domain.ConfigurationValue
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.PlannedItem
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.RealRunPlan
import com.slafftrosheen.halomodellab.domain.StageBudgets
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.runner.LabClock
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteEventImporterTest {
    private lateinit var database: LabDatabase
    private lateinit var sessionId: String
    private lateinit var attemptId: String
    private lateinit var importer: RemoteEventImporter

    @Before
    fun setUp() {
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, LabDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val repository = RoomSessionRepository(database, FakeClock(), "owner-1")
            sessionId = (repository.createRealSession(plan()) as CreateSessionResult.Created).sessionId
            assertTrue(repository.claimSession(sessionId, "owner-1"))
            val ready = repository.beginAttempt(sessionId, "owner-1") as BeginAttempt.Ready
            attemptId = ready.request.attemptId
            importer = RemoteEventImporter(database)
            assertTrue(importer.bindAcceptedRun(sessionId, "epoch-1", "remote-run-1", "server-1") is RemoteRunBindingResult.Bound)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateEventIsCountedOnceAndCursorAdvancesOnlyOnce() = runTest {
        val event = event(sequence = 1L, code = "LOAD_STARTED")

        val first = importer.importPage(sessionId, listOf(event))
        val replay = importer.importPage(sessionId, listOf(event))

        assertEquals(RemoteEventPageResult.Imported(1L, 1, 0), first)
        assertEquals(RemoteEventPageResult.Imported(1L, 0, 1), replay)
        assertEquals(1, database.labDao().remoteEventCount(sessionId))
        assertEquals(1L, database.labDao().remoteBinding(sessionId)?.highestImportedSequence)
        assertEquals(
            attemptId,
            database.labDao().remoteEventsAfter(sessionId, "epoch-1", "remote-run-1", 0L, 1)
                .single().attemptId,
        )
        val exportSnapshot = RoomSessionRepository(database, FakeClock(), "owner-1").exportSnapshot(sessionId)
        assertEquals(EvidenceOrigin.DEVICE_MEASUREMENT, exportSnapshot?.detail?.summary?.origin)
        assertEquals(1, exportSnapshot?.realEvidence?.events?.size)
        assertEquals(attemptId, exportSnapshot?.realEvidence?.events?.single()?.attemptId)
    }

    @Test
    fun changedPayloadUnderCommittedIdentityIsRejectedWithoutOverwrite() = runTest {
        importer.importPage(sessionId, listOf(event(1L, "LOAD_STARTED")))

        val result = importer.importPage(sessionId, listOf(event(1L, "EXECUTE_STARTED")))

        assertEquals(RemoteEventPageResult.ConflictingPayload(1L), result)
        assertEquals(1L, database.labDao().remoteBinding(sessionId)?.highestImportedSequence)
        assertEquals(1, database.labDao().remoteEventCount(sessionId))
    }

    @Test
    fun sequenceGapDoesNotAdvanceCursor() = runTest {
        val result = importer.importPage(sessionId, listOf(event(2L, "EXECUTE_STARTED")))

        assertEquals(RemoteEventPageResult.Gap(expectedSequence = 1L, receivedSequence = 2L), result)
        assertEquals(0L, database.labDao().remoteBinding(sessionId)?.highestImportedSequence)
        assertEquals(0, database.labDao().remoteEventCount(sessionId))
    }

    @Test
    fun unverifiedPayloadHashIsRejected() = runTest {
        val bad = BridgeRemoteEvent(
            key = com.slafftrosheen.halomodellab.domain.RemoteEventKey("epoch-1", "remote-run-1", 1L),
            payload = payload("LOAD_STARTED"),
            payloadSha256 = HASH,
        )

        assertEquals(RemoteEventPageResult.Rejected("EVENT_HASH_MISMATCH"), importer.importPage(sessionId, listOf(bad)))
        assertEquals(0L, database.labDao().remoteBinding(sessionId)?.highestImportedSequence)
    }

    @Test
    fun syntheticSessionCannotAcceptRemoteDeviceRunBinding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val syntheticDatabase = Room.inMemoryDatabaseBuilder(context, LabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = RoomSessionRepository(syntheticDatabase, FakeClock(), "owner-synthetic")
            val syntheticId = (repository.createSession(syntheticPlan()) as CreateSessionResult.Created).sessionId
            val result = RemoteEventImporter(syntheticDatabase).bindAcceptedRun(
                syntheticId, "epoch-1", "remote-run-1", "server-1",
            )
            assertEquals(RemoteRunBindingResult.SessionOriginMismatch, result)
        } finally {
            syntheticDatabase.close()
        }
    }

    private fun event(sequence: Long, code: String): BridgeRemoteEvent {
        val typedPayload = payload(code)
        val bytes = typedPayload.canonicalJson().toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return BridgeRemoteEvent(
            key = com.slafftrosheen.halomodellab.domain.RemoteEventKey("epoch-1", "remote-run-1", sequence),
            payload = typedPayload,
            payloadSha256 = hash,
        )
    }

    private fun payload(code: String) = BridgeEventPayload(
        kind = BridgeEventKind.ATTEMPT_STAGE,
        attemptId = attemptId,
        stage = TestStage.LOAD,
        eventCode = code,
        outcome = null,
        validity = null,
        failureCode = null,
        failureDomain = null,
        stopDisposition = null,
        workerInstanceId = null,
        artifactId = null,
        artifactBytes = null,
        artifactSha256 = null,
        unavailableReason = null,
    )

    private fun plan() = RealRunPlan(
        sessionId = "00000000-0000-0000-0000-000000000081",
        profileId = "text-smoke-v1",
        suiteId = "text-smoke",
        suiteVersion = "1",
        fixtureManifestSha256 = null,
        qualificationPolicySha256 = HASH,
        stageBudgets = StageBudgets(),
        sessionBudgetMs = 600_000L,
        requireCharging = true,
        capabilitySnapshot = null,
        cases = listOf(
            RealCasePlan(
                queueItemId = "00000000-0000-0000-0000-000000000082",
                providerId = "local-provider",
                modelId = "model-1",
                artifactGenerationId = "generation-1",
                artifactManifest = null,
                artifactUnavailableReason = "BRIDGE_NOT_CONNECTED",
                role = ModelRole.CHAT,
                requestedBackend = RealBackend.AUTO,
                requestedConfiguration = listOf(ConfigurationValue("contextTokens", "1024")),
                suiteId = "text-smoke",
                suiteVersion = "1",
                caseId = "smoke-1",
                fixtureManifestSha256 = null,
                repetitionIndex = 0,
                maximumOutputTokens = 64,
            ),
        ),
    )

    private fun syntheticPlan() = SessionPlan(
        profileId = "DEMO_INFRASTRUCTURE_V1",
        origin = EvidenceOrigin.SYNTHETIC,
        engineId = "demo-engine-v1",
        caseTimeoutMs = 2_000L,
        budgetMs = 60_000L,
        repeatCount = 1,
        requireCharging = false,
        items = listOf(PlannedItem("demo/success", "demo/success", ModelRole.CHAT, ComputeBackend.UNSPECIFIED, 0)),
    )

    private class FakeClock : LabClock {
        private var elapsed = 1_000L
        override fun wallTimeMs(): Long = elapsed
        override fun elapsedRealtimeMs(): Long = elapsed++
    }

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
