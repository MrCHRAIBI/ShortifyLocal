---
phase: 01-chafaudage-d-cisions-bloquantes
plan: 01
subsystem: infra
tags: [gradle, android-build, version-catalog, ffmpeg-kit, hilt, supply-chain, checksum, compose]

# Dependency graph
requires: []
provides:
  - Wrapper Gradle 9.7.1 commité (build reproductible sans Gradle global, JDK toolchain 17 via foojay)
  - Catalogue unique `gradle/libs.versions.toml` 1:1 errata E2 (23 versions, 22 libraries, 7 plugins)
  - Module `app` single-module installable : assembleDebug vert, APK debug 146 Mo produit
  - Classe Application Hilt (`ShortifyLocalApp`) + `Configuration.Provider` WorkManager on-demand
  - Manifest P1 sans aucune permission (supportsRtl, allowBackup=false, pas de cleartext)
  - Fork ffmpeg-kit `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` résolu Maven Central, checksum SHA-256 triple-source vérifié
  - Amendement E2 approuvé : compileSdk 37 (imposé par les AAR androidx épinglés)
affects: [01-02 (Room+Hilt sur le socle), 01-03 (UI Compose remplace MainActivity shell), 01-04 (installation APK sur émulateur), Phase 2 (whisper.cpp/ffmpeg-kit spike)]

# Actuals (#2632) — pairs with the plan's `estimate` to calibrate future estimates.
actuals:
  tokens: 9339
  tasks: 3
  commits: 5

# Tech tracking
tech-stack:
  added: [Gradle 9.7.1 wrapper, AGP 9.4.0, Kotlin 2.4.10, KSP2 2.3.11, Hilt 2.60.1, Room 2.8.4, Compose BOM 2026.08.00, WorkManager 2.11.2, dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl 8.1.7]
  patterns: [catalogue de versions unique zéro inline (2 exemptions documentées), FAIL_ON_PROJECT_REPOS, KSP2-only sans kapt, toolchain JDK 17 auto-provisionnée foojay, thèmes framework sans dépendance material XML]

key-files:
  created:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - gradle.properties
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/shortifylocal/ai/ShortifyLocalApp.kt
    - app/src/main/java/com/shortifylocal/ai/MainActivity.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values-night/themes.xml
    - app/src/main/res/values/ic_launcher_background.xml
    - app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    - app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
    - app/src/main/res/drawable/ic_launcher_foreground.xml
    - app/proguard-rules.pro
  modified:
    - docs/ERRATA-2026-09-05.md
    - .planning/PROJECT.md
    - .gitignore
    - .gitattributes

key-decisions:
  - "compileSdk 37 (amendement E2 du 2026-09-05 approuvé par le propriétaire de spec — option A) : les AAR androidx épinglés (Compose ui 1.12.0, Navigation 2.10.0, core 1.19.0, lifecycle 2.11.0, androidx.hilt 1.4.0) exigent minCompileSdk=37 ; targetSdk 36 et minSdk 33 inchangés — impact build-time uniquement"
  - "Fork ffmpeg-kit épinglé `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` avec SHA-256 374d3734…658b consigné en commentaire catalogue et vérifié triple-source (cache Gradle = repo1.maven.org = valeur épinglée)"
  - "WorkManager initialisé on-demand : Configuration.Provider via la PROPRIÉTÉ Kotlin `workManagerConfiguration` (la forme méthode des docs périmées ne compile pas contre 2.11.2), initialiseur par défaut retiré du InitializationProvider"
  - "Thèmes framework Android (Theme.Material.Light.NoActionBar) au lieu d'une dépendance material XML — le thème Compose Soft-Clean arrive au plan 01-03"
  - "Foojay resolver 1.0.0 inline dans settings.gradle.kts : unique exception zéro-version-inline (limitation Gradle, documentée dans le fichier et l'audit tache 3)"

patterns-established:
  - "Pattern catalogue : toute coordonnée de dépendance/plugin passe par libs.versions.toml — un grep d'audit fait foi (0 occurrence hors 2 exemptions)"
  - "Pattern supply-chain : coordonnée fork + SHA-256 en commentaire catalogue ; vérification triple-source à chaque doute"
  - "Pattern manifest P1 : zéro uses-permission — chaque permission arrive avec sa fonction (docs/01 §2.3)"

requirements-completed: [PROJ-01]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "Système de build reproductible : wrapper Gradle 9.7.1 opérationnel sans Gradle global, plugins racine (AGP 9.4.0, Kotlin 2.4.10, KSP2 2.3.11, Hilt 2.60.1, Room 2.8.4) résolus depuis le catalogue E2"
    requirement: PROJ-01
    verification:
      - kind: integration
        ref: "./gradlew --version && ./gradlew help (BUILD SUCCESSFUL, Gradle 9.7.1, 7 alias résolus)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Module app minimal installable : ./gradlew :app:assembleDebug produit app/build/outputs/apk/debug/app-debug.apk (146 Mo, .so arm64+x86_64 du fork embarqués)"
    requirement: PROJ-01
    verification:
      - kind: integration
        ref: "./gradlew :app:assembleDebug (BUILD SUCCESSFUL in 36s) + test -f app/build/outputs/apk/debug/app-debug.apk"
        status: pass
    human_judgment: false
  - id: D3
    description: "Manifest P1 verrouillé : zéro uses-permission, zéro cleartext, supportsRtl=true, allowBackup=false ; chemin manifest → Application Hilt → Activity prouvé par le build (Configuration.Provider propriété Kotlin compilée par KSP2)"
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "grep manifest: 0 uses-permission, 0 usesCleartextTraffic, supportsRtl/allowBackup présents ; kspDebugKotlin + hiltJavaCompileDebug passés"
        status: pass
    human_judgment: false
  - id: D4
    description: "Verrou supply-chain : AAR fork ffmpeg-kit 8.1.7 résolu depuis Maven Central uniquement (modules-2), SHA-256 triple-source en accord (cache = repo1.maven.org = valeur épinglée catalogue) ; audit zéro-version-inline = 0 occurrence hors 2 exemptions documentées"
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "sha256sum AAR cache + curl repo1.maven.org...aar.sha256 + grep -rnE '\"[0-9]+(\\.[0-9]+)+\"' build files → 0 hors exemptions"
        status: pass
    human_judgment: false
  - id: D5
    description: "Catalogue E2 1:1 : les 21 versions épinglées + le checksum consigné présents dans gradle/libs.versions.toml ; zéro référence à l'ancienne coordonnée vendeur (commentaires inclus)"
    requirement: PROJ-01
    verification:
      - kind: other
        ref: "grep battery 21/21 versions OK + grep 374d3734…658b = 1 + grep com.arthenica = 0"
        status: pass
    human_judgment: false

# Metrics
duration: 8min
completed: 2026-09-05
status: complete
---

# Phase 1 Plan 1: Socle build reproductible Summary

**Socle Gradle 9.7.1 + catalogue E2 épinglé : `./gradlew :app:assembleDebug` produit l'APK debug depuis un clone frais, fork ffmpeg-kit 8.1.7 résolu Maven Central avec checksum SHA-256 vérifié triple-source, module app Hilt sans aucune permission.**

## Performance

- **Duration:** 8 min (session de continuation ; la tâche 1 a été exécutée dans la session précédente, interrompue au checkpoint de décision compileSdk)
- **Started:** 2026-09-05T16:52:20Z (continuation)
- **Completed:** 2026-09-05T17:00:33Z
- **Tasks:** 3/3
- **Files modified:** 24 (16 créés, 8 modifiés incl. errata + PROJECT.md)

## Accomplishments
- Wrapper Gradle 9.7.1 généré sans Gradle global (distribution téléchargée hors dépôt), `gradlew` LF normalisé, hygiène repo Android (.gitignore/.gitattributes)
- Catalogue unique `gradle/libs.versions.toml` 1:1 errata E2 — 21 versions épinglées vérifiées par grep, checksum fork consigné, entrées phases ultérieures catalogue-only
- Module `app` : assembleDebug vert, APK debug 146 Mo ; Application Hilt + WorkManager on-demand (propriété Kotlin) ; manifest zéro permission, zéro cleartext
- Verrou supply-chain : SHA-256 de l'AAR réellement résolu = valeur publiée repo1.maven.org = valeur épinglée catalogue (`374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b`) ; audit inline-versions = 0 hors exemptions documentées
- Amendement E2 appliqué et consigné : compileSdk 37 (décision propriétaire A, approuvée 2026-09-05) — errata, PROJECT.md et build file alignés

## Task Commits

Chaque tâche commitée atomiquement :

1. **Task 1: Système de build de bout en bout** - `0707610` (feat) — session précédente
2. **Amendement E2 compileSdk 37 (décision propriétaire A)** - `604c635` (fix) — build file + errata + PROJECT.md en un commit
3. **Task 2: Module app minimal installable** - `437050d` (feat) — 11 fichiers (`app/build.gradle.kts` déjà inclus dans `604c635`)
4. **Task 3: Verrou supply-chain** - `08cb16e` (chore, commit marqueur vide) — tâche verification-only : le catalogue était déjà correctement épinglé à la tâche 1, zéro diff fichier ; la preuve (checksum triple-source + audit grep) est consignée dans le message de commit et ci-dessous

**Plan metadata:** *(commité après ce fichier)*

## Files Created/Modified
- `gradle/libs.versions.toml` - catalogue E2 unique (source de foi des versions + checksum fork)
- `settings.gradle.kts` - FAIL_ON_PROJECT_REPOS, google()/mavenCentral() seuls, foojay resolver
- `build.gradle.kts` (racine) - 7 alias plugins en `apply false`
- `gradle.properties` - flags AGP 9 (`android.builtInKotlin=false`, `android.newDsl=false`), toolchain auto-download
- `app/build.gradle.kts` - module single, compileSdk 37 (amendement E2), minSdk 33/targetSdk 36, room schemaDirectory, dépendances KSP2-only
- `app/src/main/AndroidManifest.xml` - zéro permission, MainActivity exported seule, InitializationProvider on-demand
- `app/src/main/java/com/shortifylocal/ai/ShortifyLocalApp.kt` - @HiltAndroidApp + Configuration.Provider (propriété Kotlin)
- `app/src/main/java/com/shortifylocal/ai/MainActivity.kt` - coquille minimale (Text = ressource app_name)
- `app/src/main/res/**` - strings (app_name=ShortifyLocal, D-07), thèmes clair/nuit framework, icône adaptive #E4590C
- `docs/ERRATA-2026-09-05.md` - §E2 amendé : compileSdk 37 + note datée de l'amendement approuvé
- `.planning/PROJECT.md` - contrainte compat mise à jour + ligne Key Decisions amendement
- `.gitignore`, `.gitattributes` - hygiène Android, gradlew LF

## Decisions Made
- **compileSdk 37 (amendement E2, option A)** : au checkpoint de la tâche 2, le build échouait — les AAR androidx épinglés par E2 exigent minCompileSdk=37. Le propriétaire de spec a approuvé l'amendement via le mécanisme de primauté de l'errata (note datée dans le fichier) ; targetSdk 36 et minSdk 33 inchangés, impact build-time uniquement.
- **Task 3 verification-only** : le checksum étant déjà correctement épinglé à la tâche 1 et la vérification triple-source en accord, aucun fichier n'a changé — commit marqueur vide (`--allow-empty`) pour horodater le passage du gate dans l'historique.
- **Thèmes framework au lieu de material XML** : conforme au plan (Pattern 5) — évite une dépendance XML avant l'arrivée du thème Compose (01-03).

## Deviations from Plan

### Auto-fixed Issues

**1. [Spec amendment approuvé — checkpoint résolu] compileSdk 36 → 37**
- **Found during:** Task 2 (session précédente, checkpoint de décision retourné au propriétaire de spec)
- **Issue:** CONTRADICTION E2 interne : les AAR androidx épinglés (Compose BOM 2026.08.00 → ui 1.12.0, Navigation 2.10.0, core 1.19.0, lifecycle 2.11.0, androidx.hilt 1.4.0) exigent minCompileSdk=37 alors que E2 épinglait compile/targetSdk=36 — build impossible
- **Fix:** Option A approuvée par le propriétaire de spec : amendement de l'errata E2 (primauté errata), PROJECT.md et `app/build.gradle.kts` alignés, commité atomiquement AVANT de reprendre le build
- **Files modified:** app/build.gradle.kts, docs/ERRATA-2026-09-05.md, .planning/PROJECT.md
- **Verification:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (plateforme android-37 récupérée automatiquement, licences déjà acceptées)
- **Committed in:** `604c635`

---

**Total deviations:** 1 (amendement de spec approuvé par l'utilisateur, pas un auto-fix discret)
**Impact on plan:** L'amendement était nécessaire et borné (build-time uniquement) ; aucune autre déviation — le plan a été exécuté tel qu'écrit.

## Known Stubs

| Stub | File | Reason | Resolved by |
|------|------|--------|-------------|
| MainActivity coquille minimale (`Text(app_name)` sans navigation) | `app/src/main/java/com/shortifylocal/ai/MainActivity.kt` | Intentionnel — prescrit par le plan pour prouver le chemin manifest → Application → Activity ; l'écran réel arrive au plan 01-03 | Plan 01-03 |

Aucun autre stub : toutes les dépendances déclarées en `app/build.gradle.kts` sont réellement résolues par le build (APK produit), et les entrées catalogue des phases ultérieures sont volontairement non référencées (audit tache 3).

## Issues Encountered
- Contradiction E2 compileSdk (voir Deviations) — résolue par amendement approuvé, plateforme android-37 auto-téléchargée par Gradle sans intervention.
- La tâche 1 avait été interrompue au checkpoint ; la reprise n'a pas re-exécuté la tâche 1 (commit `0707610` vérifié présent en tête d'historique).

## User Setup Required

None - aucune configuration de service externe requise (aucune clé, aucun compte ; local.properties reste gitigné).

## Next Phase Readiness
- Socle build verrouillé : les plans 01-02 (Room + Hilt + thème DataStore) et 01-03 (UI Compose) construisent directement dessus
- L'APK debug existe pour le plan 01-04 (installation émulateur/appareil) — disponibilité x86_64 du fork à confirmer à ce moment (blocker ouvert consigné dans STATE.md)
- Prochaines épinglages : coordonnée whisper.cpp b4938 (Phase 2), JitPack ajouté au moment de NewPipeExtractor (Phase 4)

---
*Phase: 01-chafaudage-d-cisions-bloquantes*
*Completed: 2026-09-05*

## Self-Check: PASSED

- 11/11 fichiers clés présents sur disque (incl. app-debug.apk et ce SUMMARY)
- 4/4 commits vérifiés dans l'historique (`0707610`, `604c635`, `437050d`, `08cb16e`)
- Vérifications plan-level re-passées : assembleDebug vert, 21/21 versions E2, checksum triple-source en accord, manifest 0 permission / 0 cleartext, 0 référence ancien vendeur
