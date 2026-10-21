package com.slafftrosheen.halomodellab.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.slafftrosheen.halomodellab.AppContainer
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.ui.catalog.CatalogRoute
import com.slafftrosheen.halomodellab.ui.history.HistoryRoute
import com.slafftrosheen.halomodellab.ui.history.SessionDetailsRoute
import com.slafftrosheen.halomodellab.ui.session.SessionRoute
import com.slafftrosheen.halomodellab.ui.settings.SettingsScreen

object LabRoutes {
    const val CATALOG: String = "catalog"
    const val SESSION: String = "session"
    const val HISTORY: String = "history"
    const val SETTINGS: String = "settings"

    const val SESSION_ID_ARG: String = "sessionId"
    const val SESSION_DETAILS: String = "session_details/{$SESSION_ID_ARG}"

    fun sessionDetails(sessionId: String): String = "session_details/$sessionId"
}

private data class LabDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val destinations = listOf(
    LabDestination(LabRoutes.CATALOG, R.string.nav_catalog, Icons.Filled.List),
    LabDestination(LabRoutes.SESSION, R.string.nav_session, Icons.Filled.PlayArrow),
    LabDestination(LabRoutes.HISTORY, R.string.nav_history, Icons.Filled.DateRange),
    LabDestination(LabRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/**
 * Navigation shell. Screens observe state; none of them owns execution or creates a session.
 */
@Composable
fun LabApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigateToTab(destination.route)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LabRoutes.CATALOG,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(LabRoutes.CATALOG) {
                CatalogRoute(
                    container = container,
                    onOpenSession = { navController.navigateToTab(LabRoutes.SESSION) },
                )
            }
            composable(LabRoutes.SESSION) {
                SessionRoute(
                    container = container,
                    onOpenCatalog = { navController.navigateToTab(LabRoutes.CATALOG) },
                )
            }
            composable(LabRoutes.HISTORY) {
                HistoryRoute(
                    container = container,
                    onOpenSession = { sessionId ->
                        navController.navigate(LabRoutes.sessionDetails(sessionId))
                    },
                )
            }
            composable(
                route = LabRoutes.SESSION_DETAILS,
                arguments = listOf(navArgument(LabRoutes.SESSION_ID_ARG) { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString(LabRoutes.SESSION_ID_ARG)
                if (sessionId != null) {
                    SessionDetailsRoute(
                        container = container,
                        sessionId = sessionId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(LabRoutes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
