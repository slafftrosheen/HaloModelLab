package com.slafftrosheen.halomodellab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.SessionDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionDetailsUiState {
    data object Loading : SessionDetailsUiState
    data object NotFound : SessionDetailsUiState
    data class Loaded(val detail: SessionDetail) : SessionDetailsUiState
}

class SessionDetailsViewModel(
    repository: SessionRepository,
    sessionId: String,
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

    companion object {
        fun factory(repository: SessionRepository, sessionId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SessionDetailsViewModel(repository, sessionId) }
            }
    }
}
