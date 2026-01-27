package com.easylog.android.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.easylog.android.ui.customer.AddCustomerScreen
import com.easylog.android.ui.customer.CustomerSelectionScreen
import com.easylog.android.ui.history.HistoryScreen
import com.easylog.android.ui.home.HomeScreen
import com.easylog.android.ui.job.ActiveJobScreen
import com.easylog.android.ui.job.CompleteJobScreen
import com.easylog.android.ui.job.SignatureScreen
import com.easylog.android.ui.job.StartJobScreen
import com.easylog.android.ui.login.LoginScreen
import com.easylog.android.ui.settings.SettingsScreen
import com.easylog.android.ui.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object CustomerSelection : Screen("customer_selection")
    object AddCustomer : Screen("add_customer")
    object StartJob : Screen("start_job/{customerId}") {
        fun createRoute(customerId: String) = "start_job/$customerId"
    }
    object ActiveJob : Screen("active_job")
    object CompleteJob : Screen("complete_job/{documentId}") {
        fun createRoute(documentId: String) = "complete_job/$documentId"
    }
    object Signature : Screen("signature/{documentId}/{workDescription}/{onSiteMinutes}") {
        fun createRoute(documentId: String, workDescription: String, onSiteMinutes: Int) =
            "signature/$documentId/$workDescription/$onSiteMinutes"
    }
    object JobHistory : Screen("job_history")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(500)) },
        exitTransition = { fadeOut(animationSpec = tween(500)) },
        popEnterTransition = { fadeIn(animationSpec = tween(500)) },
        popExitTransition = { fadeOut(animationSpec = tween(500)) }
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onStartNewJob = {
                    navController.navigate(Screen.CustomerSelection.route)
                },
                onViewActiveJob = {
                    navController.navigate(Screen.ActiveJob.route)
                },
                onViewJobHistory = {
                    navController.navigate(Screen.JobHistory.route)
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CustomerSelection.route) {
            CustomerSelectionScreen(
                onCustomerSelected = { customerId ->
                    navController.navigate(Screen.StartJob.createRoute(customerId))
                },
                onAddCustomer = {
                    navController.navigate(Screen.AddCustomer.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddCustomer.route) {
            AddCustomerScreen(
                onCustomerCreated = { customerId ->
                    // Navigate to Start Job with the newly created customer
                    navController.navigate(Screen.StartJob.createRoute(customerId)) {
                        popUpTo(Screen.CustomerSelection.route)
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.StartJob.route) {
            StartJobScreen(
                onJobStarted = {
                    navController.navigate(Screen.ActiveJob.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ActiveJob.route) {
            ActiveJobScreen(
                onEndJob = { documentId ->
                    navController.navigate(Screen.CompleteJob.createRoute(documentId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CompleteJob.route) {
            CompleteJobScreen(
                onProceedToSignature = { documentId, workDescription, onSiteMinutes ->
                    navController.navigate(
                        Screen.Signature.createRoute(documentId, workDescription, onSiteMinutes)
                    )
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Signature.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("workDescription") { type = NavType.StringType },
                navArgument("onSiteMinutes") { type = NavType.IntType }
            )
        ){
            SignatureScreen(
                onJobCompleted = {
                    // Navigate back to home and clear the entire job flow
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.JobHistory.route) {
            HistoryScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
