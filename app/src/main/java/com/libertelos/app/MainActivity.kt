package com.libertelos.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.TxnType
import com.libertelos.app.security.AppLockController
import com.libertelos.app.security.SecurityPrefs
import com.libertelos.app.ui.accounts.AccountEditScreen
import com.libertelos.app.ui.accounts.AccountsScreen
import com.libertelos.app.ui.add.AddScreen
import com.libertelos.app.ui.categories.CategoryEditScreen
import com.libertelos.app.ui.home.HomeScreen
import com.libertelos.app.ui.home.TransactionDetailScreen
import com.libertelos.app.ui.ledger.LedgerFilter
import com.libertelos.app.ui.ledger.LedgerScreen
import com.libertelos.app.ui.ledger.LedgerTimeScope
import com.libertelos.app.ui.security.LockScreen
import com.libertelos.app.ui.security.SecuritySettingsScreen
import com.libertelos.app.ui.settings.DataSettingsScreen
import com.libertelos.app.ui.settings.SettingsScreen
import com.libertelos.app.ui.stats.StatsScreen
import com.libertelos.app.ui.theme.LiberTelosTheme
import com.libertelos.app.viewmodel.LedgerViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LiberTelosApp
        setContent {
            LiberTelosTheme {
                AppLockGate {
                    LiberTelosNav(app)
                }
            }
        }
    }
}

@Composable
private fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SecurityPrefs(context) }
    var locked by remember {
        mutableStateOf(AppLockController.needsUnlock(prefs))
    }

    DisposableEffect(Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (prefs.anyEnabled) {
                    AppLockController.lock()
                    locked = true
                }
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (locked) {
            LockScreen(
                prefs = prefs,
                onUnlocked = {
                    AppLockController.unlock()
                    locked = false
                }
            )
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
                onAccounts = { navController.navigate("accounts") },
                onSettings = { navController.navigate("settings") },
                onCategoryLedger = { categoryId, type, period ->
                    navController.navigate(
                        "ledger?categoryId=$categoryId&type=${type.name}&period=${period.name}"
                    )
                },
                onPeriodLedger = { period ->
                    val route = when (period) {
                        ReportPeriod.YEAR -> "ledger?timeScope=YEAR"
                        ReportPeriod.MONTH -> "ledger?timeScope=MONTH"
                        ReportPeriod.WEEK, ReportPeriod.DAY ->
                            "ledger?timeScope=PERIOD&period=${period.name}"
                    }
                    navController.navigate(route)
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDataSettings = { navController.navigate("settings/data") },
                onSecuritySettings = { navController.navigate("settings/security") }
            )
        }
        composable("settings/data") {
            DataSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings/security") {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "ledger?accountId={accountId}&categoryId={categoryId}&type={type}&period={period}&timeScope={timeScope}",
            arguments = listOf(
                navArgument("accountId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("categoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("period") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("timeScope") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val filter = ledgerFilterFromArgs(
                accountId = entry.arguments?.getString("accountId"),
                categoryId = entry.arguments?.getString("categoryId"),
                typeName = entry.arguments?.getString("type"),
                periodName = entry.arguments?.getString("period"),
                timeScopeName = entry.arguments?.getString("timeScope")
            )
            LedgerScreen(
                viewModel = viewModel,
                initialFilter = filter,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("add") },
                onOpenTransaction = { id -> navController.navigate("txn_detail/$id") },
                onEditAccount = { id -> navController.navigate("account_edit?id=$id") }
            )
        }
        composable("ledger") {
            LedgerScreen(
                viewModel = viewModel,
                initialFilter = LedgerFilter.homeDefault(),
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("add") },
                onOpenTransaction = { id -> navController.navigate("txn_detail/$id") },
                onEditAccount = { id -> navController.navigate("account_edit?id=$id") }
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
                onOpen = { id ->
                    navController.navigate("ledger?accountId=$id&timeScope=ALL")
                },
                onEdit = { id -> navController.navigate("account_edit?id=$id") }
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

private fun ledgerFilterFromArgs(
    accountId: String?,
    categoryId: String?,
    typeName: String?,
    periodName: String?,
    timeScopeName: String?
): LedgerFilter {
    val account = accountId?.toLongOrNull()
    val category = categoryId?.toLongOrNull()
    val type = typeName?.let { runCatching { TxnType.valueOf(it) }.getOrNull() }
    val period = periodName?.let { runCatching { ReportPeriod.valueOf(it) }.getOrNull() }
        ?: ReportPeriod.MONTH
    val timeScope = timeScopeName?.let {
        runCatching { LedgerTimeScope.valueOf(it) }.getOrNull()
    }

    return when {
        account != null && category == null && type == null && timeScope == LedgerTimeScope.ALL ->
            LedgerFilter.forAccount(account)
        category != null && type != null ->
            LedgerFilter.forCategory(category, type, period).let { base ->
                if (account != null) base.copy(accountId = account) else base
            }
        else -> LedgerFilter.homeDefault().copy(
            timeScope = timeScope ?: LedgerTimeScope.MONTH,
            period = period,
            accountId = account,
            categoryId = category,
            types = type?.let { setOf(it) } ?: LedgerFilter.allTypes
        )
    }
}
