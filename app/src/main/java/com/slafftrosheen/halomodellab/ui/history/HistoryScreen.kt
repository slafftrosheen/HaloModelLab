package com.slafftrosheen.halomodellab.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slafftrosheen.halomodellab.AppContainer
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.HistoryFilter
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.ui.formatWallTime
import com.slafftrosheen.halomodellab.ui.originLabelRes
import com.slafftrosheen.halomodellab.ui.sessionStateLabelRes
import com.slafftrosheen.halomodellab.data.QualificationEntity

@Composable
fun HistoryRoute(
    container: AppContainer,
    onOpenSession: (String) -> Unit,
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(container.sessionRepository, container.qualificationRepository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    HistoryScreen(
        state = state,
        onFilterChange = viewModel::setFilter,
        onShowExcludedChange = viewModel::setShowExcluded,
        onOpenSession = onOpenSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onFilterChange: (HistoryFilter) -> Unit,
    onShowExcludedChange: (Boolean) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == HistoryFilter.ALL,
                    onClick = { onFilterChange(HistoryFilter.ALL) },
                    label = { Text(stringResource(R.string.history_filter_all)) },
                )
                FilterChip(
                    selected = state.filter == HistoryFilter.COMPLETED,
                    onClick = { onFilterChange(HistoryFilter.COMPLETED) },
                    label = { Text(stringResource(R.string.history_filter_completed)) },
                )
                FilterChip(
                    selected = state.filter == HistoryFilter.INTERRUPTED,
                    onClick = { onFilterChange(HistoryFilter.INTERRUPTED) },
                    label = { Text(stringResource(R.string.history_filter_interrupted)) },
                )
                FilterChip(
                    selected = state.filter == HistoryFilter.SYNTHETIC,
                    onClick = { onFilterChange(HistoryFilter.SYNTHETIC) },
                    label = { Text(stringResource(R.string.history_filter_synthetic)) },
                )
                FilterChip(
                    selected = state.showExcluded,
                    onClick = { onShowExcludedChange(!state.showExcluded) },
                    label = { Text("Show excluded") },
                )
            }

            if (state.showExcluded) {
                if (state.excludedScopes.isEmpty()) {
                    Text("No exact configuration scopes are currently excluded.", Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.excludedScopes.forEach { exclusion ->
                        ExcludedScopeItem(exclusion) { onOpenSession(exclusion.sessionId) }
                    }
                }
            }

            if (state.sessions.isEmpty()) {
                EmptyHistory(filtered = state.totalCount > 0)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.sessionId }) { summary ->
                        HistoryItem(summary = summary, onClick = { onOpenSession(summary.sessionId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedScopeItem(exclusion: QualificationEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Excluded exact configuration", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text("Scope ${exclusion.scopeDigestSha256.take(16)}… · policy ${exclusion.policyDigestSha256.take(12)}…",
                style = MaterialTheme.typography.bodySmall)
            Text("Evidence is retained. Open the source session to review or plan a deliberate retest.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyHistory(filtered: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (filtered) {
                    stringResource(R.string.history_empty_filtered)
                } else {
                    stringResource(R.string.history_empty_all_title)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!filtered) {
                Text(
                    text = stringResource(R.string.history_empty_all_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(summary: SessionSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(sessionStateLabelRes(summary.state)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.history_item_progress,
                        summary.completedItemCount,
                        summary.itemCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.history_origin_line,
                    stringResource(originLabelRes(summary.origin)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.history_created_line,
                    formatWallTime(summary.createdAtWallTimeMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.history_engine_line, summary.engineId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.origin == EvidenceOrigin.SYNTHETIC) {
                Text(
                    text = stringResource(R.string.catalog_card_sim_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
