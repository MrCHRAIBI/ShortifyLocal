package com.shortifylocal.ai.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rayons normatifs Soft-Clean (docs/02-UI-UX.md §1.3) mappés sur les slots Material 3
 * (D-06 — mapping à discrétion sous contrainte du résultat visuel) :
 * champs/chips 16 · boutons 16 · images 24 · pilule nav 28 · grandes cartes/modales 32.
 * Les composants Phase 6 peuvent aussi passer des [RoundedCornerShape] explicites.
 */
val SoftCleanShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
