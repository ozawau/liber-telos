package com.libertelos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.add.AddScreen
import com.libertelos.app.ui.categories.CategoryEditScreen
import com.libertelos.app.ui.theme.LiberTelosTheme
import com.libertelos.app.viewmodel.LedgerViewModel

/**
 * 桌面小组件入口：只打开「记一笔」，完成后 finish 回到桌面。
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LiberTelosApp
        setContent {
            LiberTelosTheme {
                QuickAddNav(
                    app = app,
                    onFinished = { finish() }
                )
            }
        }
    }
}

@Composable
private fun QuickAddNav(
    app: LiberTelosApp,
    onFinished: () -> Unit
) {
    val navController = rememberNavController()
    val viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.Factory(app.repository)
    )

    NavHost(
        navController = navController,
        startDestination = "add",
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable("add") {
            AddScreen(
                viewModel = viewModel,
                transactionId = null,
                onBack = onFinished,
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
    }
}
