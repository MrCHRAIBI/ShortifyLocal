---
phase: 01-chafaudage-d-cisions-bloquantes
plan: 03
subsystem: ui
tags: [compose, material3, navigation, theme, mvvm, rtl-ready]

requires:
  - 01-02 (ThemeMode/nextExplicitMode D-05, ThemeRepository DataStore « settings », ThemeViewModel @HiltViewModel)
  - 01-01 (socle build : BOM Compose, material-icons-extended, hilt-navigation-compose, lifecycle-runtime-compose)
provides:
  - SoftCleanTokens (14 tokens normatifs docs/02 §1.1–§1.2, clair/sombre)
  - SoftCleanShapes (rayons 16/16/24/28/32 sur slots M3, D-06)
  - Modifier.softShadow (ombre unique verticale 5 % clair / 30 % sombre, Option A research)
  - ShortifyLocalTheme + LocalSoftCleanDark (when D-05 exact, mapping slots M3)
  - AppShell (routes accueil/historique/parametres, NavigationBar launchSingleTop/restoreState/saveState)
  - HomeScreen / HistoryScreen / SettingsScreen (coquilles vides P1, libellés ressources)
  - MainActivity câblé (@AndroidEntryPoint, hiltViewModel, collectAsStateWithLifecycle)
  - ressources strings.xml : tab_accueil, tab_historique, tab_parametres, settings_appearance, theme_toggle_cd
affects: [01-04 (install/launch + persistance sur cibles), Phase 6 (BottomNavPill + écrans réels), Phase 6 typographie]

actuals:
  tokens: 3600 # chars/4 sur le diff réel 01-03 (415 insertions / 5 deletions, 10 fichiers) — estimation plan 40000 très au-dessus du réel
  tasks: 2
  commits: 3 # 2 production + 1 metadata docs

tech-stack:
  added: [] # aucune nouvelle dépendance — catalogue E2 du plan 01-01 uniquement
  patterns:
    - Thème piloté par tokens : SoftCleanTokens -> base.copy() sur les slots M3 (D-06)
    - CompositionLocal LocalSoftCleanDark exposé par le thème, lu en contexte composable (capture avant callback)
    - NavigationBar + NavHost routes string avec launchSingleTop/restoreState/popUpTo(saveState)
    - MVVM shell : hiltViewModel() + collectAsStateWithLifecycle dans setContent

key-files:
  created:
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/Color.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/Shape.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/Shadow.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/Theme.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/navigation/AppShell.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/ui/home/HomeScreen.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/ui/history/HistoryScreen.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/ui/settings/SettingsScreen.kt
  modified:
    - app/src/main/java/com/shortifylocal/ai/MainActivity.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "softShadow = Option A du research (Modifier.shadow, elevation 6.dp, couleurs token) — Option B drawBehind reste le recours si la revue visuelle 01-04 juge l'approximation insuffisante"
  - "@AndroidEntryPoint ajouté à MainActivity (omis dans le sketch du plan) — requis par hiltViewModel() sans quoi le @HiltViewModel ne se résout pas au runtime"
  - "Libellé de bascule = theme_toggle_cd utilisé aussi comme contentDescription (pas de ressource dédiée par mode en P1 — i18n complet Phase 7)"

patterns-established:
  - "Pattern: tout libellé UI passe par strings.xml ; alignements Start/End/Center uniquement (greps d'acceptation = 0)"
  - "Pattern: thème Soft-Clean construit depuis tokens normatifs — les hex de docs/02 §1.1 restent la seule source de vérité couleur"
  - "Pattern: lecture de CompositionLocal TOUJOURS en contexte @Composable, jamais dans un callback lambda (capture avant passage)"

requirements-completed: [PROJ-01]

coverage:
  - deliverable: SoftCleanTokens (14 tokens normatifs §1.1–§1.2)
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "Task 1 <verify> : grep 9 hex normatifs présents + alpha 0.05f/0.30f + assembleDebug vert"
        status: pass
    human_judgment: false
  - deliverable: SoftCleanShapes (rayons 32/24/16/28 sur slots M3)
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "Task 1 <verify> : grep RoundedCornerShape count=7 (>=4) + assembleDebug vert"
        status: pass
    human_judgment: false
  - deliverable: softShadow — ombre unique à couleur token (anti-neumorphisme T-01-09)
    verification:
      - kind: other
        ref: "assembleDebug vert ; UNE seule invocation shadow() à couleurs SoftCleanTokens (revue de code)"
        status: pass
    human_judgment: true
    rationale: "La fidélité visuelle de l'ombre (approximation elevation 6.dp vs blur 24/16 offset (0,8)/(0,6)) est un critère de jugement — revue visuelle humaine planifiée au 01-04 contre docs/02 §0/§1.2."
  - deliverable: ShortifyLocalTheme (sémantique D-05 + LocalSoftCleanDark + mapping slots M3)
    verification:
      - kind: other
        ref: "assembleDebug vert (compilation du when D-05 et du colorScheme) ; when revu conforme verbatim D-05"
        status: pass
    human_judgment: false
  - deliverable: AppShell — 3 onglets navigables + navigation state-preserving
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "assembleDebug vert ; greps Left/Right=0 et littéraux UI=0 ; routes accueil/historique/parametres revues avec launchSingleTop/restoreState/saveState"
        status: pass
    human_judgment: true
    rationale: "Le lancement réel de l'APK et la navigation tactile sont prouvés au plan 01-04 (install/launch sur cibles) — hors périmètre automatisable de 01-03."
  - deliverable: Bascule thème câblée bout-en-bout (SettingsScreen -> vm.toggleTheme -> DataStore -> recomposition)
    verification:
      - kind: other
        ref: "assembleDebug vert (graphe Hilt valide avec @AndroidEntryPoint) ; chemin revu sans maillon manquant"
        status: pass
    human_judgment: true
    rationale: "La persistance réelle (survit au force-stop) sera prouvée par le scénario adb du plan 01-04 (SPEC R3) — cohérent avec le découpage des plans."
  - deliverable: Ressources strings (5 clés) + structure RTL-ready
    verification:
      - kind: other
        ref: "Task 2 <verify> : grep name= tab_accueil/tab_historique/tab_parametres/theme_toggle_cd présents ; supportsRtl=true (manifest 01-01)"
        status: pass
    human_judgment: false

duration: 14 min
completed: 2026-09-05
status: complete
---

# Phase 01 Plan 03: Coquille Compose thémée Soft-Clean Summary

Coquille Compose Material 3 Soft-Clean : 14 tokens normatifs (docs/02 §1.1–§1.3) mappés sur les slots M3, navigation 3 onglets (Accueil/Historique/Paramètres) à état conservé, et bascule de thème câblée bout-en-bout SettingsScreen → ThemeViewModel → DataStore → recomposition — `assembleDebug` vert, greps prohibitions (littéraux UI, Left/Right) à 0.

## Performance
- **Duration :** 14 min (20:54 – 21:08 UTC, 2026-09-05)
- **Started :** 2026-09-05T20:54:47Z
- **Completed :** 2026-09-05T21:08:56Z
- **Tasks :** 2/2
- **Files modified :** 10 (8 créés, 2 modifiés)

## Accomplishments
- **Task 1** (`86629f9`) — Design system Soft-Clean en thème : `SoftCleanTokens` (14 tokens normatifs exacts, ombre 5 %/30 %), `SoftCleanShapes` (16/16/24/28/32 → slots M3, D-06), `Modifier.softShadow` (Option A : `Modifier.shadow` elevation 6.dp, couleurs token, `clip = false`), `ShortifyLocalTheme` (when D-05 verbatim + `base.copy()` des slots background/surface/surfaceContainer/onBackground/onSurface/onSurfaceVariant/primary/onPrimary/secondary/onSecondary + `LocalSoftCleanDark`).
- **Task 2** (`aa24a60`) — Walking skeleton UI : `AppShell` (NavigationBar fond surfaceContainer + softShadow rayon 28, routes `accueil`/`historique`/`parametres`, navigation `launchSingleTop`/`restoreState`/`popUpTo(saveState)`), 3 écrans coquilles vides assumées P1, `SettingsScreen` avec bascule (icône BrightnessAuto/LightMode/DarkMode selon mode), `MainActivity` réécrit (`@AndroidEntryPoint` + `hiltViewModel()` + `collectAsStateWithLifecycle`), 5 clés strings.xml ajoutées.
- **Tracer feedback gate** : vérification Task 2 relancée après fix — `assembleDebug` vert + 3 greps d'acceptation à 0/présents. ⚡ Tracer verified end-to-end.
- **SPEC R2/R3** : l'APK lancé affichera les 3 onglets thémés clair/sombre avec bascule persistée (proub install/launch + persistance au plan 01-04, conformément au découpage).

## Verification
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (final : 11s, 41 tasks ; exécutions t1/t2/t2b).
- Grep 9 hex normatifs dans Color.kt : 0 MISSING ; alphas 0.05f/0.30f présents.
- Grep RoundedCornerShape Shape.kt : 7 (≥ 4).
- Grep `Alignment.(Left|Right)|TextAlign.(Left|Right)` : 0.
- Grep `(text|label|contentDescription) = "` dans presentation/ : 0.
- Ressources `tab_accueil`/`tab_historique`/`tab_parametres`/`theme_toggle_cd` : toutes présentes.

## Task Commits
1. **Task 1** - `86629f9` (feat) — tokens Soft-Clean mappés Material 3 (D-05/D-06)
2. **Task 2** - `aa24a60` (feat) — coquille 3 onglets + bascule thème câblée DataStore
**Plan metadata:** docs commit (voir § suivant)

## Files Created/Modified
- Créés : Color.kt, Shape.kt, Shadow.kt, Theme.kt, AppShell.kt, HomeScreen.kt, HistoryScreen.kt, SettingsScreen.kt
- Modifiés : MainActivity.kt (réécrit + `@AndroidEntryPoint`), strings.xml (5 clés)

## Decisions Made
- `softShadow` = Option A du research (`Modifier.shadow`, elevation 6.dp) — conforme à l'autorisation docs/02 §1.2 ; Option B (`drawBehind`) reste le recours sanctionné si la revue visuelle 01-04 trouve l'approximation insuffisante.
- `@AndroidEntryPoint` sur `MainActivity` : requis par `hiltViewModel()` (voir Deviations).
- `theme_toggle_cd` réutilisé comme libellé visible ET description d'accessibilité (pas de doubling de ressources en P1 ; i18n 7 langues Phase 7).

## Deviations from Plan

1. **[Rule 1 - Bug] Lecture de CompositionLocal dans un callback non composable (AppShell.kt:106)**
   - **Found during:** Task 2 (première passe `assembleDebug` — BUILD FAILED, `@Composable invocations can only happen from the context of a @Composable function`).
   - **Issue:** le sketch du plan place `LocalSoftCleanDark.current` dans le lambda `onThemeToggle = { ... }` (callback onClick), qui n'est pas un contexte composable — non compilable.
   - **Fix:** capture `val darkNow = LocalSoftCleanDark.current` dans le contenu composable de la destination `parametres`, puis passage de la capture au callback. Sémantique identique (état effectif courant).
   - **Files modified:** app/src/main/java/com/shortifylocal/ai/presentation/navigation/AppShell.kt
   - **Verification:** `assembleDebug` BUILD SUCCESSFUL en relance.
   - **Commit:** `aa24a60`

2. **[Rule 2 - Missing critical] `@AndroidEntryPoint` manquant sur MainActivity**
   - **Found during:** Task 2 (implémentation).
   - **Issue:** le sketch MainActivity du plan omet l'annotation ; `hiltViewModel()` exige une Activity `@AndroidEntryPoint`, sinon le `@HiltViewModel` ThemeViewModel ne se résout pas (crash runtime « Cannot create an instance ») — le chemin de bascule D-05 serait cassé à l'exécution.
   - **Fix:** `@AndroidEntryPoint` ajouté (chaîne Hilt déjà en place : `@HiltAndroidApp` du plan 01-01).
   - **Files modified:** app/src/main/java/com/shortifylocal/ai/MainActivity.kt
   - **Verification:** graphe Hilt valide — `assembleDebug` vert.
   - **Commit:** `aa24a60`

**Total deviations:** 2 auto-fixed (1 Rule 1 bug compilation, 1 Rule 2 annotation requise). **Impact:** aucune dérive de périmètre — les deux corrections étaient des prérequis mécaniques du chemin prescrit ; le comportement livre est exactement celui du plan.

## Issues Encountered
Première passe `assembleDebug` Task 2 en échec (deviation 1) — résolu en une itération, sans dépasser le budget de fix.

## Known Stubs
Écrans volontairement vides — décision SPEC assumée P1, contenu réel Phase 6 :
| Fichier | Description | Résolution |
|---|---|---|
| HomeScreen.kt | Box centrée avec libellé d'onglet (aucun contenu réel) | Phase 6 (écran Accueil réel, docs/02 §4) |
| HistoryScreen.kt | idem | Phase 6 (grille historique, docs/02 §6) |
| SettingsScreen.kt | seule la tuile Apparence/bascule existe | Phase 6 (tuiles complètes, docs/02 §7) |

Aucun stub accidentel : la bascule de thème, elle, est câblée au réel (DataStore lu/écrit).

## User Setup Required
None - no external service configuration required.

## Authentication Gates
Aucun.

## Next Phase Readiness
Ready for 01-04 — la coquille livre tout ce que 01-04 doit prouver sur cibles : APK compilable (assembleDebug vert), 3 onglets navigables, bascule câblée bout-en-bout à vérifier en persistance (`am force-stop` → relaunch). Points d'attention 01-04 : revue visuelle anti-neumorphisme (T-01-09, docs/02 §0/§1.2) et vérification humaine appareil physique ARM64 (D-03, fin de phase).

## Self-Check: PASSED

- 8 fichiers créés + 2 modifiés vérifiés présents sur disque.
- Commits vérifiés dans l'historique : `86629f9` (Task 1), `aa24a60` (Task 2).
- `assembleDebug` final vert (log build/gradle-asm-t2b.log : BUILD SUCCESSFUL).
