package com.livesac.manager.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val auth = rememberAdminAuth()

    // Première utilisation : choix obligatoire du mot de passe administrateur.
    var configured by remember { mutableStateOf(auth.isConfigured()) }
    if (!configured) {
        PasswordSetupDialog(auth) { configured = true }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // L'onglet « actif » reste celui d'où l'on vient quand on ouvre un écran de détail.
    var tab by rememberSaveable { mutableStateOf(TAB_LIVES) }
    LaunchedEffect(route) {
        when {
            route == TAB_CLIENTS || route == TAB_LIVES || route == TAB_PACKING -> tab = route.orEmpty()
            route?.startsWith("live/") == true || route?.startsWith("add/") == true -> tab = TAB_LIVES
        }
    }

    fun openTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Barre du bas fixe : visible partout sauf dans le formulaire de saisie (clavier + bouton Enregistrer).
    val showBar = route != null && !route.startsWith("add/")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) BottomNavBar(selected = tab, onSelect = ::openTab) }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TAB_LIVES,
            modifier = Modifier.padding(padding)
        ) {
            composable(TAB_CLIENTS) {
                ClientsScreen(
                    onOpenClient = { id -> nav.navigate("client/$id") },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(TAB_LIVES) {
                LivesScreen(
                    onOpenLive = { id -> nav.navigate("live/$id") },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(TAB_PACKING) {
                PackingScreen(
                    onOpenClient = { id -> nav.navigate("client/$id") },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }

            composable(
                route = "live/{liveId}",
                arguments = listOf(navArgument("liveId") { type = NavType.LongType })
            ) { entry ->
                val liveId = entry.arguments?.getLong("liveId") ?: 0L
                LiveScreen(
                    liveId = liveId,
                    onBack = { nav.popBackStack() },
                    onAddJp = { nav.navigate("add/$liveId") },
                    onOpenClient = { clientId -> nav.navigate("client/$clientId") }
                )
            }

            composable(
                route = "add/{liveId}",
                arguments = listOf(navArgument("liveId") { type = NavType.LongType })
            ) { entry ->
                val liveId = entry.arguments?.getLong("liveId") ?: 0L
                AddJpScreen(liveId = liveId, onBack = { nav.popBackStack() })
            }

            composable(
                route = "client/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.LongType })
            ) { entry ->
                val clientId = entry.arguments?.getLong("clientId") ?: 0L
                ClientScreen(clientId = clientId, onBack = { nav.popBackStack() })
            }
        }
    }
}
