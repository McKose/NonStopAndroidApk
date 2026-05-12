package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.gymapp.domain.tax.TaxAutoPostingUseCase
import javax.inject.Inject
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.presentation.calendar.CalendarScreen
import com.gymapp.presentation.dashboard.DashboardScreen
import com.gymapp.presentation.login.LoginScreen
import com.gymapp.presentation.packages.AddPackageScreen
import com.gymapp.presentation.packages.PackageListScreen
import com.gymapp.presentation.members.MemberDetailScreen
import com.gymapp.presentation.members.MemberListScreen
import com.gymapp.presentation.members.MeasurementEntryScreen
import com.gymapp.presentation.members.PostureCommentScreen
import com.gymapp.presentation.members.RegisterMemberScreen
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.presentation.finance.FinanceScreen
import com.gymapp.presentation.finance.FinanceRevenueScreen
import com.gymapp.presentation.finance.FinanceExpensesScreen
import com.gymapp.presentation.finance.FinanceTaxScreen
import com.gymapp.presentation.market.MarketScreen
import com.gymapp.presentation.market.OrderHistoryScreen
import com.gymapp.presentation.settings.SettingsScreen
import com.gymapp.presentation.settings.PersonnelScreen

import dagger.hilt.android.AndroidEntryPoint
import com.gymapp.ui.theme.GymAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var errorHandler: GlobalErrorHandler
    @Inject lateinit var taxAutoPostingUseCase: TaxAutoPostingUseCase

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences(this)
        val startDest = if (prefs.isLoggedIn) "dashboard" else "login"
        
        enableEdgeToEdge()
        // Geçmiş aylar için eksik KDV ve yıl sonları için eksik gelir vergisi
        // PENDING EXPENSE kayıtlarını oluştur (idempotent, açılışta 1 kez).
        lifecycleScope.launch { taxAutoPostingUseCase() }
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
                        startDestination = startDest
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
                            FinanceScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToRevenue = { navController.navigate("finance_revenue") },
                                onNavigateToExpenses = { navController.navigate("finance_expenses") },
                                onNavigateToTaxes = { navController.navigate("finance_taxes") }
                            )
                        }
                        composable("finance_revenue") {
                            FinanceRevenueScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("finance_expenses") {
                            FinanceExpensesScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("finance_taxes") {
                            FinanceTaxScreen(onNavigateBack = { navController.popBackStack() })
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
                                onNavigateBack = { navController.popBackStack() },
                                onRequestMeasurement = { id ->
                                    navController.navigate("measurement_entry/$id/0") {
                                        popUpTo("register_member") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "renew_package/{memberId}",
                            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                            RegisterMemberScreen(
                                isRenewal = true,
                                memberId = memberId,
                                onNavigateBack = { navController.popBackStack() },
                                onRequestMeasurement = { id ->
                                    navController.navigate("measurement_entry/$id/0") {
                                        popUpTo("renew_package/$memberId") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "member_detail/{memberId}",
                            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                            MemberDetailScreen(
                                memberId = memberId,
                                onNavigateBack = { navController.popBackStack() },
                                onAddMeasurement = {
                                    navController.navigate("measurement_entry/$memberId/0")
                                },
                                onEditMeasurement = { measurementId ->
                                    navController.navigate("measurement_entry/$memberId/$measurementId")
                                },
                                onOpenPosture = {
                                    navController.navigate("posture/$memberId")
                                }
                            )
                        }
                        composable(
                            route = "measurement_entry/{memberId}/{measurementId}",
                            arguments = listOf(
                                navArgument("memberId") { type = NavType.LongType },
                                navArgument("measurementId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                            val measurementId = backStackEntry.arguments?.getLong("measurementId") ?: 0L
                            MeasurementEntryScreen(
                                memberId = memberId,
                                measurementId = measurementId,
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "posture/{memberId}",
                            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                            PostureCommentScreen(
                                memberId = memberId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("package_list") {
                            PackageListScreen(
                                onNavigateToAdd = { navController.navigate("add_package") },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("add_package") {
                            AddPackageScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
