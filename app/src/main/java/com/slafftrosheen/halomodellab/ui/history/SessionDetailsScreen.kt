package com.slafftrosheen.halomodellab.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slafftrosheen.halomodellab.AppContainer
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.EventView
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.export.ExportShare
import com.slafftrosheen.halomodellab.export.ReportExporter
import com.slafftrosheen.halomodellab.ui.attemptOutcomeLabelRes
import com.slafftrosheen.halomodellab.ui.demoScenarioTitleRes
import com.slafftrosheen.halomodellab.ui.formatWallTime
import com.slafftrosheen.halomodellab.ui.originLabelRes
import com.slafftrosheen.halomodellab.ui.queueItemStateLabelRes
import com.slafftrosheen.halomodellab.ui.sessionStateLabelRes
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SessionDetailsRoute(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
) {
    val viewModel: SessionDetailsViewModel = viewModel(
        factory = SessionDetailsViewModel.factory(
            repository = container.sessionRepository,
            exporter = container.reportExporter,
            sessionId = sessionId,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportShare.MIME_TYPE),
    ) { target ->
        // Cancelling the picker is not an export or test failure: nothing is written.
        val ready = exportState as? ExportUiState.Ready ?: return@rememberLauncherForActivityResult
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = ExportShare.copyToDocument(context, File(ready.filePath), target)
            Toast.makeText(
                context,
                if (copied) R.string.export_save_done else R.string.export_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    SessionDetailsScreen(
        state = state,
        exportState = exportState,
        onBack = onBack,
        onExport = viewModel::export,
        onShare = {
            val ready = exportState as? ExportUiState.Ready
            if (ready != null) {
                val shared = runCatching {
                    ExportShare.share(context, File(ready.filePath))
                }.isSuccess
                if (!shared) {
                    Toast.makeText(context, R.string.export_share_failed, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSave = {
            val ready = exportState as? ExportUiState.Ready
            if (ready != null) saveLauncher.launch(ready.fileName)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    state: SessionDetailsUiState,
    exportState: ExportUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.details_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (state) {
            SessionDetailsUiState.Loading -> Text(
                text = stringResource(R.string.details_loading),
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )

            SessionDetailsUiState.NotFound -> Text(
                text = stringResource(R.string.details_not_found),
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )

            is SessionDetailsUiState.Loaded -> DetailsContent(
                detail = state.detail,
                exportState = exportState,
                contentPadding = innerPadding,
                onExport = onExport,
                onShare = onShare,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun DetailsContent(
    detail: SessionDetail,
    exportState: ExportUiState,
    contentPadding: PaddingValues,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (detail.summary.origin == EvidenceOrigin.SYNTHETIC) {
            item { SimulationBanner() }
        }

        item { SummaryCard(detail) }
        item { PlanCard(detail) }
        item {
            ExportCard(
                detail = detail,
                exportState = exportState,
                onExport = onExport,
                onShare = onShare,
                onSave = onSave,
            )
        }

        item {
            Text(
                text = stringResource(R.string.details_queue_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(detail.queue, key = { it.queueItemId }) { item ->
            QueueItemCard(item)
        }

        item {
            Text(
                text = stringResource(R.string.details_events_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (detail.events.size > MAX_VISIBLE_EVENTS) {
                    stringResource(
                        R.string.details_events_more,
                        MAX_VISIBLE_EVENTS,
                        detail.events.size,
                    )
                } else {
                    stringResource(R.string.details_events_count, detail.events.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.events.isEmpty()) {
            item { Text(stringResource(R.string.details_events_empty)) }
        } else {
            items(detail.events.take(MAX_VISIBLE_EVENTS), key = { it.seq }) { event ->
                EventRow(event)
            }
        }

        item {
            Text(
                text = stringResource(R.string.details_telemetry_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (detail.telemetry.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.details_telemetry_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(detail.telemetry.size) { index ->
                val sample = detail.telemetry[index]
                Text(
                    text = "${sample.name}: ${sample.value ?: sample.unavailableReason.orEmpty()} ${sample.unit} [${sample.scope}]",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(detail: SessionDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(sessionStateLabelRes(detail.summary.state)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.session_items_progress,
                    detail.summary.completedItemCount,
                    detail.summary.itemCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.history_origin_line,
                    stringResource(originLabelRes(detail.summary.origin)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.history_engine_line, detail.summary.engineId),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.history_created_line,
                    formatWallTime(detail.summary.createdAtWallTimeMs),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            detail.summary.finishedWallTimeMs?.let { finishedAt ->
                Text(
                    text = stringResource(
                        R.string.details_finished_line,
                        formatWallTime(finishedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.details_active_time, detail.persistedActiveMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.sessionFailure?.let { failure ->
                FailureBlock(failure = failure, label = stringResource(R.string.details_session_failure))
            }
        }
    }
}

@Composable
private fun PlanCard(detail: SessionDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.details_plan_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.session_profile, detail.summary.profileId),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.details_repeats, detail.repeatCount),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.details_timeout, (detail.caseTimeoutMs / 1_000L).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.details_budget, (detail.budgetMs / 60_000L).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.details_charging,
                    if (detail.requireCharging) {
                        stringResource(R.string.common_yes)
                    } else {
                        stringResource(R.string.common_no)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ExportCard(
    detail: SessionDetail,
    exportState: ExportUiState,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val quiescent = ReportExporter.isExportable(detail.summary.state)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.export_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.export_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!quiescent) {
                Text(
                    text = stringResource(R.string.export_running_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onExport,
                enabled = quiescent && exportState !is ExportUiState.Exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_button))
            }
            when (exportState) {
                ExportUiState.Idle -> Unit
                ExportUiState.Exporting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.export_in_progress),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is ExportUiState.Ready -> {
                    Text(
                        text = stringResource(R.string.export_ready, exportState.fileName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onShare,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.export_share))
                        }
                        OutlinedButton(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.export_save))
                        }
                    }
                }

                is ExportUiState.Failed -> Text(
                    text = stringResource(
                        R.string.export_error,
                        exportState.code,
                        exportState.detail,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun QueueItemCard(item: QueueItemView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.details_queue_item,
                    item.ordinal + 1,
                    item.scenarioId?.let { stringResource(demoScenarioTitleRes(it)) }
                        ?: item.subjectId,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.details_queue_state,
                    stringResource(queueItemStateLabelRes(item.state)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = item.scenarioId ?: stringResource(R.string.scenario_unknown_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.details_repeat_index, item.repeatIndex + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.attempts.forEach { attempt ->
                AttemptBlock(attempt)
            }
        }
    }
}

@Composable
private fun AttemptBlock(attempt: AttemptView) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(
                R.string.details_attempt_title,
                attempt.number,
                stringResource(attemptOutcomeLabelRes(attempt.outcome)),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.details_attempt_stage, attempt.stage.name),
            style = MaterialTheme.typography.labelSmall,
        )
        attempt.validity?.let { validity ->
            Text(
                text = stringResource(R.string.details_attempt_validity, validity.name),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        attempt.deadlineReason?.let { reason ->
            Text(
                text = stringResource(R.string.details_attempt_deadline, reason.name),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = stringResource(
                R.string.details_attempt_elapsed,
                attempt.elapsedMs ?: stringResource(R.string.common_unknown_short),
            ),
            style = MaterialTheme.typography.labelSmall,
        )
        attempt.failure?.let { failure ->
            FailureBlock(
                failure = failure,
                label = stringResource(R.string.details_attempt_failure),
            )
        }
    }
}

@Composable
private fun FailureBlock(failure: FailureInfo, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label: ${failure.code}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "${failure.domain.name} / ${failure.confidence.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = failure.detail,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EventRow(event: EventView) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = event.seq.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = event.code, style = MaterialTheme.typography.bodySmall)
            Text(
                text = listOfNotNull(event.stage?.name, event.attemptId?.take(8)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SimulationBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.catalog_sim_banner),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private const val MAX_VISIBLE_EVENTS = 100
