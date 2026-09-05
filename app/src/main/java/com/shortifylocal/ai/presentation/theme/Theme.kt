package com.shortifylocal.ai.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Obscurité effective du thème, exposée aux composants (ex. [softShadow], coquille).
 * Défaut `false` hors [ShortifyLocalTheme] (aperçus, tests).
 */
val LocalSoftCleanDark = compositionLocalOf { false }

/**
 * Thème Soft-Clean (docs/02-UI-UX.md §1.1–§1.3 mappés sur Material 3, D-06).
 *
 * Sémantique D-05 exacte :
 * - SYSTEM : suit le système ([isSystemInDarkTheme] comme valeur initiale) ;
 * - LIGHT / DARK : choix explicite persisté (DataStore via ThemeViewModel) qui prime.
 *
 * Typographie : défaut Material 3 — la typographie complète et les polices bundlées
 * (Inter/Cairo/Noto, docs/02 §1.4) sont Phase 6, hors périmètre P1.
 */
@Composable
fun ShortifyLocalTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val background = if (dark) SoftCleanTokens.BackgroundDark else SoftCleanTokens.BackgroundLight
    val card = if (dark) SoftCleanTokens.CardDark else SoftCleanTokens.CardLight
    val primaryText = if (dark) SoftCleanTokens.PrimaryTextDark else SoftCleanTokens.PrimaryTextLight
    val secondaryText = if (dark) SoftCleanTokens.SecondaryTextDark else SoftCleanTokens.SecondaryTextLight
    val primaryButton = if (dark) SoftCleanTokens.PrimaryButtonDark else SoftCleanTokens.PrimaryButtonLight
    val accentActive = if (dark) SoftCleanTokens.AccentActiveDark else SoftCleanTokens.AccentActiveLight

    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        background = background,
        surface = background,
        surfaceContainer = card,
        onBackground = primaryText,
        onSurface = primaryText,
        onSurfaceVariant = secondaryText,
        primary = primaryButton,
        onPrimary = if (dark) Color.Black else Color.White,
        secondary = accentActive,
        onSecondary = Color.White,
        secondaryContainer = accentActive,
        onSecondaryContainer = if (dark) Color(0xFF0E0E10) else Color.White,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SoftCleanShapes,
        content = {
            CompositionLocalProvider(LocalSoftCleanDark provides dark, content = content)
        },
    )
}
