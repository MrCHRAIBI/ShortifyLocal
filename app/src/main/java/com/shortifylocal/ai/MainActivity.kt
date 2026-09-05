package com.shortifylocal.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource

// Coquille minimale P1 (preuve du chemin manifest → Application Hilt → Activity) —
// remplacée par l'écran réel au plan 01-03. Aucune chaîne en dur : ressource app_name.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text(text = stringResource(R.string.app_name))
        }
    }
}
