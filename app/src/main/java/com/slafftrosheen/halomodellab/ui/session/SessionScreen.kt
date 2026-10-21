package com.slafftrosheen.halomodellab.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.slafftrosheen.halomodellab.StartupState
import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.catalog.DemoScenario
import com.slafftrosheen.halomodellab.domain.PlanLimits
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.ui.demoScenarioTitleRes
import com.slafftrosheen.halomodellab.ui.formatWallTime
import com.slafftrosheen.halomodellab.ui.originLabelRes
import com.slafftrosheen.halomodellab.ui.sessionStateLabelRes

@Composable
fun SessionRoute(
    container: AppContainer,
    onOpenCatalog: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(
            catalogRepository = container.catalogRepository,
            sessionRepository = container.sessionRepository,
            startupState = container.startupState,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    SessionScreen(
        state = state,
        onRepeatsChange = viewModel::setRepeats,
        onTimeoutChange = viewModel::setTimeoutSeconds,
        onBudgetChange = viewModel::setBudgetMinutes,
        onRequireChargingChange = viewModel::setRequireCharging,
        onCreateSession = viewModel::createSession,
        onCancelOpenSession = viewModel::cancelOpenSession,
        onClearSelection = viewModel::clearSelection,
        onOpenCatalog = onOpenCatalog,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onRepeatsChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onBudgetChange: (Int) -> Unit,
    onRequireChargingChange: (Boolean) -> Unit,
    onCreateSession: () -> Unit,
    onCancelOpenSession: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenCatalog: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.session_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { message ->
                MessageCard(message = message, onDismiss = onDismissMessage)
            }

            when (val startup = state.startup) {
                StartupState.Recovering -> InfoCard(
                    title = stringResource(R.string.session_startup_recovering_title),
                    body = stringResource(R.string.session_startup_recovering_body),
                )

                is StartupState.Failed -> ErrorCard(
                    title = stringResource(R.string.session_startup_failed_title),
                    body = stringResource(R.string.session_startup_failed_body, startup.detail),
                )

                is StartupState.Ready -> {
                    val openSession = state.openSession
                    if (openSession != null) {
                        OpenSessionCard(
                            detail = openSession,
                            canCancel = state.canCancelOpenSession,
                            onCancel = onCancelOpenSession,
                        )
                    } else {
                        PlanBuilder(
                            state = state,
                            onRepeatsChange = onRepeatsChange,
                            onTimeoutChange = onTimeoutChange,
                            onBudgetChange = onBudgetChange,
                            onRequireChargingChange = onRequireChargingChange,
                            onCreateSession = onCreateSession,
                            onClearSelection = onClearSelection,
                            onOpenCatalog = onOpenCatalog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanBuilder(
    state: SessionUiState,
    onRepeatsChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onBudgetChange: (Int) -> Unit,
    onRequireChargingChange: (Boolean) -> Unit,
    onCreateSession: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    SimulationBanner()

    if (state.selectedScenarios.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.session_builder_selection_empty),
            body = stringResource(R.string.session_builder_selection_hint),
        )
        Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_open_catalog))
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_builder_selection_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.selectedScenarios.forEachIndexed { index, scenario ->
                    SelectedScenarioRow(index = index + 1, scenario = scenario)
                }
                TextButton(onClick = onClearSelection) {
                    Text(stringResource(R.string.session_clear_selection))
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.session_builder_params_title),
                style = MaterialTheme.typography.titleMedium,
            )
            StepperRow(
                label = stringResource(R.string.session_param_repeats),
                value = state.repeats,
                range = PlanLimits.REPEATS,
                onChange = onRepeatsChange,
                enabled = state.selectedScenarios.isNotEmpty(),
            )
            StepperRow(
                label = stringResource(R.string.session_param_timeout),
                value = state.timeoutSeconds,
                range = 1..120,
                onChange = onTimeoutChange,
                enabled = state.selectedScenarios.isNotEmpty(),
            )
            StepperRow(
                label = stringResource(R.string.session_param_budget),
                value = state.budgetMinutes,
                range = 1..60,
                onChange = onBudgetChange,
                enabled = state.selectedScenarios.isNotEmpty(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_param_charging),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.requireCharging,
                    onCheckedChange = onRequireChargingChange,
                )
            }
            Text(
                text = stringResource(R.string.session_param_charging_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text(
        text = stringResource(R.string.session_plan_items, state.plannedItemCount),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.session_profile, DemoCatalog.PROFILE_ID),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = onCreateSession,
        enabled = state.canCreate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.session_create))
    }
    Text(
        text = stringResource(R.string.session_create_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SelectedScenarioRow(index: Int, scenario: DemoScenario) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(
                R.string.session_item_title,
                index,
                stringResource(demoScenarioTitleRes(scenario.id)),
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = scenario.id,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.catalog_card_sim_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OpenSessionCard(
    detail: SessionDetail,
    canCancel: Boolean,
    onCancel: () -> Unit,
) {
    SimulationBanner()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.session_open_exists_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.session_state_label,
                    stringResource(sessionStateLabelRes(detail.summary.state)),
                ),
                style = MaterialTheme.typography.bodyMedium,
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
                    R.string.session_origin,
                    stringResource(originLabelRes(detail.summary.origin)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.session_created_at,
                    formatWallTime(detail.summary.createdAtWallTimeMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.session_open_exists_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.session_start_disabled))
    }
    Text(
        text = stringResource(R.string.session_start_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
        onClick = onCancel,
        enabled = canCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.session_cancel))
    }
    if (!canCancel) {
        Text(
            text = stringResource(R.string.session_cancel_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.session_param_range, range.first, range.last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onChange(value - 1) },
            enabled = enabled && value > range.first,
        ) {
            Text("−")
        }
        Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
        TextButton(
            onClick = { onChange(value + 1) },
            enabled = enabled && value < range.last,
        ) {
            Text("+")
        }
    }
}

@Composable
private fun MessageCard(message: SessionMessage, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when (message) {
                    SessionMessage.Created -> stringResource(R.string.session_message_created)
                    is SessionMessage.Blocked -> stringResource(
                        R.string.session_message_blocked,
                        stringResource(sessionStateLabelRes(message.state)),
                    )

                    SessionMessage.NoScenarios -> stringResource(R.string.session_message_no_scenarios)
                    is SessionMessage.StorageFailure -> stringResource(
                        R.string.session_message_storage,
                        message.detail,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_dismiss))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
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
