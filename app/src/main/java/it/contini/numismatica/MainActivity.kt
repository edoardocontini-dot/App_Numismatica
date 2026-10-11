package it.contini.numismatica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.contini.numismatica.ui.CollectionScreen
import it.contini.numismatica.ui.DetailScreen
import it.contini.numismatica.ui.ExportExcelDialogs
import it.contini.numismatica.ui.NumismaticaTheme
import it.contini.numismatica.ui.SearchScreen
import it.contini.numismatica.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NumismaticaTheme { App() }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("search", "Cerca", Icons.Filled.Search),
    Tab("collection", "Collezione", Icons.Filled.CollectionsBookmark),
    Tab("settings", "Impostazioni", Icons.Filled.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    // First launch without a key: open on Settings so Dad can paste it.
    val start = remember { if (vm.apiKey.value.isBlank()) "settings" else "search" }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    ExportExcelDialogs(vm)

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable("search") {
                SearchScreen(vm, openDetail = { nav.navigate("detail/$it") })
            }
            composable("collection") {
                CollectionScreen(vm, openDetail = { nav.navigate("detail/$it") })
            }
            composable("settings") {
                SettingsScreen(vm, onGoSearch = {
                    nav.navigate("search") {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                DetailScreen(vm, id, onBack = { nav.popBackStack() })
            }
        }
    }
}
