package com.slafftrosheen.halomodellab.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.catalog.CatalogMode
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.CatalogState
import com.slafftrosheen.halomodellab.catalog.HaloInstalledCatalogRepository
import com.slafftrosheen.halomodellab.catalog.InstalledCatalogResult
import com.slafftrosheen.halomodellab.catalog.RealCatalogStatus
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Only forwards commands and exposes state. It cannot fabricate catalog entries.
 */
class CatalogViewModel(
    private val repository: CatalogRepository,
    private val installedCatalog: HaloInstalledCatalogRepository,
) : ViewModel() {

    val state: StateFlow<CatalogState> = repository.state

    fun setMode(mode: CatalogMode) {
        repository.setMode(mode)
    }

    fun setQuery(query: String) {
        repository.setQuery(query)
    }

    fun toggleScenario(scenarioId: String) {
        repository.toggleScenario(scenarioId)
    }

    fun clearSelection() {
        repository.clearSelection()
    }

    fun refreshRealInventory() {
        repository.setRealCatalogStatus(RealCatalogStatus.LOADING)
        viewModelScope.launch {
            when (val result = installedCatalog.load()) {
                is InstalledCatalogResult.Available -> repository.setRealCatalogStatus(RealCatalogStatus.READY, result.models)
                is InstalledCatalogResult.Empty -> repository.setRealCatalogStatus(RealCatalogStatus.EMPTY)
                is InstalledCatalogResult.Unavailable -> repository.setRealCatalogStatus(
                    result.reasonCode.toCatalogStatus(),
                    reason = result.reasonCode,
                )
            }
        }
    }

    companion object {
        fun factory(repository: CatalogRepository, installedCatalog: HaloInstalledCatalogRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(repository, installedCatalog) }
        }
    }
}

private fun String.toCatalogStatus(): RealCatalogStatus = when (this) {
    "HALO_NOT_INSTALLED" -> RealCatalogStatus.HALO_NOT_INSTALLED
    "BRIDGE_NOT_SUPPORTED" -> RealCatalogStatus.BRIDGE_NOT_SUPPORTED
    "SIGNATURE_MISMATCH" -> RealCatalogStatus.SIGNATURE_MISMATCH
    "CONSENT_REQUIRED" -> RealCatalogStatus.CONSENT_REQUIRED
    "PROTOCOL_MISMATCH" -> RealCatalogStatus.PROTOCOL_MISMATCH
    "BRIDGE_BUSY" -> RealCatalogStatus.BRIDGE_BUSY
    "BRIDGE_RECONNECTING" -> RealCatalogStatus.BRIDGE_RECONNECTING
    else -> RealCatalogStatus.UNAVAILABLE
}
