package com.slafftrosheen.halomodellab.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Button
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
    onOpenCatalog: () -> Unit,
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
        onOpenCatalog = onOpenCatalog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onFilterChange: (HistoryFilter) -> Unit,
    onShowExcludedChange: (Boolean) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenCatalog: () -> Unit,
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
                    label = { Text(stringResource(R.string.history_show_excluded)) },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.showExcluded) {
                    item(key = "excluded-header") {
                        Text(stringResource(R.string.history_excluded_title), style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.excludedScopes.isEmpty()) {
                        item(key = "excluded-empty") {
                            Text(
                                stringResource(R.string.history_excluded_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = state.excludedScopes,
                            key = { "${it.scopeDigestSha256}:${it.policyDigestSha256}" },
                        ) { exclusion ->
                            ExcludedScopeItem(
                                exclusion = exclusion,
                                onReviewEvidence = { onOpenSession(exclusion.sessionId) },
                                onRetest = onOpenCatalog,
                            )
                        }
                    }
                }
                if (state.sessions.isEmpty()) {
                    item(key = "history-empty") { EmptyHistory(filtered = state.totalCount > 0) }
                } else {
                    items(state.sessions, key = { it.sessionId }) { summary ->
                        HistoryItem(summary = summary, onClick = { onOpenSession(summary.sessionId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedScopeItem(
    exclusion: QualificationEntity,
    onReviewEvidence: () -> Unit,
    onRetest: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_excluded_scope_title), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text(stringResource(
                R.string.history_excluded_scope_identity,
                exclusion.scopeDigestSha256.take(16),
                exclusion.policyDigestSha256.take(12),
            ),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.history_excluded_scope_body),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReviewEvidence, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.history_excluded_review))
                }
                Button(onClick = onRetest, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.history_excluded_retest))
                }
            }
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
