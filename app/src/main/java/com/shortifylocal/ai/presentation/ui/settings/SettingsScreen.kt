package com.shortifylocal.ai.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shortifylocal.ai.R
import com.shortifylocal.ai.presentation.theme.ThemeMode

/**
 * Écran Paramètres — coquille P1 : contrôle de bascule du thème (D-05, chemin complet
 * SettingsScreen -> ThemeViewModel -> DataStore -> recomposition). Contenu réel Phase 6.
 * Icônes non directionnelles (lune/soleil/auto) : non flippées RTL (docs/02 §1.6).
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.theme_toggle_cd),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                    },
                    contentDescription = stringResource(R.string.theme_toggle_cd),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
