package com.shortifylocal.ai.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Ombre unique verticale Soft-Clean (docs/02-UI-UX.md §1.2, Option A du research —
 * signature Modifier.shadow vérifiée dans androidx-main) : UNE seule ombre par surface,
 * couleur token 5 % clair / 30 % sombre. `clip = false` pour ne pas rogner l'ombre.
 * Interdiction neumorphisme (docs/02 §0.3) : jamais deux ombres superposées.
 *
 * Option B (`drawBehind` custom, blur 24/16 offset (0,8)/(0,6)) reste le recours
 * sanctionné si la revue visuelle de fin de phase juge l'approximation insuffisante.
 */
fun Modifier.softShadow(
    shape: Shape = RoundedCornerShape(28.dp),
    darkTheme: Boolean,
): Modifier = shadow(
    elevation = 6.dp,
    shape = shape,
    clip = false,
    ambientColor = if (darkTheme) SoftCleanTokens.ShadowDark else SoftCleanTokens.ShadowLight,
    spotColor = if (darkTheme) SoftCleanTokens.ShadowDark else SoftCleanTokens.ShadowLight,
)
