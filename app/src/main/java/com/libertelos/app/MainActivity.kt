package com.libertelos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.accounts.AccountEditScreen
import com.libertelos.app.ui.accounts.AccountLedgerScreen
import com.libertelos.app.ui.accounts.AccountsScreen
import com.libertelos.app.ui.add.AddScreen
import com.libertelos.app.ui.categories.CategoryEditScreen
import com.libertelos.app.ui.home.HomeScreen
import com.libertelos.app.ui.home.TransactionDetailScreen
import com.libertelos.app.ui.home.TransactionListScreen
import com.libertelos.app.ui.stats.StatsScreen
import com.libertelos.app.ui.theme.LiberTelosTheme
import com.libertelos.app.viewmodel.LedgerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LiberTelosApp
        setContent {
            LiberTelosTheme {
                LiberTelosNav(app)
            }
        }
    }
}

@Composable
private fun LiberTelosNav(app: LiberTelosApp) {
    val navController = rememberNavController()
    val viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.Factory(app.repository)
    )

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAdd = { navController.navigate("add") },
                onLedger = { navController.navigate("ledger") },
                onStats = { navController.navigate("stats") },
                onAccounts = { navController.navigate("accounts") }
            )
        }
        composable("ledger") {
            TransactionListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("add") },
                onOpenTransaction = { id -> navController.navigate("txn_detail/$id") }
            )
        }
        composable(
            route = "txn_detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val txnId = entry.arguments?.getLong("id") ?: return@composable
            TransactionDetailScreen(
                viewModel = viewModel,
                transactionId = txnId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("add?id=$txnId") }
            )
        }
        composable(
            route = "add?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val txnId = entry.arguments?.getString("id")?.toLongOrNull()
            AddScreen(
                viewModel = viewModel,
                transactionId = txnId,
                onBack = { navController.popBackStack() },
                onEditCategories = { type ->
                    navController.navigate("category_edit/${type.name}")
                }
            )
        }
        composable(
            route = "category_edit/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { entry ->
            val typeName = entry.arguments?.getString("type") ?: TxnType.EXPENSE.name
            val type = runCatching { TxnType.valueOf(typeName) }.getOrDefault(TxnType.EXPENSE)
            CategoryEditScreen(
                viewModel = viewModel,
                type = type,
                onBack = { navController.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("accounts") {
            AccountsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate("account_edit") },
                onOpen = { id -> navController.navigate("account_ledger/$id") },
                onEdit = { id -> navController.navigate("account_edit?id=$id") }
            )
        }
        composable(
            route = "account_ledger/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val accountId = entry.arguments?.getLong("id") ?: return@composable
            AccountLedgerScreen(
                viewModel = viewModel,
                accountId = accountId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("account_edit?id=$accountId") },
                onOpenTransaction = { id -> navController.navigate("txn_detail/$id") }
            )
        }
        composable(
            route = "account_edit?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val rawId = entry.arguments?.getString("id")
            val accountId = rawId?.toLongOrNull()
            AccountEditScreen(
                viewModel = viewModel,
                accountId = accountId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
