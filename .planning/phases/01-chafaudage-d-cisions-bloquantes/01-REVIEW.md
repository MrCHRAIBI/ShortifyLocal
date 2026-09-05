---
phase: 01-chafaudage-d-cisions-bloquantes
reviewed: 2026-09-05T21:52:33Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - app/build.gradle.kts
  - gradle/libs.versions.toml
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/shortifylocal/ai/ShortifyLocalApp.kt
  - app/src/main/java/com/shortifylocal/ai/MainActivity.kt
  - app/src/main/java/com/shortifylocal/ai/data/local/preferences/ThemeRepository.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/ThemeMode.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/ThemeViewModel.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/Color.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/Shape.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/Shadow.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/theme/Theme.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/navigation/AppShell.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/ui/home/HomeScreen.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/ui/history/HistoryScreen.kt
  - app/src/main/java/com/shortifylocal/ai/presentation/ui/settings/SettingsScreen.kt
  - app/src/test/java/com/shortifylocal/ai/presentation/theme/ThemeModeTest.kt
  - scripts/verify_p1.sh
  - scripts/verify_emulator_p1.sh
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values/themes.xml
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-09-05T21:52:33Z
**Depth:** standard
**Files Reviewed:** 21 (19 from the workflow list + strings.xml + themes.xml for cross-referencing; `presentation/theme/ThemeRepository.kt` does not exist — no duplicate of the data-layer repository, confirmed)
**Status:** issues_found

## Summary

Phase 01 scaffolding is coherent: the Gradle socle pins every version in a single catalog, the Clean Architecture tree is in place with an inert (intentionally deferred) Room slice, the D-05 theme toggle chain (`SettingsScreen → ThemeViewModel → ThemeRepository → DataStore → StateFlow → recomposition`) is correctly wired end-to-end, and `ThemeMode.nextExplicitMode` is a pure, fully unit-tested function. Cross-references all resolve: the 6 string resources, `Theme.ShortifyLocal` (values + values-night), mipmap icons, and the WorkManager on-demand init pair (`Configuration.Provider` property override + `tools:node="remove"` in the manifest) are consistent. Manifest hygiene is good (zero permissions, no cleartext, `allowBackup=false`, HTTPS-only by default). No critical issues found.

Four warnings remain. The most visible: `ShortifyLocalTheme` maps only a subset of the Material 3 slots, so the `NavigationBarItem` selected-indicator renders with the baseline M3 `secondaryContainer` (stock purple) — a leak of non-normative colors into the shipped P1 shell, contradicting the "hex values are NORMATIVE" rule of `Color.kt`. Two robustness gaps sit on the theme persistence path (startup crash on unparseable persisted value; toggle TOCTOU on the StateFlow snapshot). One edge-to-edge defect: on Android 15+ (targetSdk 36) the status-bar icon appearance follows the *system* configuration, not the app's persisted dark theme, giving dark-on-dark status bar icons after toggling to dark while the OS is light.

Out of scope per owner instruction, not re-flagged: the intentionally empty Home/History tab screens (SPEC decision, resolved Phase 6), the inert Room/Hilt-Work build wiring (Phase 3 deferral), the bundled ffmpeg-kit AAR (E2 catalog decision, documented), and the inline foojay version in `settings.gradle.kts` (documented exception).

## Warnings

### WR-01: Startup crash on unparseable persisted theme value — no fallback in `ThemeMode.valueOf`, no catch on DataStore read

**File:** `app/src/main/java/com/shortifylocal/ai/data/local/preferences/ThemeRepository.kt:31-33`
**Issue:** `ThemeMode.valueOf(value)` throws `IllegalArgumentException` for any stored string that is not an exact enum constant name. The value is persisted in `settings.preferences_pb` and survives app updates, so a future rename of an enum constant (a routine refactor) or a corrupted/hand-edited DataStore file makes every `themeMode` emission throw. The exception propagates into `stateIn(viewModelScope)` in `ThemeViewModel` and crashes the app at startup. Secondary cause on the same flow: DataStore's `data` flow emits `IOException` if the preferences file is unreadable (disk corruption, partial write from a force-kill — and the P1 emulator script force-stops the app), and nothing catches it.
**Fix:**
```kotlin
val themeMode: Flow<ThemeMode> = context.themeDataStore.data
    .catch { emit(emptyPreferences()) } // fichier illisible -> repartir sur SYSTEM (logguer)
    .map { prefs ->
        prefs[THEME_MODE_KEY]
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
            ?: ThemeMode.SYSTEM
    }
```

### WR-02: Theme toggle reads a stale StateFlow snapshot — first tap after cold start can be a silent no-op; rapid double-tap collapses to one toggle

**File:** `app/src/main/java/com/shortifylocal/ai/presentation/theme/ThemeViewModel.kt:33-39`
**Issue:** `toggleTheme` computes the next mode from `themeMode.value`, the `StateFlow` snapshot. Two race windows:
1. The flow is seeded with `initialValue = ThemeMode.SYSTEM` (line 25) and the real persisted value arrives asynchronously from disk. If a persisted explicit mode (e.g. DARK) exists and the user taps before the first DataStore emission lands, `nextExplicitMode(SYSTEM, systemDark)` is computed from the wrong current mode — with system in light mode it re-writes DARK, so the tap does nothing.
2. Two taps in quick succession both read the same pre-write value (LIGHT), so both compute DARK and the second write is a no-op instead of toggling back.
DataStore's `edit { }` is serialized, so the correct fix is to derive the current mode inside the write, making the toggle atomic.
**Fix (repository-side, atomic):**
```kotlin
// ThemeRepository
suspend fun toggleThemeMode(systemInDarkTheme: Boolean) {
    context.themeDataStore.edit { prefs ->
        val current = prefs[THEME_MODE_KEY]
            ?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } }
            ?: ThemeMode.SYSTEM
        prefs[THEME_MODE_KEY] = ThemeMode.nextExplicitMode(current, systemInDarkTheme).name
    }
}
// ThemeViewModel.toggleTheme -> themeRepository.toggleThemeMode(systemInDarkTheme)
```
(Minimal variant: `val current = themeRepository.themeMode.first()` inside the `launch` before `setThemeMode`.)

### WR-03: Incomplete M3 slot mapping — baseline purple leaks into the shipped shell (NavigationBarItem selected indicator)

**File:** `app/src/main/java/com/shortifylocal/ai/presentation/theme/Theme.kt:45-57`
**Issue:** `colorScheme` is built from `darkColorScheme()`/`lightColorScheme()` baselines and only overrides background/surface/surfaceContainer/onBackground/onSurface/onSurfaceVariant/primary/onPrimary/secondary/onSecondary. Every unmapped slot keeps the stock Material purple palette. The user-visible leak in the P1 shell: `NavigationBarItem`'s selected-indicator pill defaults to `MaterialTheme.colorScheme.secondaryContainer`, which is baseline lavender (`#E8DEF8` light / `#4A4458` dark) — a color that is not in the normative Soft-Clean token set and clashes with the accent-orange token the rest of the shell uses. `primaryContainer`, `surfaceVariant` (future TextFields), `surfaceContainerHigh` (future menus/dialogs), `inverseSurface` (future snackbars) carry the same latent risk into Phases 4-6.
**Fix:** map the interactive-container slots from existing tokens (no new hex without an amendement):
```kotlin
val colorScheme = base.copy(
    // ...existing mappings...
    secondaryContainer = card,          // pilule d'onglet actif = surface carte
    onSecondaryContainer = primaryText,
    primaryContainer = card,
    onPrimaryContainer = primaryText,
)
```
or add dedicated normative tokens in `Color.kts` via a spec amendement.

### WR-04: Status-bar icon appearance follows the system theme, not the app theme — dark-on-dark icons under enforced edge-to-edge (targetSdk 36)

**File:** `app/src/main/java/com/shortifylocal/ai/MainActivity.kt:21-33` (with `app/src/main/res/values/themes.xml:4` / `values-night/themes.xml:3`)
**Issue:** With `targetSdk = 36`, edge-to-edge is enforced on Android 15+: system bars are transparent, the Compose background draws behind the status bar, and system-bar icon contrast derives from the window theme's `windowLightStatusBar` attribute — which resolves through `values-night`, i.e. the *system* uiMode configuration. When the user persists DARK in-app while the OS stays in light mode (exactly the scenario the D-05 toggle creates), the framework keeps the light theme variant → dark status-bar icons over the app's `#0E0E10` background → illegible icons. The reverse mismatch occurs for persisted LIGHT while the OS is dark. `enableEdgeToEdge()` is not called, and no code drives the insets appearance from the collected `ThemeMode`.
**Fix:** in `MainActivity`, drive the system-bar appearance from the effective dark flag:
```kotlin
val view = LocalView.current
LaunchedEffect(dark) {
    val window = (view.context as Activity).window
    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
}
```
(or call `enableEdgeToEdge(statusBarStyle = ..., navigationBarStyle = ...)` keyed on `dark`). Add a P1 emulator check toggling the app theme while the OS stays light.

## Info

### IN-01: `softShadow` declares a defaulted parameter before a required one

**File:** `app/src/main/java/com/shortifylocal/ai/presentation/theme/Shadow.kt:18-20`
**Issue:** `fun Modifier.softShadow(shape: Shape = RoundedCornerShape(28.dp), darkTheme: Boolean)` — `darkTheme` is required but positioned after a defaulted parameter, so positional callers must always pass `shape` too; `softShadow(true)` does not compile. A compile-time trap rather than a runtime bug, but it will annoy every Phase 6 consumer of this API.
**Fix:** reorder to `fun Modifier.softShadow(darkTheme: Boolean, shape: Shape = RoundedCornerShape(28.dp))` and update the single call site (`AppShell.kt:72-75`) to use positional/named args accordingly.

### IN-02: Emulator verification script never cleans up the emulator it starts

**File:** `scripts/verify_emulator_p1.sh:55-66`
**Issue:** When the script auto-starts an AVD (`"$EMU_BIN" -avd "$AVD" > build/emu.log 2>&1 &`), the background process is left running after both success and failure exits — there is no `trap`/cleanup. On a repeated-run CI box or a developer machine this silently accumulates emulator processes (each holding the AVD lock, preventing a subsequent clean start).
**Fix:** record `EMULATOR_PID=$!`, and add `trap '[ -n "${EMULATOR_PID:-}" ] && kill "$EMULATOR_PID" 2>/dev/null' EXIT` — or document explicitly that a started emulator is intentionally left running for iteration and skip cleanup on success only.

### IN-03: `verify_p1.sh` checksum probe takes the first cache hit; version checks are substring-weak

**File:** `scripts/verify_p1.sh:100-103, 113-115`
**Issue:** (a) `find ... -name 'ffmpeg-kit-full-gpl-8.1.7.aar' | head -1` verifies whichever duplicate appears first in the Gradle cache; a stale or partially-downloaded copy in a second hash directory can pass while the build resolves a different one (or fail while the resolved one is fine). (b) The E2 catalog check (section 5) greps bare version substrings (`"1.4.0"`, `"2.11.2"`) anywhere in the TOML, so a version bump that leaves the old number in a comment or a different coordinate line would still pass.
**Fix:** (a) verify all matches: `find ... -print0 | xargs -0 -n1 sha256sum` and require every hit to equal `CHECKSUM_ATTENDU`, or pin the exact `files-2.1/dev.ffmpegkit-maintained/ffmpeg-kit-full-gpl/8.1.7/<sha1-of-pom>/` path. (b) anchor the greps to the TOML entry lines, e.g. `check_present "..." '^ffmpegKit = "8.1.7"' "$TOML"` (switch `check_present` to support regex).

---

_Non-findings verified during review (no action needed):_ all string/theme/mipmap resource references resolve; WorkManager on-demand init pairing is correct for 2.11.x; the `darkNow` capture in `AppShell.kt:104-110` correctly re-reads the CompositionLocal in composition; passing effective-dark as `systemInDarkTheme` is behaviorally equivalent under `nextExplicitMode`; `ThemeModeTest` covers every branch of the toggle semantics.

_Reviewed: 2026-09-05T21:52:33Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
