package com.slafftrosheen.halomodellab.catalog

import com.slafftrosheen.halomodellab.bridge.BridgeConnectionStatus
import com.slafftrosheen.halomodellab.bridge.BridgeOperationResult
import com.slafftrosheen.halomodellab.bridge.HaloLabGateway
import com.slafftrosheen.halomodellab.bridge.ModelInventoryEntry
import com.slafftrosheen.halomodellab.bridge.ModelInventoryPage
import com.slafftrosheen.halomodellab.bridge.ModelInventoryState
import com.slafftrosheen.halomodellab.domain.ArtifactManifest
import com.slafftrosheen.halomodellab.domain.BackendWireValue
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RoleWireMapping
import kotlinx.coroutines.CancellationException

/** Inventory metadata only; it never owns model bytes or claims runtime capability by name. */
data class InstalledModelDescriptor(
    val providerId: String,
    val modelId: String,
    val generationId: String?,
    val state: ModelInventoryState,
    val manifest: ArtifactManifest?,
    val unavailableReason: String?,
    val supportedRoles: Set<ModelRole>,
    val unsupportedRoleCodes: Set<String>,
    val backendCandidates: Set<RealBackend>,
    val unsupportedBackendCodes: Set<String>,
) {
    val eligibleForArtifactLease: Boolean
        get() = state == ModelInventoryState.INSTALLED_VERIFIED && generationId != null && manifest != null

    fun mayConfigure(role: ModelRole): Boolean =
        eligibleForArtifactLease && role in supportedRoles
}

sealed interface InstalledCatalogResult {
    data class Available(val models: List<InstalledModelDescriptor>) : InstalledCatalogResult {
        init { require(models.isNotEmpty()) }
    }

    data class Empty(val reasonCode: String) : InstalledCatalogResult
    data class Unavailable(val reasonCode: String) : InstalledCatalogResult
}

object InstalledCatalogProjection {
    fun map(entry: ModelInventoryEntry): InstalledModelDescriptor {
        val decoded = entry.roles.map(RoleWireMapping::decode)
        val roles = decoded.mapNotNull { (it as? RoleWireMapping.Supported)?.labRole }.toSet()
        val unsupported = decoded.mapNotNull { (it as? RoleWireMapping.Unsupported)?.wireValue }.toSet()
        val (backends, unsupportedBackends) = decodeBackendCandidates(entry.backendCodes)
        return InstalledModelDescriptor(
            providerId = entry.providerId,
            modelId = entry.modelId,
            generationId = entry.generationId,
            state = entry.state,
            manifest = entry.manifest,
            unavailableReason = entry.unavailableReason,
            supportedRoles = roles,
            unsupportedRoleCodes = unsupported,
            backendCandidates = backends,
            unsupportedBackendCodes = unsupportedBackends,
        )
    }

    fun aggregate(pages: List<List<ModelInventoryEntry>>, pageLimit: Int = MAX_PAGES): InstalledCatalogResult {
        if (pages.size > pageLimit) return InstalledCatalogResult.Unavailable("INVENTORY_PAGE_LIMIT")
        val entries = pages.flatten()
        if (entries.size > MAX_MODELS) return InstalledCatalogResult.Unavailable("INVENTORY_MODEL_LIMIT")
        if (entries.isEmpty()) return InstalledCatalogResult.Empty("NO_INSTALLED_MODELS")
        val identities = entries.map { Triple(it.providerId, it.modelId, it.generationId) }
        if (identities.distinct().size != identities.size) {
            return InstalledCatalogResult.Unavailable("DUPLICATE_INVENTORY_IDENTITY")
        }
        return InstalledCatalogResult.Available(entries.map(::map))
    }

    const val MAX_PAGES = 20
    const val MAX_MODELS = 1_000
}

/** Lab-side paging adapter. It never converts a failed or partial read into an empty catalog. */
class HaloInstalledCatalogRepository(
    private val listModels: suspend (String?) -> BridgeOperationResult<ModelInventoryPage>,
) {
    constructor(gateway: HaloLabGateway) : this(gateway::listModels)

    suspend fun load(): InstalledCatalogResult {
        val pages = mutableListOf<List<ModelInventoryEntry>>()
        val seenTokens = mutableSetOf<String>()
        var nextToken: String? = null

        repeat(InstalledCatalogProjection.MAX_PAGES) {
            val result = try {
                listModels(nextToken)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return InstalledCatalogResult.Unavailable("INVENTORY_REQUEST_FAILED")
            }
            val page = when (result) {
                is BridgeOperationResult.Success -> result.value
                is BridgeOperationResult.Accepted -> return InstalledCatalogResult.Unavailable("INVENTORY_UNEXPECTED_ACCEPTED")
                is BridgeOperationResult.Pending -> return InstalledCatalogResult.Unavailable(result.stableReasonCode)
                is BridgeOperationResult.Rejected -> return InstalledCatalogResult.Unavailable(result.stableReasonCode)
                is BridgeOperationResult.Unavailable -> return InstalledCatalogResult.Unavailable(result.status.code())
            }
            pages += page.entries
            if (pages.sumOf { it.size } > InstalledCatalogProjection.MAX_MODELS) {
                return InstalledCatalogResult.Unavailable("INVENTORY_MODEL_LIMIT")
            }
            val token = page.nextPageToken ?: return InstalledCatalogProjection.aggregate(pages)
            if (!seenTokens.add(token)) return InstalledCatalogResult.Unavailable("INVENTORY_PAGINATION_CYCLE")
            nextToken = token
        }
        return if (nextToken == null) InstalledCatalogProjection.aggregate(pages)
        else InstalledCatalogResult.Unavailable("INVENTORY_PAGE_LIMIT")
    }

    private fun BridgeConnectionStatus.code(): String = when (this) {
        BridgeConnectionStatus.HaloNotInstalled -> "HALO_NOT_INSTALLED"
        BridgeConnectionStatus.BridgeNotSupported -> "BRIDGE_NOT_SUPPORTED"
        BridgeConnectionStatus.SignatureMismatch -> "SIGNATURE_MISMATCH"
        BridgeConnectionStatus.ConsentRequired -> "CONSENT_REQUIRED"
        BridgeConnectionStatus.ProtocolMismatch -> "PROTOCOL_MISMATCH"
        BridgeConnectionStatus.Busy -> "BRIDGE_BUSY"
        BridgeConnectionStatus.Reconnecting -> "BRIDGE_RECONNECTING"
        BridgeConnectionStatus.Ready -> "INVENTORY_UNAVAILABLE"
        BridgeConnectionStatus.UnknownConnectionFailure -> "UNKNOWN_CONNECTION_FAILURE"
        is BridgeConnectionStatus.Unsupported -> "UNSUPPORTED_CONNECTION_STATUS"
    }
}

/** Keeps unknown backend values visible to callers instead of coercing them to AUTO or CPU. */
fun decodeBackendCandidates(values: Set<String>): Pair<Set<RealBackend>, Set<String>> {
    val decoded = values.map(BackendWireValue::decode)
    return decoded.mapNotNull { (it as? BackendWireValue.Supported)?.value }.toSet() to
        decoded.mapNotNull { (it as? BackendWireValue.Unsupported)?.wireValue }.toSet()
}
