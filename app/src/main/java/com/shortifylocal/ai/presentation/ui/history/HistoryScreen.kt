package com.shortifylocal.ai.presentation.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shortifylocal.ai.R

/**
 * Écran Historique — coquille vide assumée P1 (décision SPEC : contenu réel Phase 6).
 * Structure RTL-ready : centrage neutre, aucun alignement Left/Right.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.tab_historique),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
