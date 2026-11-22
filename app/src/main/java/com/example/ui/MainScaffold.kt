package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessProfile
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.SandboxScreen
import com.example.ui.screens.SettingsScreen

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_CATALOG = "catalog"
const val ROUTE_SANDBOX = "sandbox"
const val ROUTE_LOGS = "logs"
const val ROUTE_SETTINGS = "settings"

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppLanguageToggleChip(
    isBn: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("language_toggle_chip")
    ) {
        Text(
            text = if (isBn) "বাংলা | EN" else "EN | বাংলা",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun MainFeatureScreen(
    viewModel: ShopViewModel,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val logsState by viewModel.replyLogsState.collectAsStateWithLifecycle()
    val sandboxState by viewModel.sandboxState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val profile = (profileState as? UiState.Success)?.data ?: BusinessProfile()
    val language = AppLanguage.fromRaw(profile.languagePreference)
    val strings = remember(language) { AppStrings.forLanguage(language) }
    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val logs = (logsState as? UiState.Success)?.data ?: emptyList()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val tabs = remember(strings) {
        listOf(
            NavTabItem(ROUTE_DASHBOARD, strings.tabDashboard, Icons.Filled.Home, Icons.Outlined.Home, "nav_dashboard"),
            NavTabItem(ROUTE_CATALOG, strings.tabCatalog, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_catalog"),
            NavTabItem(ROUTE_SANDBOX, strings.tabSandbox, Icons.Filled.Science, Icons.Outlined.Science, "nav_sandbox"),
            NavTabItem(ROUTE_LOGS, strings.tabLogs, Icons.Filled.Forum, Icons.Outlined.Forum, "nav_logs"),
            NavTabItem(ROUTE_SETTINGS, strings.tabSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
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
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (profileState is UiState.Loading && productsState is UiState.Loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (profileState is UiState.Error) {
                        val err = (profileState as UiState.Error).message
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    strings = strings,
                                    profile = profile,
                                    products = products,
                                    logs = logs,
                                    onToggleAutoReply = { enabled ->
                                        viewModel.toggleAutoReply(enabled)
                                    },
                                    onToggleLanguage = {
                                        val nextLang = if (language == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
                                        viewModel.toggleLanguage(nextLang)
                                    },
                                    onOpenSandbox = { navigateTo(ROUTE_SANDBOX) },
                                    onOpenCatalog = { navigateTo(ROUTE_CATALOG) }
                                )
                            }
                            composable(ROUTE_CATALOG) {
                                CatalogScreen(
                                    strings = strings,
                                    products = products,
                                    onSaveProduct = { id, name, price, curr, cat, desc, inStock, done ->
                                        viewModel.saveProduct(id, name, price, curr, cat, desc, inStock, done)
                                    },
                                    onToggleStock = { product ->
                                        viewModel.toggleProductStock(product)
                                    },
                                    onDeleteProduct = { productId ->
                                        viewModel.deleteProduct(productId)
                                    },
                                    onAddSampleProducts = {
                                        viewModel.addSampleProducts()
                                    }
                                )
                            }
                            composable(ROUTE_SANDBOX) {
                                SandboxScreen(
                                    strings = strings,
                                    sandboxState = sandboxState,
                                    onRunSimulation = { sender, msg, platform ->
                                        viewModel.runSandboxSimulation(sender, msg, platform)
                                    }
                                )
                            }
                            composable(ROUTE_LOGS) {
                                LogsScreen(
                                    strings = strings,
                                    logs = logs,
                                    onDeleteLog = { logId ->
                                        viewModel.deleteReplyLog(logId)
                                    }
                                )
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    strings = strings,
                                    profile = profile,
                                    onSaveProfile = { updated ->
                                        viewModel.updateProfile(updated)
                                    },
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
