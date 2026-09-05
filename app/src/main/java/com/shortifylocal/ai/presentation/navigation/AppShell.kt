package com.shortifylocal.ai.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shortifylocal.ai.R
import com.shortifylocal.ai.presentation.theme.LocalSoftCleanDark
import com.shortifylocal.ai.presentation.theme.ThemeMode
import com.shortifylocal.ai.presentation.theme.softShadow
import com.shortifylocal.ai.presentation.ui.history.HistoryScreen
import com.shortifylocal.ai.presentation.ui.home.HomeScreen
import com.shortifylocal.ai.presentation.ui.settings.SettingsScreen

/** Destination d'onglet de la coquille P1 (routes string — Navigation Compose 2.10). */
private data class TabSpec(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

// Glyphe History non miroiré acceptable en P1 — le miroir RTL reste réservé aux icônes
// directionnelles (docs/02 §1.6) ; revue visuelle Phase 6.
private val TABS = listOf(
    TabSpec(route = "accueil", labelRes = R.string.tab_accueil, icon = Icons.Filled.Home),
    TabSpec(route = "historique", labelRes = R.string.tab_historique, icon = Icons.Filled.History),
    TabSpec(route = "parametres", labelRes = R.string.tab_parametres, icon = Icons.Filled.Settings),
)

/**
 * Coquille 3 onglets (Accueil / Historique / Paramètres) — docs/02 §3.
 * Le BottomNavPill définitif arrive en Phase 6 ; P1 = NavigationBar standard stylée par
 * les tokens (fond surfaceContainer, ombre unique §1.2, rayon pilule 28).
 * Navigation launchSingleTop/restoreState/saveState : un seul onglet actif, état conservé.
 */
@Composable
fun AppShell(
    themeMode: ThemeMode,
    onThemeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val dark = LocalSoftCleanDark.current

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.softShadow(
                    shape = RoundedCornerShape(28.dp),
                    darkTheme = dark,
                ),
            ) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                        label = { Text(text = stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "accueil",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(route = "accueil") { HomeScreen() }
            composable(route = "historique") { HistoryScreen() }
            composable(route = "parametres") {
                // Lecture de la CompositionLocal en contexte @Composable — la capture est
                // passée au callback (un lambda onClick n'est pas @Composable).
                val darkNow = LocalSoftCleanDark.current
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeToggle = { onThemeToggle(darkNow) },
                )
            }
        }
    }
}
