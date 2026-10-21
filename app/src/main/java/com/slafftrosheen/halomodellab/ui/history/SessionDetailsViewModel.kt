package com.slafftrosheen.halomodellab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.export.ExportResult
import com.slafftrosheen.halomodellab.export.ReportExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionDetailsUiState {
    data object Loading : SessionDetailsUiState
    data object NotFound : SessionDetailsUiState
    data class Loaded(val detail: SessionDetail) : SessionDetailsUiState
}

/**
 * Real export progress, not a fire-and-forget snackbar: Ready appears only after the ZIP
 * is closed and renamed, and failures keep their code so the UI can explain them.
 */
sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Exporting : ExportUiState
    data class Ready(val filePath: String, val fileName: String) : ExportUiState
    data class Failed(val code: String, val detail: String) : ExportUiState
}

class SessionDetailsViewModel(
    repository: SessionRepository,
    private val exporter: ReportExporter,
    private val sessionId: String,
) : ViewModel() {

    val state: StateFlow<SessionDetailsUiState> = repository.observeSession(sessionId)
        .map { detail ->
            if (detail == null) SessionDetailsUiState.NotFound else SessionDetailsUiState.Loaded(detail)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailsUiState.Loading,
        )

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    fun export() {
        if (_exportState.value is ExportUiState.Exporting) return
        _exportState.value = ExportUiState.Exporting
        viewModelScope.launch {
            _exportState.value = when (val result = exporter.export(sessionId)) {
                is ExportResult.Success -> ExportUiState.Ready(
                    filePath = result.file.absolutePath,
                    fileName = result.file.name,
                )

                is ExportResult.NotExportable -> ExportUiState.Failed(
                    code = "SESSION_NOT_QUIESCENT",
                    detail = result.state.name,
                )

                is ExportResult.MissingSession -> ExportUiState.Failed(
                    code = "SESSION_MISSING",
                    detail = result.sessionId,
                )

                is ExportResult.Failure -> ExportUiState.Failed(
                    code = result.code,
                    detail = result.detail,
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: SessionRepository,
            exporter: ReportExporter,
            sessionId: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SessionDetailsViewModel(repository, exporter, sessionId) }
            }
    }
}
