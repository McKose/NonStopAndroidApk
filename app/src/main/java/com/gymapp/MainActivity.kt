package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.gymapp.presentation.common.GlobalErrorHandler
import org.koin.android.ext.android.inject
import android.widget.Toast
import org.koin.androidx.compose.koinViewModel
import com.gymapp.presentation.calendar.CalendarScreen
import com.gymapp.presentation.dashboard.DashboardScreen
import com.gymapp.presentation.login.LoginScreen
import com.gymapp.presentation.packages.AddPackageScreen
import com.gymapp.presentation.packages.PackageListScreen
import com.gymapp.presentation.members.MemberDetailScreen
import com.gymapp.presentation.members.MemberListScreen
import com.gymapp.presentation.members.RegisterMemberScreen
import com.gymapp.presentation.finance.FinanceScreen
import com.gymapp.presentation.market.MarketScreen
import com.gymapp.presentation.market.OrderHistoryScreen
import com.gymapp.presentation.settings.SettingsScreen
import com.gymapp.presentation.settings.PersonnelScreen

import com.gymapp.ui.theme.GymAppTheme

class MainActivity : ComponentActivity() {
    private val errorHandler: GlobalErrorHandler by inject()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                errorHandler.errors.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            GymAppTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToMembers = { navController.navigate("member_list") },
                                onNavigateToFinance = { navController.navigate("finance") },
                                onNavigateToMarket = { navController.navigate("market") },
                                onNavigateToCalendar = { navController.navigate("calendar") },
                                onNavigateToPackages = { navController.navigate("package_list") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("calendar") {
                            CalendarScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("member_list") {
                            MemberListScreen(
                                onNavigateToRegister = {
                                    navController.navigate("register_member")
                                },
                                onNavigateToDetail = { memberId ->
                                    navController.navigate("member_detail/$memberId")
                                },
                                onNavigateToPackages = {
                                    navController.navigate("package_list")
                                },
                                onNavigateToFinance = {
                                    navController.navigate("finance")
                                },
                                onNavigateToMarket = {
                                    navController.navigate("market")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToRenew = { memberId ->
                                    navController.navigate("renew_package/$memberId")
                                }
                            )
                        }
                        composable("finance") {
                            FinanceScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("market") {
                            MarketScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToOrders = { navController.navigate("order_history") }
                            )
                        }
                        composable("order_history") {
                            OrderHistoryScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPersonnel = { navController.navigate("personnel") },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("personnel") {
                            PersonnelScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("register_member") {
                            RegisterMemberScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "renew_package/{memberId}",
                            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getString("memberId").orEmpty()
                            RegisterMemberScreen(
                                isRenewal = true,
                                memberId = memberId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "member_detail/{memberId}",
                            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getString("memberId").orEmpty()
                            MemberDetailScreen(
                                memberId = memberId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("package_list") {
                            PackageListScreen(
                                onNavigateToAdd = { packageId ->
                                    if (packageId == null) {
                                        navController.navigate("add_package")
                                    } else {
                                        navController.navigate("edit_package/$packageId")
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("add_package") {
                            AddPackageScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(
                            route = "edit_package/{packageId}",
                            arguments = listOf(navArgument("packageId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            AddPackageScreen(
                                packageId = backStackEntry.arguments?.getString("packageId"),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
