package com.slafftrosheen.halomodellab.ui.catalog

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.slafftrosheen.halomodellab.catalog.CatalogMode
import com.slafftrosheen.halomodellab.catalog.CatalogState
import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.catalog.DemoScenario
import com.slafftrosheen.halomodellab.ui.demoScenarioDescriptionRes
import com.slafftrosheen.halomodellab.ui.demoScenarioTitleRes

@Composable
fun CatalogRoute(
    container: AppContainer,
    onOpenSession: () -> Unit,
) {
    val viewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModel.factory(
            container.catalogRepository,
            container.installedCatalogRepository,
            container.sessionRepository,
            container.haloGateway,
            container.processInstanceId,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val realPlanCreation by viewModel.realPlanCreation.collectAsStateWithLifecycle()

    CatalogScreen(
        state = state,
        onModeChange = viewModel::setMode,
        onQueryChange = viewModel::setQuery,
        onToggleScenario = viewModel::toggleScenario,
        onClearSelection = viewModel::clearSelection,
        onRefreshRealInventory = viewModel::refreshRealInventory,
        realPlanCreation = realPlanCreation,
        onCreateFastSmokePlan = { model -> viewModel.createFastSmokePlan(model, onOpenSession) },
        onOpenSession = onOpenSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogState,
    onModeChange: (CatalogMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleScenario: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRefreshRealInventory: () -> Unit,
    realPlanCreation: RealPlanCreationState,
    onCreateFastSmokePlan: (com.slafftrosheen.halomodellab.catalog.InstalledModelDescriptor) -> Unit,
    onOpenSession: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.catalog_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSwitch(
                demoEnabled = state.mode == CatalogMode.DEMO,
                onModeChange = onModeChange,
            )

            if (state.mode == CatalogMode.DEMO) {
                DemoSection(
                    state = state,
                    onQueryChange = onQueryChange,
                    onToggleScenario = onToggleScenario,
                    onClearSelection = onClearSelection,
                    onOpenSession = onOpenSession,
                )
            } else {
                RealCatalogSection(state, realPlanCreation, onRefreshRealInventory, onCreateFastSmokePlan)
            }
        }
    }
}

@Composable
private fun ModeSwitch(
    demoEnabled: Boolean,
    onModeChange: (CatalogMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.catalog_mode_simulation),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = demoEnabled,
                    onCheckedChange = { checked ->
                        onModeChange(if (checked) CatalogMode.DEMO else CatalogMode.REAL)
                    },
                )
            }
            Text(
                text = stringResource(R.string.catalog_mode_simulation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RealCatalogSection(
    state: CatalogState,
    planCreation: RealPlanCreationState,
    onRefresh: () -> Unit,
    onCreateFastSmokePlan: (com.slafftrosheen.halomodellab.catalog.InstalledModelDescriptor) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.catalog_real_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.catalog_real_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.catalog_real_status,
                    realCatalogStatusText(state.realCatalogStatus, state.realCatalogReason),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OutlinedButton(onClick = onRefresh, enabled = state.realCatalogStatus != com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.LOADING,
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.catalog_real_refresh))
    }

    state.installedModels.forEach { model ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.modelId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.catalog_real_model_provider, model.providerId), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.catalog_real_model_state, model.state.name), style = MaterialTheme.typography.bodySmall)
                val backendCodes = (model.backendCandidates.map { it.name } + model.unsupportedBackendCodes).sorted()
                Text(stringResource(R.string.catalog_real_model_backends, backendCodes.joinToString().ifEmpty { "unknown" }),
                    style = MaterialTheme.typography.bodySmall)
                model.manifest?.let { manifest ->
                    Text(stringResource(R.string.catalog_real_model_generation, manifest.generationId.take(16)),
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onCreateFastSmokePlan(model) },
                    enabled = !planCreation.inProgress && model.eligibleForArtifactLease &&
                        com.slafftrosheen.halomodellab.domain.ModelRole.FAST in model.supportedRoles &&
                        com.slafftrosheen.halomodellab.domain.RealBackend.CPU in model.backendCandidates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.catalog_create_fast_smoke))
                }
            }
        }
    }

    planCreation.messageCode?.let { code ->
        Text(stringResource(R.string.catalog_real_plan_status, code), style = MaterialTheme.typography.bodySmall)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.catalog_download_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.catalog_download_body), style = MaterialTheme.typography.bodySmall)
        }
    }

    Text(
        text = stringResource(R.string.catalog_real_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun realCatalogStatusText(
    status: com.slafftrosheen.halomodellab.catalog.RealCatalogStatus,
    reason: String?,
): String = when (status) {
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.NOT_CHECKED -> stringResource(R.string.catalog_real_not_checked)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.LOADING -> stringResource(R.string.catalog_real_loading)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.READY -> stringResource(R.string.catalog_real_ready)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.EMPTY -> stringResource(R.string.catalog_real_empty)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.HALO_NOT_INSTALLED -> stringResource(R.string.catalog_real_halo_missing)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.BRIDGE_NOT_SUPPORTED -> stringResource(R.string.catalog_real_bridge_missing)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.SIGNATURE_MISMATCH -> stringResource(R.string.catalog_real_signature)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.CONSENT_REQUIRED -> stringResource(R.string.catalog_real_consent)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.PROTOCOL_MISMATCH -> stringResource(R.string.catalog_real_protocol)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.BRIDGE_BUSY -> stringResource(R.string.catalog_real_busy)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.BRIDGE_RECONNECTING -> stringResource(R.string.catalog_real_reconnecting)
    com.slafftrosheen.halomodellab.catalog.RealCatalogStatus.UNAVAILABLE -> stringResource(
        R.string.catalog_real_unavailable,
        reason ?: "UNKNOWN",
    )
}

@Composable
private fun DemoSection(
    state: CatalogState,
    onQueryChange: (String) -> Unit,
    onToggleScenario: (String) -> Unit,
    onClearSelection: () -> Unit,
    onOpenSession: () -> Unit,
) {
    SimulationBanner()

    Text(
        text = stringResource(R.string.catalog_demo_purpose),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.catalog_search_label)) },
    )

    val scenarioRows = DemoCatalog.scenarios.map { scenario ->
        ScenarioRow(
            scenario = scenario,
            title = stringResource(demoScenarioTitleRes(scenario.id)),
            description = stringResource(demoScenarioDescriptionRes(scenario.id)),
        )
    }

    val query = state.query.trim()
    val visibleScenarios = if (query.isEmpty()) {
        scenarioRows
    } else {
        scenarioRows.filter { row ->
            row.scenario.id.contains(query, ignoreCase = true) ||
                row.title.contains(query, ignoreCase = true) ||
                row.description.contains(query, ignoreCase = true)
        }
    }

    if (visibleScenarios.isEmpty()) {
        Text(
            text = stringResource(R.string.catalog_search_no_results),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    visibleScenarios.forEach { row ->
        ScenarioCard(
            row = row,
            selected = row.scenario.id in state.selectedScenarioIds,
            onToggle = { onToggleScenario(row.scenario.id) },
        )
    }

    Text(
        text = stringResource(R.string.catalog_selected_count, state.selectedScenarioIds.size),
        style = MaterialTheme.typography.titleSmall,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onClearSelection,
            enabled = state.selectedScenarioIds.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.catalog_clear_selection))
        }
        Button(
            onClick = onOpenSession,
            enabled = state.selectedScenarioIds.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.catalog_open_session))
        }
    }
}

private data class ScenarioRow(
    val scenario: DemoScenario,
    val title: String,
    val description: String,
)

@Composable
private fun ScenarioCard(
    row: ScenarioRow,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val scenario = row.scenario
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = scenario.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.catalog_card_role,
                        scenario.role.name,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.catalog_card_backend,
                        scenario.requestedBackend.name,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SimulationLabel()
            }
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

@Composable
private fun SimulationLabel() {
    Text(
        text = stringResource(R.string.catalog_card_sim_label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun SectionCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
