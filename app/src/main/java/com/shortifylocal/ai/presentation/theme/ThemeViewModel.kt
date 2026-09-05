package com.shortifylocal.ai.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shortifylocal.ai.data.local.preferences.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Expose la préférence thème persistée (D-05) à la coquille Compose (consommée au plan 01-03).
 * Aucune logique ici : la bascule délègue à ThemeMode.nextExplicitMode (pur) et à ThemeRepository.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    /**
     * Bascule D-05 : premier tap = mode explicite opposé au thème effectif
     * (système sombre -> LIGHT, système clair -> DARK), puis LIGHT <-> DARK.
     * L'écriture DataStore est atomique (edit { }).
     */
    fun toggleTheme(systemInDarkTheme: Boolean) {
        viewModelScope.launch {
            themeRepository.setThemeMode(
                ThemeMode.nextExplicitMode(themeMode.value, systemInDarkTheme),
            )
        }
    }
}
