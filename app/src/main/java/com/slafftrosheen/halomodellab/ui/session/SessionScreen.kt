package com.slafftrosheen.halomodellab.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slafftrosheen.halomodellab.AppContainer
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.catalog.DemoScenario
import com.slafftrosheen.halomodellab.ui.demoScenarioTitleRes

@Composable
fun SessionRoute(
    container: AppContainer,
    onOpenCatalog: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.catalogRepository),
    )
    val selected by viewModel.selectedScenarios.collectAsStateWithLifecycle()

    SessionScreen(
        selectedScenarios = selected,
        onOpenCatalog = onOpenCatalog,
        onClearSelection = viewModel::clearSelection,
    )
}

/**
 * New-session screen for HML-L0.
 *
 * It shows only what actually exists: the in-memory demo selection. Plan parameters,
 * the immutable plan snapshot and the queue belong to HML-L1/HML-L2, so the start button
 * stays disabled and says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    selectedScenarios: List<DemoScenario>,
    onOpenCatalog: () -> Unit,
    onClearSelection: () -> Unit,
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
            if (selectedScenarios.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.session_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.session_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Button(
                    onClick = onOpenCatalog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.session_open_catalog))
                }
            } else {
                SimulationBanner()

                Text(
                    text = stringResource(R.string.session_profile, DemoCatalog.PROFILE_ID),
                    style = MaterialTheme.typography.titleSmall,
                )

                selectedScenarios.forEachIndexed { index, scenario ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.session_item_title,
                                    index + 1,
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
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.session_in_memory),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.session_plan_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onClick = onClearSelection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.session_clear_selection))
                }
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
