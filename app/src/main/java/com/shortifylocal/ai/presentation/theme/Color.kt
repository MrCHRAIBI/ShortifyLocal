package com.shortifylocal.ai.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens normatifs Soft-Clean (docs/02-UI-UX.md §1.1 couleurs + §1.2 ombre, D-06).
 * Les valeurs hex sont NORMATIVES — toute modification exige un amendement du cahier
 * des charges. Consommés par [ShortifyLocalTheme] (mapping slots M3).
 */
object SoftCleanTokens {
    // §1.1 — fond / surfaces
    val BackgroundLight = Color(0xFFEEF0F2)
    val BackgroundDark = Color(0xFF0E0E10)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF1A1A1E)

    // §1.1 — textes
    val PrimaryTextLight = Color(0xFF1A1A1A)
    val PrimaryTextDark = Color(0xFFFFFFFF)
    val SecondaryTextLight = Color(0xFF8A8F98)
    val SecondaryTextDark = Color(0xFF9A9AA2)

    // §1.1 — boutons primaires (texte : blanc en clair / noir en sombre)
    val PrimaryButtonLight = Color(0xFF111111)
    val PrimaryButtonDark = Color(0xFFFFFFFF)

    // §1.1 — états actifs (onglet, tag sélectionné, liens, score moyen)
    val AccentActiveLight = Color(0xFFE4590C)
    val AccentActiveDark = Color(0xFFFF7A3D)

    // §1.1 — score excellent (identique clair/sombre)
    val ScoreExcellent = Color(0xFF22C55E)

    // §1.2 — ombre unique verticale : noir 5 % clair / 30 % sombre.
    // Interdiction neumorphisme (§0.3) : UNE seule ombre par surface, jamais deux superposées.
    val ShadowLight = Color(0xFF000000).copy(alpha = 0.05f)
    val ShadowDark = Color(0xFF000000).copy(alpha = 0.30f)
}
