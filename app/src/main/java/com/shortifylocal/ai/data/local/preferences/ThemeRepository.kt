package com.shortifylocal.ai.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shortifylocal.ai.presentation.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore « settings » unique de l'app (préférences non sensibles — T-01-05). */
private val Context.themeDataStore by preferencesDataStore(name = "settings")

/** Le NOM de l'enum est stocké ; clé absente = suit le système (SYSTEM, D-05). */
val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/**
 * Source de vérité persistée du thème (D-05) — DataStore « settings » uniquement.
 * La préférence thème ne va JAMAIS dans Room ni dans le stockage chiffré
 * (non sensible ; le coffre-fort est une décision Phase 3, SEC-01).
 */
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Émet le mode persisté ; absent = SYSTEM (suit le système, D-05). */
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { value -> ThemeMode.valueOf(value) } ?: ThemeMode.SYSTEM
    }

    /** Écriture atomique du choix explicite via edit { }. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }
}
