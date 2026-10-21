package com.slafftrosheen.halomodellab.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.catalog.DemoScenario
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Projects the selected demo scenarios for the session screen.
 *
 * The selection is in-memory in HML-L0; no plan is created, saved or started.
 */
class SessionViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {

    val selectedScenarios: StateFlow<List<DemoScenario>> = repository.state
        .map { state -> state.selectedScenarioIds.mapNotNull(DemoCatalog::scenarioById) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun clearSelection() {
        repository.clearSelection()
    }

    companion object {
        fun factory(repository: CatalogRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(repository) }
        }
    }
}
