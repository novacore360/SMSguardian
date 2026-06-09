package com.secureguardian.app.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.secureguardian.app.presentation.auth.AuthScreen
import com.secureguardian.app.presentation.detail.MessageDetailScreen
import com.secureguardian.app.presentation.flagged.FlaggedScreen
import com.secureguardian.app.presentation.inbox.InboxScreen
import com.secureguardian.app.presentation.inbox.QuarantineScreen
import com.secureguardian.app.presentation.onboarding.OnboardingScreen
import com.secureguardian.app.presentation.report.ReportScreen
import com.secureguardian.app.presentation.settings.PrivacyPolicyScreen
import com.secureguardian.app.presentation.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Inbox : Screen("inbox")
    object Flagged : Screen("flagged")
    object Report : Screen("report")
    object Settings : Screen("settings")
    object MessageDetail : Screen("message/{messageId}") {
        fun createRoute(id: String) = "message/$id"
    }
    object Quarantine : Screen("quarantine")
    object PrivacyPolicy : Screen("privacy_policy")
}

sealed class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    object Inbox : BottomNavItem(Screen.Inbox, "Inbox", Icons.Default.Inbox, Icons.Default.Inbox)
    object Flagged : BottomNavItem(Screen.Flagged, "Flagged", Icons.Default.Shield, Icons.Default.Shield)
    object Report : BottomNavItem(Screen.Report, "Report", Icons.Default.ReportProblem, Icons.Default.ReportProblem)
    object Settings : BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings, Icons.Default.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Inbox,
    BottomNavItem.Flagged,
    BottomNavItem.Report,
    BottomNavItem.Settings
)

@Composable
fun AppNavigation(isAuthenticated: Boolean, hasSeenOnboarding: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Inbox.route, Screen.Flagged.route,
        Screen.Report.route, Screen.Settings.route
    )

    val startDestination = when {
        !isAuthenticated -> Screen.Auth.route
        !hasSeenOnboarding -> Screen.Onboarding.route
        else -> Screen.Inbox.route
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
            exitTransition = { fadeOut() + slideOutHorizontally { -it / 8 } },
            popEnterTransition = { fadeIn() + slideInHorizontally { -it / 8 } },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } }
        ) {
            composable(Screen.Auth.route) {
                AuthScreen()
                LaunchedEffect(isAuthenticated) {
                    if (isAuthenticated) {
                        navController.navigate(
                            if (!hasSeenOnboarding) Screen.Onboarding.route else Screen.Inbox.route
                        ) { popUpTo(Screen.Auth.route) { inclusive = true } }
                    }
                }
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Inbox.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }

            composable(Screen.Inbox.route) {
                InboxScreen(
                    onMessageClick = { id ->
                        navController.navigate(Screen.MessageDetail.createRoute(id))
                    }
                )
            }

            composable(Screen.Flagged.route) { FlaggedScreen() }

            composable(Screen.Report.route) { ReportScreen() }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onSignOut = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }

            composable(
                route = Screen.MessageDetail.route,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType })
            ) {
                MessageDetailScreen(
                    onBack = { navController.popBackStack() },
                    onReport = { navController.navigate(Screen.Report.route) }
                )
            }

            composable(Screen.Quarantine.route) {
                QuarantineScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
