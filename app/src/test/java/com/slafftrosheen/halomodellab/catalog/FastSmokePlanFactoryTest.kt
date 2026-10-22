package com.slafftrosheen.halomodellab.catalog

import com.slafftrosheen.halomodellab.bridge.ModelInventoryState
import com.slafftrosheen.halomodellab.domain.ArtifactManifest
import com.slafftrosheen.halomodellab.domain.ArtifactPart
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RuntimeCapabilities
import com.slafftrosheen.halomodellab.domain.RuntimeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSmokePlanFactoryTest {
    @Test
    fun buildsOnlyTheFixedVerifiedFastCpuCaseAndKeepsUnknownsUnknown() {
        val model = model(ModelInventoryState.INSTALLED_VERIFIED)

        val result = FastSmokePlanFactory.create(
            model = model,
            capabilities = capabilities(),
            deviceProfileId = DEVICE_ID,
            osBuildId = "build-fingerprint",
        )

        assertTrue(result is FastSmokePlanResult.Ready)
        val plan = (result as FastSmokePlanResult.Ready).plan
        assertEquals(1, plan.cases.size)
        assertEquals("fast-smoke", plan.suiteId)
        assertEquals("short-ready", plan.cases.single().caseId)
        assertEquals(RealBackend.CPU, plan.cases.single().requestedBackend)
        assertEquals(ModelRole.FAST, plan.cases.single().role)
        assertEquals(null, plan.cases.single().executionScope?.effectiveBackend)
        assertFalse(plan.cases.single().executionScope!!.sourceIdentitiesVerified)
        assertTrue(plan.canonicalJson().contains("\"executionScope\":{\"schemaVersion\":1"))
    }

    @Test
    fun refusesUnverifiedInventoryAndMissingCpuRuntimeCapability() {
        assertEquals(
            FastSmokePlanResult.Unavailable("VERIFIED_LOCAL_ARTIFACT_REQUIRED"),
            FastSmokePlanFactory.create(
                model = model(ModelInventoryState.INSTALLED_UNVERIFIED),
                capabilities = capabilities(),
                deviceProfileId = DEVICE_ID,
                osBuildId = "build",
            ),
        )
        val noCpu = capabilities().copy(
            capabilities = listOf(
                RuntimeCapability(ModelRole.FAST, setOf(RealBackend.GPU), false, false, 128, 8),
            ),
        ).let { it.copy(snapshotSha256 = it.canonicalSha256()) }
        assertEquals(
            FastSmokePlanResult.Unavailable("RUNTIME_FAST_CPU_LIMIT_UNSUPPORTED"),
            FastSmokePlanFactory.create(
                model = model(ModelInventoryState.INSTALLED_VERIFIED),
                capabilities = noCpu,
                deviceProfileId = DEVICE_ID,
                osBuildId = "build",
            ),
        )
    }

    private fun model(state: ModelInventoryState) = InstalledModelDescriptor(
        providerId = "provider.litert.local",
        modelId = "verified-model",
        generationId = "generation-1",
        state = state,
        manifest = manifest(),
        unavailableReason = null,
        supportedRoles = setOf(ModelRole.FAST),
        unsupportedRoleCodes = emptySet(),
        backendCandidates = setOf(RealBackend.CPU),
        unsupportedBackendCodes = emptySet(),
    )

    private fun capabilities(): RuntimeCapabilities {
        val base = RuntimeCapabilities(
            capturedAtWallTimeMs = 1L,
            haloSourceId = "halo-source-1",
            runtimeId = "litert",
            runtimeVersion = "1.0",
            adapterCompatibilityId = "adapter-1",
            capabilities = listOf(
                RuntimeCapability(ModelRole.FAST, setOf(RealBackend.CPU), false, false, 256, 8),
            ),
            snapshotSha256 = "0".repeat(64),
        )
        return base.copy(snapshotSha256 = base.canonicalSha256())
    }

    private fun manifest() = ArtifactManifest(
        providerId = "provider.litert.local",
        modelId = "verified-model",
        generationId = "generation-1",
        parts = listOf(ArtifactPart("model.litertlm", "a".repeat(64), 1024L)),
    )

    private companion object {
        const val DEVICE_ID = "00000000-0000-0000-0000-000000000111"
    }
}
