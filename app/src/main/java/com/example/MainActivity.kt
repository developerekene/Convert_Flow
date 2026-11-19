package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.ThemePreference
import com.example.ui.navigation.AppDestination
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.ConvertFlowTheme
import com.example.ui.viewmodel.ConvertFlowViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ConvertFlowViewModel = viewModel(factory = ConvertFlowViewModel.Factory)
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            ConvertFlowTheme(darkTheme = darkTheme) {
                ConvertFlowRoot(viewModel = viewModel)
            }
        }
    }
}

private data class TopLevelTab(
    val label: String,
    val route: AppDestination,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab("Convert", AppDestination.Convert, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz, "tab_convert"),
    TopLevelTab("Tools", AppDestination.Tools, Icons.Filled.GridView, Icons.Outlined.GridView, "tab_tools"),
    TopLevelTab("Library", AppDestination.History, Icons.Filled.History, Icons.Outlined.History, "tab_history"),
    TopLevelTab("Settings", AppDestination.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
)

@Composable
private fun ConvertFlowRoot(viewModel: ConvertFlowViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isPreviewRoute = currentDest?.hasRoute<AppDestination.Preview>() == true

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateToTab(route: AppDestination) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail && !isPreviewRoute) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            TOP_LEVEL_TABS.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isPreviewRoute) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        TOP_LEVEL_TABS.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppDestination.Convert,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable<AppDestination.Convert> {
                        ConvertScreen(
                            viewModel = viewModel,
                            onOpenPreview = { path, name, ext ->
                                navController.navigate(AppDestination.Preview(path, name, ext))
                            }
                        )
                    }
                    composable<AppDestination.Tools> {
                        ToolsScreen(
                            onLaunchTool = { tool ->
                                viewModel.launchStudioTool(tool)
                                navigateToTab(AppDestination.Convert)
                            }
                        )
                    }
                    composable<AppDestination.History> {
                        HistoryScreen(
                            viewModel = viewModel,
                            onOpenPreview = { path, name, ext ->
                                navController.navigate(AppDestination.Preview(path, name, ext))
                            },
                            onGoToConvert = {
                                navigateToTab(AppDestination.Convert)
                            }
                        )
                    }
                    composable<AppDestination.Settings> {
                        SettingsScreen(viewModel = viewModel)
                    }
                    composable<AppDestination.Preview> { entry ->
                        val args = entry.toRoute<AppDestination.Preview>()
                        PreviewScreen(
                            filePath = args.filePath,
                            fileName = args.fileName,
                            extension = args.extension,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
