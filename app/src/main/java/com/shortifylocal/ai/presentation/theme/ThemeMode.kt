package com.shortifylocal.ai.presentation.theme

/**
 * Mode de thème choisi par l'utilisateur (D-05).
 *
 * SYSTEM : aucun choix explicite persisté — le thème suit le système
 * (`isSystemInDarkTheme()` comme valeur initiale, D-05).
 * LIGHT / DARK : choix explicite persisté dans DataStore, prime sur le système.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /**
         * Sémantique D-05 de la bascule : depuis SYSTEM, le premier tap choisit le
         * mode explicite opposé au thème effectif du système ; ensuite LIGHT <-> DARK.
         * Fonction pure, testable sans dépendance Android.
         */
        fun nextExplicitMode(current: ThemeMode, systemInDarkTheme: Boolean): ThemeMode =
            when (current) {
                SYSTEM -> if (systemInDarkTheme) LIGHT else DARK
                LIGHT -> DARK
                DARK -> LIGHT
            }
    }
}
