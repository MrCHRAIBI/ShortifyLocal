package com.shortifylocal.ai.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la sémantique D-05 : l'enum ThemeMode et sa logique pure de bascule.
 *
 * Sémantique normative (01-CONTEXT.md D-05) :
 * - tant qu'aucun choix explicite n'existe, le thème suit le système ;
 * - le premier tap choisit le mode explicite OPPOSÉ au thème effectif du système ;
 * - ensuite LIGHT <-> DARK.
 */
class ThemeModeTest {

    @Test
    fun `valueOf sur le nom fait un aller-retour exact pour les 3 valeurs`() {
        for (mode in listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)) {
            assertEquals(mode, ThemeMode.valueOf(mode.name))
        }
    }

    @Test
    fun `SYSTEM avec système sombre bascule vers LIGHT`() {
        assertEquals(
            ThemeMode.LIGHT,
            ThemeMode.nextExplicitMode(ThemeMode.SYSTEM, systemInDarkTheme = true),
        )
    }

    @Test
    fun `SYSTEM avec système clair bascule vers DARK`() {
        assertEquals(
            ThemeMode.DARK,
            ThemeMode.nextExplicitMode(ThemeMode.SYSTEM, systemInDarkTheme = false),
        )
    }

    @Test
    fun `LIGHT bascule vers DARK quel que soit le système`() {
        assertEquals(
            ThemeMode.DARK,
            ThemeMode.nextExplicitMode(ThemeMode.LIGHT, systemInDarkTheme = true),
        )
        assertEquals(
            ThemeMode.DARK,
            ThemeMode.nextExplicitMode(ThemeMode.LIGHT, systemInDarkTheme = false),
        )
    }

    @Test
    fun `DARK bascule vers LIGHT quel que soit le système`() {
        assertEquals(
            ThemeMode.LIGHT,
            ThemeMode.nextExplicitMode(ThemeMode.DARK, systemInDarkTheme = true),
        )
        assertEquals(
            ThemeMode.LIGHT,
            ThemeMode.nextExplicitMode(ThemeMode.DARK, systemInDarkTheme = false),
        )
    }
}
