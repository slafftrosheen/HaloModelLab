package com.slafftrosheen.halomodellab.domain

import com.slafftrosheen.halomodellab.profiles.ProfileAvailability
import com.slafftrosheen.halomodellab.profiles.TestProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPlanValidationTest {

    @Test
    fun stageBudgetRejectsOverflowAndOutOfRangeStages() {
        assertThrows(IllegalArgumentException::class.java) {
            StageBudgets(loadMs = Long.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StageBudgets(unloadMs = 0L)
        }
    }

    @Test
    fun artifactManifestCanonicalHashIgnoresInputPartOrder() {
        val first = manifest(listOf(part("model.bin", HASH_A), part("tokenizer.json", HASH_B)))
        val reversed = manifest(listOf(part("tokenizer.json", HASH_B), part("model.bin", HASH_A)))

        assertEquals(first.canonicalSha256(), reversed.canonicalSha256())
    }

    @Test
    fun artifactPartRejectsTraversalAndMalformedHashes() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactPart("../outside.bin", HASH_A, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactPart("weights/model.bin", "not-a-sha256", 1L)
        }
    }

    @Test
    fun unknownRoleAndBackendDoNotFallBackToChatOrCpu() {
        assertEquals(RoleWireMapping.Unsupported("VAD"), RoleWireMapping.decode("VAD"))
        assertEquals(BackendWireValue.Unsupported("DSP"), BackendWireValue.decode("DSP"))
        assertNotEquals(RoleWireMapping.Supported(ModelRole.CHAT, "CHAT"), RoleWireMapping.decode("VAD"))
    }

    @Test
    fun labReasoningAndActionMapToObservedHaloRoleNames() {
        assertEquals(RoleWireMapping.Supported(ModelRole.THINK, "REASONING"), RoleWireMapping.encode(ModelRole.THINK))
        assertEquals(RoleWireMapping.Supported(ModelRole.ACTION, "AGENTIC"), RoleWireMapping.encode(ModelRole.ACTION))
        assertEquals(RoleWireMapping.Unsupported("STT"), RoleWireMapping.encode(ModelRole.STT))
    }

    @Test
    fun scopeWithUnknownEffectiveBackendCannotReuseExactEvidence() {
        val unknown = scope(effectiveBackend = null)
        val unspecified = scope(effectiveBackend = RealBackend.UNSPECIFIED)
        val known = scope(effectiveBackend = RealBackend.CPU)

        assertFalse(unknown.mayReuseExactDeviceEvidence())
        assertFalse(unspecified.mayReuseExactDeviceEvidence())
        assertFalse(scope(effectiveBackend = RealBackend.AUTO).mayReuseExactDeviceEvidence())
        assertTrue(known.mayReuseExactDeviceEvidence())
    }

    @Test
    fun unknownOrUnidentifiedDirtySourceCannotReuseEvidence() {
        val unknown = scope(effectiveBackend = RealBackend.CPU).copy(
            labSourceId = "unknown",
            sourceIdentitiesVerified = false,
        )
        val falselyVerified = assertThrows(IllegalArgumentException::class.java) {
            scope(effectiveBackend = RealBackend.CPU).copy(
                labSourceId = "unknown",
                sourceIdentitiesVerified = true,
            )
        }

        assertFalse(unknown.mayReuseExactDeviceEvidence())
        assertTrue(falselyVerified.message?.isNotBlank() == true)
    }

    @Test
    fun remoteEventIdentityRequiresPositiveSequenceAndIncludesServerEpoch() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteEventKey("epoch-1", "run-1", 0L)
        }
        assertNotEquals(
            RemoteEventKey("epoch-1", "run-1", 1L),
            RemoteEventKey("epoch-2", "run-1", 1L),
        )
    }

    @Test
    fun realPlanSnapshotKeepsIndependentVersionFieldsAndUnavailableArtifactReason() {
        val plan = RealRunPlan(
            sessionId = "00000000-0000-0000-0000-000000000001",
            profileId = "text-smoke-v1",
            suiteId = "text-smoke",
            suiteVersion = "1",
            fixtureManifestSha256 = null,
            qualificationPolicySha256 = HASH_D,
            stageBudgets = StageBudgets(),
            sessionBudgetMs = 600_000L,
            requireCharging = true,
            capabilitySnapshot = null,
            cases = listOf(
                RealCasePlan(
                    queueItemId = "00000000-0000-0000-0000-000000000002",
                    providerId = "local-provider",
                    modelId = "model-1",
                    artifactGenerationId = "generation-1",
                    artifactManifest = null,
                    artifactUnavailableReason = "BRIDGE_NOT_CONNECTED",
                    role = ModelRole.THINK,
                    requestedBackend = RealBackend.AUTO,
                    requestedConfiguration = listOf(ConfigurationValue("contextTokens", "1024")),
                    suiteId = "text-smoke",
                    suiteVersion = "1",
                    caseId = "fixed-smoke-1",
                    fixtureManifestSha256 = null,
                    repetitionIndex = 0,
                    maximumOutputTokens = 64,
                ),
            ),
        )

        val snapshot = plan.canonicalJson()
        assertTrue(snapshot.contains("\"planSchemaVersion\":1"))
        assertTrue(snapshot.contains("\"exportSchemaVersion\":1"))
        assertTrue(snapshot.contains("\"origin\":\"DEVICE_MEASUREMENT\""))
        assertTrue(snapshot.contains("\"artifactUnavailableReason\":\"BRIDGE_NOT_CONNECTED\""))
        assertTrue(snapshot.contains("\"haloRole\":\"REASONING\""))
        assertFalse(snapshot.contains("\"artifactManifestSha256\":\""))
    }

    @Test
    fun onlyDeclaredVersionedProfilesAreResolvable() {
        assertEquals(
            ProfileAvailability.CONTRACT_ONLY,
            TestProfileRegistry.resolve("text-smoke", "1")?.availability,
        )
        assertEquals(null, TestProfileRegistry.resolve("unknown-suite", "1"))
        assertEquals(null, TestProfileRegistry.resolve("text-smoke", "99"))
    }

    private fun part(name: String, hash: String) = ArtifactPart(name, hash, 1L)

    private fun manifest(parts: List<ArtifactPart>) = ArtifactManifest(
        providerId = "local-provider",
        modelId = "model-1",
        generationId = "generation-1",
        parts = parts,
    )

    private fun scope(effectiveBackend: RealBackend?) = ExecutionScope(
        deviceProfileId = "00000000-0000-0000-0000-000000000041",
        osBuildId = "build-id",
        haloSourceId = "halo-source",
        labSourceId = "lab-source",
        runtimeId = "runtime",
        runtimeVersion = "runtime-version",
        adapterCompatibilityId = "adapter-v1",
        artifactManifestSha256 = HASH_A,
        role = ModelRole.CHAT,
        requestedBackend = RealBackend.CPU,
        effectiveBackend = effectiveBackend,
        effectiveConfigurationSha256 = HASH_B,
        suiteId = "text-smoke",
        suiteVersion = "1",
        fixtureManifestSha256 = HASH_C,
        qualificationPolicyVersion = 1,
        qualificationPolicySha256 = HASH_D,
        measurementMode = "STANDARD",
        language = null,
        voiceId = null,
        sourceIdentitiesVerified = true,
    )

    private companion object {
        const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val HASH_D = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    }
}
