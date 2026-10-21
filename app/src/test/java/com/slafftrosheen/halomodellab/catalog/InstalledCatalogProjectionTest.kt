package com.slafftrosheen.halomodellab.catalog

import com.slafftrosheen.halomodellab.bridge.ModelInventoryEntry
import com.slafftrosheen.halomodellab.bridge.ModelInventoryState
import com.slafftrosheen.halomodellab.bridge.BridgeConnectionStatus
import com.slafftrosheen.halomodellab.bridge.BridgeOperationResult
import com.slafftrosheen.halomodellab.bridge.ModelInventoryPage
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledCatalogProjectionTest {
    @Test
    fun emptyInventoryIsExplicitAndNotAvailableWithNoRows() {
        assertEquals(InstalledCatalogResult.Empty("NO_INSTALLED_MODELS"), InstalledCatalogProjection.aggregate(emptyList()))
    }

    @Test
    fun unverifiedEntryDoesNotBecomeLeaseEligibleFromItsNameOrRole() {
        val projection = InstalledCatalogProjection.map(
            ModelInventoryEntry(
                providerId = "provider-a",
                modelId = "friendly-text-model",
                generationId = "generation-1",
                state = ModelInventoryState.INSTALLED_UNVERIFIED,
                manifest = null,
                unavailableReason = "MANIFEST_NOT_VERIFIED",
                roles = setOf("CHAT"),
                backendCodes = setOf("CPU"),
            ),
        )

        assertEquals(setOf(ModelRole.CHAT), projection.supportedRoles)
        assertFalse(projection.eligibleForArtifactLease)
        assertFalse(projection.mayConfigure(ModelRole.CHAT))
    }

    @Test
    fun unsupportedRoleAndBackendCodesRemainVisible() {
        val projection = InstalledCatalogProjection.map(
            ModelInventoryEntry(
                providerId = "provider-a",
                modelId = "model-a",
                generationId = null,
                state = ModelInventoryState.UNSUPPORTED,
                manifest = null,
                unavailableReason = "CAPABILITY_UNSUPPORTED",
                roles = setOf("CHAT", "FUTURE_ROLE"),
                backendCodes = setOf("FUTURE_ACCELERATOR"),
            ),
        )

        assertEquals(setOf(ModelRole.CHAT), projection.supportedRoles)
        assertEquals(setOf("FUTURE_ROLE"), projection.unsupportedRoleCodes)
        assertEquals(emptySet<RealBackend>(), projection.backendCandidates)
        assertEquals(setOf("FUTURE_ACCELERATOR"), projection.unsupportedBackendCodes)
        assertTrue(projection.manifest == null)
    }

    @Test
    fun repeatedPageTokenDoesNotReturnPartialCatalogAsAvailable() = runTest {
        val repository = HaloInstalledCatalogRepository { _ ->
            BridgeOperationResult.Success(ModelInventoryPage(emptyList(), "repeat"))
        }

        assertEquals(InstalledCatalogResult.Unavailable("INVENTORY_PAGINATION_CYCLE"), repository.load())
    }

    @Test
    fun bridgeFailureIsNotConvertedToAnEmptyInventory() = runTest {
        val repository = HaloInstalledCatalogRepository { _ ->
            BridgeOperationResult.Unavailable(BridgeConnectionStatus.ConsentRequired)
        }

        assertEquals(InstalledCatalogResult.Unavailable("CONSENT_REQUIRED"), repository.load())
    }
}
