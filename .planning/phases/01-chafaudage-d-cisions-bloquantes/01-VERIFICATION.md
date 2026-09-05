---
phase: 01-chafaudage-d-cisions-bloquantes
verified: 2026-09-05T22:13:26Z
status: human_needed
score: 9/9 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
mode_discrepancy: >-
  ROADMAP.md declares "Mode: mvp" for this phase but the phase goal is NOT in
  user-story format (user-story.validate: valid=false on all three clauses).
  Per verify-mvp-mode.md this is surfaced as a discrepancy: run
  "/gsd mvp-phase 1" to set a proper User Story goal, or drop mode: mvp.
  The MVP user-story narrowing could not be applied (all-or-nothing rule);
  standard goal-backward verification was executed instead. A user-flow
  coverage table derived from SC2 (the user-visible outcome) is included.
behavior_unverified_items: []
coincidental_reliance_items: []
deferred:
  - truth: "Tranche Room v1 (AppDatabase, DatabaseModule, schéma exporté, DB shortify_local.db)"
    addressed_in: "Phase 3"
    evidence: "ROADMAP SC3 (amendé 2026-09-05) : « la tranche Room v1 (schéma commité) est reprogrammée en Phase 3 (décision propriétaire B du 2026-09-05 : Room 2.8.4 rejette un @Database sans entités) » ; amend commit 0696d5b"
  - truth: "Contenu réel des 3 écrans d'onglets (coquilles vides)"
    addressed_in: "Phase 6"
    evidence: "SC2 exige exprès « navigation à 3 destinations vide » ; WINDOWS ledger : écrans réels (docs/02 §4/§6/§7) en Phase 6"
human_verification:
  - test: "Lég appareil physique ARM64 (D-03/R7) — brancher un téléphone ARM64 Android 13+ en USB (débogage USB), puis : adb devices ; ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh ; cp .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/emulator-shell.png .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/device-shell.png"
    expected: "« ÉMU OK — install + lancement + 3 onglets + bascule + persistance (LIGHT|DARK) prouvés sur <serial> », SANS INSTALL_FAILED_NO_MATCHING_ABIS / INSTALL_FAILED_CPU_ABI_INCOMPATIBLE ; en cas de blocage ABI : STOP, escalade au propriétaire de spec, ne rien changer"
    why_human: "Aucun appareil physique attaché pendant l'exécution (adb devices : émulateur seul) — la moitié arm64 de la double cible ne peut pas être sondée sans le matériel"
  - test: "Revue visuelle anti-neumorphisme + fidélité tokens (interdit judgment-tier) — relire les captures émulateur (screens/emulator-shell.png clair, emulator-after-toggle.png sombre, régénérées par la sonde de vérification) et device-shell.png dès la lég appareil, contre docs/02-UI-UX.md §0/§1.1–1.3 : UNE seule ombre douce verticale par surface (5 % clair / 30 % sombre), aucun relief néumorphique (pas d'ombre double clair+sombre, aucun élément de la couleur du fond en relief), rayons 32/24/16/28, couleurs conformes aux tokens en clair ET en sombre"
    expected: "Aucune ombre double, aucun halo néumorphique, pilule de navigation et surfaces conformes aux tokens. Points connus à disposition : pilule d'onglet sélectionné = secondaryContainer M3 par défaut (violet, WR-03) et icônes de barre d'état suivant le thème système (WR-04) — accepter, amender ou programmer la correction"
    why_human: "La fidélité visuelle est une prohibition judgment-tier (PLAN 01-03/01-04) — le jugement final appartient à l'humain ; la pré-lecture du vérificateur (2 captures relues) n'est pas une preuve"
---

# Phase 1: Échafaudage & décisions bloquantes — Verification Report

**Phase Goal:** Le projet Android natif compile et s'exécute depuis un socle technique verrouillé — dépendances épinglées dans le catalogue unique, artefact ffmpeg-kit mort substitué — et les trois décisions transverses (supply-chain, licence, distribution Play) sont actées avant toute ligne de pipeline.
**Verified:** 2026-09-05T22:13:26Z
**Status:** human_needed (all 9/9 must-haves verified with behavioral evidence; 2 human UAT items remain — physical ARM64 device leg + visual anti-neumorphism review)
**Re-verification:** No — initial verification

## Mode Discrepancy (MVP)

ROADMAP.md declares `Mode: mvp` but the phase goal is not in user-story format
(`gsd_run query user-story.validate` → `valid: false`, three clause errors). Per
`gsd-core/references/verify-mvp-mode.md`, the MVP narrowing applies only to a
user-story goal (all-or-nothing) — it could not be applied. Standard
goal-backward verification was executed; the developer should either run
`/gsd mvp-phase 1` or drop `mode: mvp` for this phase.

## User Flow Coverage (derived from SC2 — the phase's user-visible outcome)

| Step | Expected | Evidence | Status |
|------|----------|----------|--------|
| Open the app on Android 13+ | App installs and launches, no crash | Probe run: `adb install -r` → Success on emulator-5554 (API 36); `am start` → pid 12215 alive, shell displayed | ✓ |
| See the Compose shell with 3-destination empty navigation | Accueil / Historique / Paramètres tabs, empty content | uiautomator dump greps all 3 labels; screenshot `screens/emulator-shell.png` read by verifier: 3 tabs, centered empty label | ✓ |
| Navigate to Paramètres and toggle theme | Theme switches (light↔dark), icon reflects mode | Probe tapped Paramètres then toggle (850,1153); `screens/emulator-after-toggle.png` read by verifier: dark shell, moon icon, Apparence tile | ✓ |
| Outcome: preference survives restart | Force-stop → cold relaunch restores last chosen theme | `am force-stop` → relaunch → pid 12379, `run-as cat files/datastore/settings.preferences_pb` contains DARK; D-05 semantics unit-tested (5/5) | ✓ |

## Goal Achievement

### Observable Truths

Merged from ROADMAP success criteria (contract) + PLAN frontmatter must_haves (4 plans). Deduplicated; roadmap wording kept.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1 — `assembleDebug` produit un APK installable depuis le catalogue unique, versions vérifiées registres (clause de fraîcheur notée) ; artefact mort `com.arthenica:ffmpeg-kit-full-gpl` absent, fork maintenu épinglé (coordonnée + version + checksum consignés) | ✓ VERIFIED | Fresh `./gradlew :app:assembleDebug` exit 0 this session; APK 146 763 997 octets; `gradle/libs.versions.toml` read in full: fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl` 8.1.7 + SHA-256 `374d3734…4658b` consignés en commentaire + en-tête « versions vérifiées contre registres officiels le 2026-09-05 »; grep `com.arthenica` sur fichiers de build = 0 (re-vérifié indépendamment); battery section 7: sha256 de l'AAR cache Gradle = valeur épinglée |
| 2 | SC2 — L'app s'installe et se lance sur appareil/émulateur Android 13+ en affichant la coquille Compose à 3 destinations vide | ✓ VERIFIED | Sonde ré-exécutée par le vérificateur (émulateur en ligne, pas démarré par moi) : install Success, lancement pid 12215, 3 onglets prouvés au dump uiautomator, captures relues. Moitié physique (D-03) → human_verification (SC2 dit « appareil/émulateur » — l'émulateur satisfait la lettre) |
| 3 | SC3 — Structure Clean Architecture (domain/data/ui) en place | ✓ VERIFIED | 6/6 packages `.gitkeep` présents (domain/{model,repository,usecase}, data/repository, worker, util) + `data/local/preferences`, `presentation/{theme,navigation,ui.*}`; purity grep `^import (android|androidx)\.` dans domain/ = 0 |
| 4 | SC3 — WorkManager + Hilt initialisés | ✓ VERIFIED | `ShortifyLocalApp` : `@HiltAndroidApp` + `Configuration.Provider` (propriété Kotlin `workManagerConfiguration`, forme 2.11.2), `@Inject HiltWorkerFactory`; manifest retire `WorkManagerInitializer` (init on-demand); `MainActivity` `@AndroidEntryPoint`; graphe Hilt validé par build vert + lancement sans crash (pid vivant = Application Hilt créée au runtime). Init on-demand WorkManager : aucun worker en P1 à déclencher — la compilation de la propriété est la preuve d'acceptation définie par le plan 01-01 |
| 5 | SC3 — Coquille de thème clair/sombre persistée DataStore (D-05) | ✓ VERIFIED | Chaîne complète lue : MainActivity (`hiltViewModel` + `collectAsStateWithLifecycle`) → ThemeViewModel (`@HiltViewModel`, StateFlow, `toggleTheme`) → ThemeRepository (`preferencesDataStore "settings"`, clé `theme_mode`, absent→SYSTEM, `edit {}`) → DataStore. Comportement prouvé par la sonde : tap bascule → force-stop → relance à froid → DARK dans `settings.preferences_pb`, pid vivant; captures clair→sombre relues. `ThemeModeTest` 5 tests / 0 échec (re-run frais) |
| 6 | SC3/PLAN 01-02 — Tranche Room v1 absente en P1 (reprogrammée Phase 3, décision propriétaire B) | ✓ VERIFIED | `app/schemas/` absent; grep `RoomDatabase` dans app/src/ = 0; `fallbackToDestructiveMigration` = 0; câblage build Room inerté en place (plugin room + deps épinglées, schemaDirectory) pour la Phase 3 |
| 7 | SC4 — Décisions bloquantes actées et consignées PROJECT.md (Key Decisions) : licence GPL-3.0, positionnement Play « studio de montage IA » + plan B APK signé | ✓ VERIFIED | PROJECT.md lignes 77–79 : GPL-3.0 (NewPipeExtractor + ffmpeg-kit-full-gpl) « Committed »; substitution fork 8.1.7 « Committed »; distribution hybride Play « studio de montage IA » + canal APK signé « Committed »; épinglage fraîcheur « Committed » |
| 8 | SC4 — Stratégie x86_64/appareil ARM64 pour le développement consignée | ✓ VERIFIED (warning W-01) | Actée : 01-CONTEXT.md D-03/D-04 (committée), exécutée moitié émulateur (install x86_64 Success, STATE.md), routée UAT (01-USER-SETUP.md). MAIS absente du tableau PROJECT.md Key Decisions (lettre SC4) — voir Warnings W-01 ; correction = 1 ligne, recommandée au tour UAT |
| 9 | Contraintes transverses — clone frais buildé au wrapper (JDK 17 foojay, sans Gradle global), `local.properties` gitigné ; versions 1:1 errata E2 | ✓ VERIFIED | Wrapper 9.7.1 commité (`gradle-wrapper.properties` distributionUrl, jar présent, gradlew LF, `.gitattributes`); `gradle.properties` foojay auto-download + flags AGP 9; `local.properties` ignoré ET non tracké (`git check-ignore` + `git ls-files`); catalogue relu intégralement : 21/21 versions E2 + checksum (battery section 5 + lecture directe); errata E2 relu (docs/ERRATA-2026-09-05.md, amendement compileSdk 37 consigné) |

**Score:** 9/9 truths verified (0 present-but-behavior-unverified)

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Tranche Room v1 (AppDatabase + DatabaseModule + schéma exporté) | Phase 3 | ROADMAP SC3 amendé 2026-09-05 (décision propriétaire B, commit 0696d5b) — décision connue du propriétaire, hors gaps |
| 2 | Contenu réel des écrans (Accueil/Historique/Paramètres) | Phase 6 | SC2 exige « 3 destinations vide » ; décision WINDOWS ledger — hors gaps |

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `gradle/libs.versions.toml` | Catalogue unique E2, fork épinglé + checksum | ✓ VERIFIED | Lu intégralement : 21 versions E2, coordonnée vivante, SHA-256 consigné, en-tête fraîcheur daté |
| `settings.gradle.kts` | FAIL_ON_PROJECT_REPOS, google()/mavenCentral() seuls, foojay | ✓ VERIFIED | Lu : les 3 présents ; JitPack explicitement absent (Phase 4) ; exception inline foojay documentée |
| `build.gradle.kts` (racine) | 7 alias `apply false` | ✓ VERIFIED | Lu : exactement les 7 alias |
| `gradle.properties` | Flags AGP 9 ensemble + toolchain auto-download | ✓ VERIFIED | Lu : `android.builtInKotlin=false` + `android.newDsl=false` + foojay |
| `app/build.gradle.kts` | Module installable, minSdk 33/targetSdk 36, toolchain 17, deps catalogue, KSP2-only | ✓ VERIFIED | Lu : conforme + compileSdk 37 (amendement E2, décision connue) ; zéro kapt ; deps toutes résolues par le build |
| `app/src/main/AndroidManifest.xml` | Zéro permission, pas de cleartext, supportsRtl, allowBackup=false, init WorkManager on-demand | ✓ VERIFIED | Lu : conforme ; `WorkManagerInitializer` retiré via `tools:node="remove"` |
| `ShortifyLocalApp.kt` | @HiltAndroidApp + Configuration.Provider (propriété) | ✓ VERIFIED | Lu : forme propriété Kotlin correcte (2.11.2) |
| `MainActivity.kt` | @AndroidEntryPoint, hiltViewModel, collectAsStateWithLifecycle, thème + shell | ✓ VERIFIED | Lu : chemin complet câblé |
| `ThemeMode.kt` / `ThemeRepository.kt` / `ThemeViewModel.kt` | D-05 : enum + nextExplicitMode pur ; DataStore « settings » réel ; @HiltViewModel StateFlow | ✓ VERIFIED |lus ; logique pure testée 5/5 ; DataStore réellement lu/écrit (sonde) |
| `Color.kt` / `Shape.kt` / `Shadow.kt` / `Theme.kt` | 14 tokens normatifs, rayons 16/24/28/32, ombre unique 5 %/30 %, when D-05 | ✓ VERIFIED | Lus : 14 tokens exacts, slots M3 mappés, UNE invocation `shadow()` à couleurs token, when D-05 verbatim |
| `AppShell.kt` + 3 écrans | 3 routes, launchSingleTop/restoreState/saveState, libellés ressources, Start/End | ✓ VERIFIED | Lus ; preuve runtime par dump uiautomator (3 libellés) |
| `ThemeModeTest.kt` | 5+ cas D-05 | ✓ VERIFIED | 5 tests, 0 skip, assertions valeur (assertEquals) — re-run frais 5/0/0 |
| `scripts/verify_p1.sh` | Batterie mécanique 7 sections rejouable | ✓ VERIFIED | Re-run : 7/7 sections OK, exit 0 |
| `scripts/verify_emulator_p1.sh` | E2E cible paramétrable ANDROID_SERIAL | ✓ VERIFIED | Re-run sur émulateur en ligne : ÉMU OK exit 0 ; relu : aucun repli silencieux (chaque étape échoue bruyamment) |
| `screens/emulator-shell.png`, `screens/emulator-after-toggle.png` | Captures non vides coquille + après bascule | ✓ VERIFIED | Régénérées par la sonde du vérificateur, relues visuellement (clair 3 onglets / sombre Paramètres) |
| `01-USER-SETUP.md` | Lég appareil consignée avec commande exacte | ✓ VERIFIED | Lu : status Incomplete, commande `ANDROID_SERIAL=…` fournie + règle d'escalade ABI |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `MainActivity.kt` | `ThemeViewModel` | `hiltViewModel()` + `@AndroidEntryPoint` | ✓ WIRED | Lu ; lancement runtime sans crash = graphe résolu |
| `ThemeViewModel.kt` | `ThemeRepository` | Injection constructeur (`@HiltViewModel`/`@Singleton`) | ✓ WIRED | Lu |
| `ThemeRepository.kt` | DataStore « settings » | `preferencesDataStore` délégué + clé `theme_mode` | ✓ WIRED | Lu ; écriture/lecture prouvées par `run-as cat` (DARK persisté) |
| `AppShell.kt` → `SettingsScreen` | `vm.toggleTheme(systemDark)` | callback `onThemeToggle` (capture `darkNow` en contexte composable) | ✓ WIRED | Lu ; bascule effectivement appliquée (capture sombre après tap) |
| `app/build.gradle.kts` | `gradle/libs.versions.toml` | alias `libs.*` uniquement | ✓ WIRED | Lu ; audit zéro-version-inline : 0 hors 2 exemptions documentées |
| Manifest | `ShortifyLocalApp` / `MainActivity` | `android:name` / activity déclarée | ✓ WIRED | Lu |
| Theme files | docs/02 §1.1–1.3 tokens | hex normatifs + rayons + alphas | ✓ WIRED | 14 hex exacts, 0.05f/0.30f, rayons 16/24/28/32 (greps battery + lecture) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| Shell (thème) | `mode` | DataStore « settings » → ThemeRepository → ThemeViewModel → collectAsStateWithLifecycle | Oui — write/read prouvés par la sonde (DARK persisté, thème inversé à l'écran) | ✓ FLOWING |
| Libellés d'onglets | `stringResource(tab.labelRes)` | `strings.xml` (5 clés) | Oui — dump uiautomator contient Accueil/Historique/Paramètres | ✓ FLOWING |
| Icône bascule | `when (themeMode)` | ThemeMode persisté | Oui — lune visible en sombre (capture relue) | ✓ FLOWING |

Aucune valeur rendue ne remonte d'un littéral statique ou d'un mock. Les `.gitkeep` sont des marqueurs d'arborescence voulus (pas des stubs de code) ; les écrans vides sont une décision SPEC (SC2 dit « vide »), tracée Phase 6.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Build produit l'APK | `./gradlew :app:assembleDebug` | exit 0, APK 146 763 997 octets | ✓ PASS |
| Tests unitaires D-05 | `./gradlew :app:testDebugUnitTest` | exit 0 — tests=5, skipped=0, failures=0 | ✓ PASS |
| Batterie mécanique | `bash scripts/verify_p1.sh` | 7/7 sections [OK], « P1 OK », exit 0 | ✓ PASS |
| Install + lancement + 3 onglets + bascule + persistance (émulateur Android 13+) | `bash scripts/verify_emulator_p1.sh` | « ÉMU OK » exit 0 — install Success, pid 12215, 3 libellés au dump, tap bascule, force-stop → relance pid 12379, DARK dans settings.preferences_pb | ✓ PASS |
| Preuve visuelle coquille/bascule | Lecture des 2 captures régénérées | Clair (Accueil, 3 onglets) → Sombre (Paramètres, lune) | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
| ----- | ------- | ------ | ------ |
| `scripts/verify_p1.sh` | `bash scripts/verify_p1.sh` (re-run vérificateur) | exit 0, « P1 OK — 7/7 sections » | PASS |
| `scripts/verify_emulator_p1.sh` | `bash scripts/verify_emulator_p1.sh` (re-run vérificateur, émulateur déjà en ligne — non démarré par la vérification) | exit 0, « ÉMU OK », DARK persisté | PASS |

Les affirmations « probe pass » des SUMMARY n'ont pas été prises pour argent comptant : les deux sondes déclarées par les plans ont été ré-exécutées par le vérificateur dans cette session, avec sorties conformes.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| PROJ-01 | 01-01, 01-02, 01-03, 01-04 (les 4 déclarent `requirements: [PROJ-01]`) | Compile en APK debug sur toolchain épinglée avec catalogue unique, clause de fraîcheur appliquée et notée | ✓ SATISFIED | Build vert (wrapper 9.7.1, JDK 17 toolchain), APK installable, catalogue E2 1:1 relu, en-tête fraîcheur daté 2026-09-05, checksum fork triple-source |

Aucune exigence orpheline : la traceabilité REQUIREMENTS.md ne mappe que PROJ-01 sur la Phase 1 (statut Complete) et toutes les intentions des plans y remontent. PROJ-02 (16 KB/ native) et PROJ-03 (architecture complète) appartiennent aux Phases 2/3 — non attendus ici.

### Test Quality Audit

| Test File | Linked Req | Active | Skipped | Circular | Assertion Level | Verdict |
| --------- | ---------- | ------ | ------- | -------- | --------------- | ------- |
| `app/src/test/java/.../ThemeModeTest.kt` | PROJ-01 / D-05 | 5 | 0 | 0 | Value (`assertEquals` sur chaque branche de `nextExplicitMode` + aller-retour enum) | PASS |

**Disabled tests on requirements:** 0. **Circular patterns:** 0 (fonction pure testée sans oracle auto-généré). **Insufficient assertions:** 0. Le comportement runtime (persistance réelle) est couvert par la sonde E2E adb, pas par le test unitaire — cohérent avec le découpage des plans.

### Decision Coverage

All trackable CONTEXT.md decisions are honored by shipped artifacts (gsd-tools `check.decision-coverage-verify`: 7/7 honored, 0 not honored, gate non-blocking).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (aucun) | — | 0 marqueur de dette (TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) sur app/src, fichiers de build, scripts | — | Aucun blocage |
| (aucun) | — | 0 placeholder / implémentation vide / repli silencieux dans les sondes | — | Aucun blocage |

Aucun 🛑 blocage. Stubs connus et assumés : écrans vides Phase 6 (exigé « vide » par SC2), tranche Room Phase 3 (décision propriétaire B) — documentés, hors gaps.

### Warnings (non-blocking, à disposition du développeur)

- **W-01 — SC4 (lettre) : la stratégie x86_64/appareil ARM64 n'est pas dans PROJECT.md Key Decisions.** Elle est actée dans `01-CONTEXT.md` D-03/D-04 (committée), exécutée moitié émulateur et routée UAT (`01-USER-SETUP.md`), mais le tableau PROJECT.md (le registre inter-phases nommé par SC4) ne contient pas de rangée dédiée. Recommandation : ajouter 1 rangée (« Double cible de vérification : émulateur x86_64 première cible, téléphone physique ARM64 Android 13+ en adb USB (D-03/D-04) ») au tour UAT. Le goal-level (les trois décisions transverses supply-chain/licence/Play) est, lui, intégralement consigné.
- **W-02 — PROJECT.md « Context » ligne « Dépendances natives » cite encore `ffmpeg-kit-full-gpl 6.0-2`** (version morte). Hors périmètre de l'interdit (fichiers de build — propres), simple ligne doc périmée à aligner sur le fork 8.1.7.
- **W-03 — 01-REVIEW.md : 0 critique, 4 warnings (WR-01 crash sur valeur DataStore non parsable, WR-02 TOCTOU bascule, WR-03 pilule M3 secondContainer violette visible sur les captures, WR-04 icônes barre d'état suivant le thème système), 3 info.** Aucun ne contredit un must-have ; à programmer par le propriétaire (WR-03/WR-04 visibles sur les captures de cette vérification).

## Human Verification Required

### 1. Lég appareil physique ARM64 (D-03 / R7)

**Test:** Brancher un téléphone ARM64 Android 13+ en USB (débogage USB), puis :
`adb devices` → `ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh` → copier la capture vers `screens/device-shell.png`.
**Expected:** « ÉMU OK — install + lancement + 3 onglets + bascule + persistance (LIGHT|DARK) prouvés sur <serial> », sans `INSTALL_FAILED_NO_MATCHING_ABIS`/`INSTALL_FAILED_CPU_ABI_INCOMPATIBLE` (blocage ABI ⇒ STOP + escalade propriétaire de spec, ne rien changer).
**Why human:** Aucun appareil physique attaché pendant l'exécution — la moitié arm64 de la double cible exige le matériel.

### 2. Revue visuelle anti-neumorphisme + fidélité tokens (prohibition judgment-tier)

**Test:** Relire `screens/emulator-shell.png` (clair) et `screens/emulator-after-toggle.png` (sombre) — et `device-shell.png` dès la lég 1 — contre docs/02-UI-UX.md §0/§1.1–1.3 : UNE seule ombre douce verticale par surface (5 % clair / 30 % sombre), aucun relief néumorphique, rayons 32/24/16/28, tokens en clair ET en sombre.
**Expected:** Aucune ombre double ni halo ; disposition à prendre sur les écarts connus WR-03 (pilule d'onglet sélectionnée = violet M3 par défaut, visible sur la capture claire) et WR-04 (icônes barre d'état blanches sur fond clair).
**Why human:** Jugement visuel — la pré-lecture du vérificateur (2 captures relues : ombre unique sous la NavigationBar, pas de halo double) n'est pas une preuve.

## Gaps Summary

Aucun gap bloquant. Les 9 must-haves (4 SC roadmap + contraintes transverses + interdits test-tier) sont vérifiés avec preuve comportementale fraîche : build vert ré-exécuté, tests 5/0/0, batterie mécanique 7/7, sonde E2E émulateur ré-exécutée par le vérificateur (install x86_64 Success — blocage ABI clos —, lancement, 3 onglets, bascule, persistance après force-stop). Les décisions bloquantes SC4 sont actées et consignées (licence GPL-3.0, substitution fork, positionnement Play + plan B). Statut `human_needed` uniquement pour les 2 items UAT ci-dessus (matériel physique + jugement visuel), prévus comme tels par le plan 01-04 — plus 3 warnings documentaires/revue à disposition.

**Mode note:** `mode: mvp` déclaré mais goal hors format user-story — divergence à résoudre (`/gsd mvp-phase 1` ou retrait du mode) ; la vérification standard goal-backward a été appliquée.

**next_command suggestion:** `/gsd:verify-work` (tour UAT — items 1 et 2 ci-dessus) ; corriger au passage W-01/W-02 (2 lignes doc) ; puis Phase 2.

---

_Verified: 2026-09-05T22:13:26Z_
_Verifier: Claude (gsd-verifier)_
