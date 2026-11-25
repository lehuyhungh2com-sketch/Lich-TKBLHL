package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppThemeMode
import com.example.ui.AppViewModel
import com.example.ui.MainUiState
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.LhlTimetableTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_TODAY = "today"
        const val TAB_TIMETABLE = "timetable"
        const val TAB_CALENDAR = "calendar"
        const val TAB_SETTINGS = "settings"
    }

    private val viewModel: AppViewModel by viewModels()
    private var pendingTabRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTabRoute = intent?.getStringExtra(EXTRA_OPEN_TAB)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (uiState.preferences.appThemeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !useDarkTheme
                    controller.isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            LhlTimetableTheme(darkTheme = useDarkTheme) {
                LhlTimetableRoot(
                    uiState = uiState,
                    viewModel = viewModel,
                    pendingTabRoute = pendingTabRoute,
                    onConsumePendingTab = { pendingTabRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_TAB)?.let { target ->
            pendingTabRoute = target
        }
    }
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val APP_TABS = listOf(
    NavDestinationItem(
        route = MainActivity.TAB_TODAY,
        label = "Hôm nay",
        selectedIcon = Icons.Rounded.Today,
        unselectedIcon = Icons.Outlined.Today
    ),
    NavDestinationItem(
        route = MainActivity.TAB_TIMETABLE,
        label = "TKB Tuần",
        selectedIcon = Icons.Rounded.ViewWeek,
        unselectedIcon = Icons.Outlined.ViewWeek
    ),
    NavDestinationItem(
        route = MainActivity.TAB_CALENDAR,
        label = "Lịch",
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    NavDestinationItem(
        route = MainActivity.TAB_SETTINGS,
        label = "Cài đặt",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@Composable
private fun LhlTimetableRoot(
    uiState: MainUiState,
    viewModel: AppViewModel,
    pendingTabRoute: String?,
    onConsumePendingTab: () -> Unit
) {
    if (!uiState.isInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.isLoggedIn) {
        LoginScreen(
            uiState = uiState,
            onLogin = viewModel::login,
            onClearError = viewModel::clearLoginError
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MainActivity.TAB_TODAY

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingTabRoute) {
        if (!pendingTabRoute.isNullOrBlank() && APP_TABS.any { it.route == pendingTabRoute }) {
            navigateToTab(pendingTabRoute)
            onConsumePendingTab()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        APP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
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
                if (useNavRail) {
                    NavigationRail {
                        APP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MainActivity.TAB_TODAY,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(MainActivity.TAB_TODAY) {
                        TodayScreen(
                            uiState = uiState,
                            onToggleHomeroom = viewModel::toggleHomeroomMode,
                            onSyncNow = { viewModel.syncNow(force = false) },
                            onRefreshWeather = viewModel::refreshWeatherSilently,
                            onOpenTimetable = { navigateToTab(MainActivity.TAB_TIMETABLE) },
                            onDismissBanner = viewModel::dismissBanner
                        )
                    }

                    composable(MainActivity.TAB_TIMETABLE) {
                        TimetableScreen(
                            uiState = uiState,
                            onSelectWeek = viewModel::selectWeek,
                            onToggleHomeroom = viewModel::toggleHomeroomMode,
                            onSyncNow = { viewModel.syncNow(force = true) }
                        )
                    }

                    composable(MainActivity.TAB_CALENDAR) {
                        CalendarScreen(
                            uiState = uiState,
                            onJumpToWeek = { week ->
                                viewModel.selectWeek(week)
                                navigateToTab(MainActivity.TAB_TIMETABLE)
                            }
                        )
                    }

                    composable(MainActivity.TAB_SETTINGS) {
                        SettingsScreen(
                            uiState = uiState,
                            onSyncNow = { viewModel.syncNow(force = true) },
                            onUpdateAppTheme = viewModel::updateAppThemeMode,
                            onUpdateDailyReminder = viewModel::updateDailyReminder,
                            onTogglePeriodReminder = viewModel::togglePeriodReminder,
                            onSendTestNotification = viewModel::sendTestReminderNotification,
                            onToggleGpsLocation = viewModel::setUseGpsLocation,
                            onUpdateWidgetAppearance = viewModel::updateWidgetAppearance,
                            onLogout = viewModel::logout
                        )
                    }
                }
            }
        }
    }
}
