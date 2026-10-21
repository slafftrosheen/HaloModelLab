package com.slafftrosheen.halomodellab.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.catalog.CatalogMode
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.CatalogState
import kotlinx.coroutines.flow.StateFlow

/**
 * Only forwards commands and exposes state. It cannot fabricate catalog entries.
 */
class CatalogViewModel(
    private val repository: CatalogRepository,
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

    companion object {
        fun factory(repository: CatalogRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(repository) }
        }
    }
}
