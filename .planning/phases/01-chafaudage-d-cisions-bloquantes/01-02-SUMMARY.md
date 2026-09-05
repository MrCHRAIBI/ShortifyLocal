---
phase: 01-chafaudage-d-cisions-bloquantes
plan: 02
subsystem: app-scaffolding
tags: [clean-architecture, datastore, theme, hilt, mvvm, tdd]
requires:
  - 01-01 (socle build : catalogue E2 épinglé, plugins hilt/ksp/room câblés, datastore-preferences 1.2.1)
provides:
  - arborescence Clean Architecture normative (domain/{model,repository,usecase}, data/repository, worker, util — .gitkeep)
  - ThemeMode (enum + nextExplicitMode pur, sémantique D-05)
  - ThemeRepository (DataStore « settings », clé theme_mode, absent = SYSTEM)
  - ThemeViewModel (@HiltViewModel, StateFlow<ThemeMode>, toggleTheme)
affects:
  - 01-03 (coquille visuelle consomme ThemeViewModel)
  - Phase 3 DATA-01 (tranche Room reportée ici — décision propriétaire B)
tech-stack:
  added: [] # aucune nouvelle dépendance — catalogue E2 du plan 01-01 uniquement
  patterns:
    - DataStore Preferences 1.2.1 (Pattern 7 : preferencesDataStore délégué top-level, edit { } atomique)
    - MVVM Hilt : @HiltViewModel injecté par constructeur, StateFlow stateIn(WhileSubscribed(5_000))
    - Logique de bascule pure dans le companion de l'enum (testable sans Android)
key-files:
  created:
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/ThemeMode.kt
    - app/src/main/java/com/shortifylocal/ai/data/local/preferences/ThemeRepository.kt
    - app/src/main/java/com/shortifylocal/ai/presentation/theme/ThemeViewModel.kt
    - app/src/test/java/com/shortifylocal/ai/presentation/theme/ThemeModeTest.kt
    - app/src/main/java/com/shortifylocal/ai/domain/model/.gitkeep
    - app/src/main/java/com/shortifylocal/ai/domain/repository/.gitkeep
    - app/src/main/java/com/shortifylocal/ai/domain/usecase/.gitkeep
    - app/src/main/java/com/shortifylocal/ai/data/repository/.gitkeep
    - app/src/main/java/com/shortifylocal/ai/worker/.gitkeep
    - app/src/main/java/com/shortifylocal/ai/util/.gitkeep
  modified: []
key-decisions:
  - "Report de la tranche Room v1 en Phase 3 (DATA-01) — décision propriétaire B du 2026-09-05 (Room 2.8.4 rejette un @Database sans entités ; amend commit 0696d5b)"
  - "Préférence thème exclusivement en DataStore « settings » (non sensible, T-01-05) — jamais Room ni stockage chiffré"
patterns-established:
  - "Sémantique D-05 : absent en DataStore = suit le système ; premier tap = mode explicite opposé au thème effectif ; puis LIGHT <-> DARK"
  - "Purity domain : aucun import android.*/androidx.* dans domain/ (barre posée dès P1, grep d'acceptation)"
requirements-completed: [PROJ-01]
coverage:
  - deliverable: Arborescence Clean Architecture (6 packages .gitkeep)
    kind: verify
    ref: "Task 1 <verify> : presence .gitkeep x6 + purity greps + assembleDebug"
    status: pass
    human_judgment: false
  - deliverable: ThemeMode.nextExplicitMode + aller-retour enum (D-05)
    kind: test
    ref: "app/src/test/java/com/shortifylocal/ai/presentation/theme/ThemeModeTest.kt (5 tests, 0 failures)"
    status: pass
    human_judgment: false
  - deliverable: ThemeRepository (DataStore read/write theme_mode)
    kind: verify
    ref: "assembleDebug vert (compile dans le graphe Hilt) + inspection — round-trip runtime DataStore non instrumenté"
    status: pass
    human_judgment: true
    rationale: "Le <verify> du plan définit assembleDebug comme preuve d'acceptation ; le comportement runtime du DataStore n'est couvert par aucun test instrumenté (hors périmètre P1, cohérent avec le plan)."
  - deliverable: ThemeViewModel (@HiltViewModel, StateFlow, toggleTheme)
    kind: verify
    ref: "assembleDebug vert (graphe Hilt valide) — consommation UI reportée à 01-03"
    status: pass
    human_judgment: true
    rationale: "Compile et s'injecte correctement (preuve build) ; le comportement observé à l'écran sera vérifié à la coquille visuelle (01-03)."
duration: 6 min (session de récupération ; plan étalé sur 3 sessions exécuteur)
completed: 2026-09-05
status: complete
actuals:
  tokens: 1500 # chars/4 sur les 4 fichiers Kotlin réellement écrits (5908 chars) — estimation plan 40000 très au-dessus du réel
  tasks: 2
  commits: 4
---

# Phase 01 Plan 02: Plomberie Clean Architecture + persistance thème DataStore Summary

Arborescence Clean Architecture normative (6 packages `.gitkeep`) + thème D-05 réellement persisté dans DataStore « settings » derrière un `ThemeViewModel` `@HiltViewModel` — `ThemeModeTest` 5/5 vert, `assembleDebug` vert, slice Room reporté Phase 3 (décision propriétaire B).

## Livrables

- **Task 1** — Arborescence Clean Architecture (commit `6ca031e`) : `domain/{model,repository,usecase}`, `data/repository`, `worker/`, `util/` avec `.gitkeep`. Amend préalable `0696d5b` : la tranche Room v1 (`AppDatabase` sans entités + `DatabaseModule` + schéma exporté) est reprogrammée en Phase 3 (DATA-01) — Room 2.8.4 refuse un `@Database` sans entités, aucune variante jetable acceptée. Le câblage build Room du plan 01-01 reste en place, inerte.
- **Task 2** — Persistance thème D-05 (RED `daeae12`, GREEN `82b05d6`) :
  - `ThemeMode.kt` : enum `SYSTEM/LIGHT/DARK` + `nextExplicitMode(current, systemInDarkTheme)` pur en companion (SYSTEM+sombre→LIGHT, SYSTEM+clair→DARK, LIGHT↔DARK).
  - `ThemeRepository.kt` : DataStore « settings » (`preferencesDataStore` délégué), clé `theme_mode` (nom de l'enum), lecture map absent→SYSTEM, écriture atomique `edit { }`.
  - `ThemeViewModel.kt` : `@HiltViewModel`, `StateFlow<ThemeMode>` via `stateIn(WhileSubscribed(5_000), SYSTEM)`, `toggleTheme` lançant l'écriture dans `viewModelScope`.

## Verification

- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, `ThemeModeTest` : tests=5, failures=0, errors=0.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (graphe Hilt valide avec `@HiltViewModel` + `@Singleton` repository ; APK produit).
- Purity greps : `RoomDatabase` dans app/src = 0 ; `fallbackToDestructiveMigration` dans app/ = 0 ; imports android/androidx dans domain/ = 0 ; 6/6 `.gitkeep` présents.

## Deviations from Plan

1. **[Plan amendment — décision propriétaire B, 2026-09-05]** Slice Room reportée en Phase 3 (DATA-01). Consigné dans le plan amendé (commit `0696d5b`) avant exécution — non une deviation d'exécution mais un changement de périmètre approuvé.
2. **[Recovery]** Les deux premières sessions exécuteur ont été interrompues par le harnais (kill après 10 min sans activité — builds Gradle à froid silencieux). Tentative 3 : pattern background + polling (`gradlew ... > log &` puis `tail` toutes les 30 s), commits au fil des gates. Aucun travail perdu : le WIP sur disque des sessions mortes était complet et conforme au plan ; vérifié (tests 5/5, assembleDebug vert) puis commité comme GREEN.

Aucun auto-fix Rules 1-3 nécessaire — l'implémentation laissée sur disque était déjà conforme au `<action>` du plan.

## Auth Gates

Aucun.

## Known Stubs

Aucun. (Tous les livrables sont câblés au réel : DataStore réellement lu/écrit ; les packages `.gitkeep` sont des marqueurs d'arborescence voulus, pas des stubs.)

## Threat Flags

Aucun nouveau surface — DataStore « settings » est couvert par T-01-05 (accept, préférence non sensible) du `<threat_model>` du plan ; aucune dépendance ajoutée (T-01-SC inchangé).

## Next

Ready for 01-03 (coquille visuelle Compose consommant `ThemeViewModel`).

## Self-Check: PASSED

- Fichiers créés vérifiés présents sur disque (3 impl + 1 test + 6 .gitkeep).
- Commits vérifiés dans l'historique : `0696d5b`, `6ca031e`, `daeae12`, `82b05d6`.
- TDD gates : RED `test(01-02)` `daeae12` → GREEN `feat(01-02)` `82b05d6` (REFACTOR non nécessaire, aucun changement).
