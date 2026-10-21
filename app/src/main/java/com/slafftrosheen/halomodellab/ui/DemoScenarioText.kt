package com.slafftrosheen.halomodellab.ui

import androidx.annotation.StringRes
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.catalog.DemoCatalog

/**
 * Demo scenario display text lives in resources, not in the catalog model.
 * Unknown ids must stay visible as an error, never be silently relabeled as a model.
 */
@StringRes
fun demoScenarioTitleRes(scenarioId: String): Int = when (scenarioId) {
    DemoCatalog.SUCCESS -> R.string.scenario_success_title
    DemoCatalog.LOAD_FAILURE -> R.string.scenario_load_failure_title
    DemoCatalog.EMPTY_OUTPUT -> R.string.scenario_empty_output_title
    DemoCatalog.TIMEOUT -> R.string.scenario_timeout_title
    else -> R.string.scenario_unknown_title
}

@StringRes
fun demoScenarioDescriptionRes(scenarioId: String): Int = when (scenarioId) {
    DemoCatalog.SUCCESS -> R.string.scenario_success_body
    DemoCatalog.LOAD_FAILURE -> R.string.scenario_load_failure_body
    DemoCatalog.EMPTY_OUTPUT -> R.string.scenario_empty_output_body
    DemoCatalog.TIMEOUT -> R.string.scenario_timeout_body
    else -> R.string.scenario_unknown_body
}
