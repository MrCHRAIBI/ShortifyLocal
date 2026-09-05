package com.shortifylocal.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shortifylocal.ai.presentation.navigation.AppShell
import com.shortifylocal.ai.presentation.theme.ShortifyLocalTheme
import com.shortifylocal.ai.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hôte Compose P1 : setContent → ShortifyLocalTheme (D-05/D-06) → AppShell (3 onglets).
 * Chemin complet de la bascule : tap → vm.toggleTheme(systemDark) → ThemeRepository →
 * DataStore « settings » → StateFlow → recomposition.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ThemeViewModel = hiltViewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            ShortifyLocalTheme(themeMode = mode) {
                AppShell(
                    themeMode = mode,
                    onThemeToggle = { systemDark -> vm.toggleTheme(systemDark) },
                )
            }
        }
    }
}
