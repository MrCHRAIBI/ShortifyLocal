# Phase 1: Échafaudage & décisions bloquantes - Research

**Researched:** 2026-09-05
**Domain:** Android Gradle scaffolding (AGP 9 / Gradle 9 / Kotlin 2.4 / KSP2), Compose Material 3 theming, Hilt/WorkManager/Room wiring, Maven supply-chain pinning
**Confidence:** HIGH (every load-bearing discrete value verified against first-party registries or androidx source this session)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(verbatim from `01-CONTEXT.md <decisions>`)

### Environnement de développement
- **D-01:** L'environnement local est partiellement prêt : Android Studio, Android SDK et un émulateur existent déjà ; **le JDK 17 est le seul composant manquant**. Le plan ne doit PAS inclure de tâches d'installation SDK/Studio/AVD. — **Reversibility:** reversible — configuration locale, ne touche pas le dépôt
- **D-02:** Le JDK 17 est provisionné par **toolchain auto-provisioning Gradle** (resolveur foojay, `java-toolchain` dégradé automatiquement) plutôt qu'une installation manuelle — reproductible sur toute machine, rien à installer à la main. Le plan inclut le resolveur de toolchain dès l'échafaudage (build.gradle settings + `org.gradle.java.installations.auto-download` si pertinent).

### Vérification double cible (R7)
- **D-03:** L'appareil physique de vérification est un **téléphone ARM64 sous Android 13+**, connecté en **adb USB**. Les vérifications d'install/lancement peuvent être scriptées (`adb install -r` + `am start` + `adb shell`). — **Reversibility:** reversible
- **D-04:** L'émulateur sert de première cible rapide, l'appareil physique valide la cible arm64 (les `.so` arm64-only du fork ne sont jamais chargés en P1 — l'install émulateur x86_64 doit rester possible).

### Thème
- **D-05:** Au **premier lancement, le thème suit le système** (`isSystemInDarkTheme()` comme valeur initiale). La bascule utilisateur devient une **préférence explicite persistée** (DataStore) qui prime ensuite sur le système ; tant qu'aucun choix explicite n'existe, l'app reste synchronisée sur le système. — **Reversibility:** costly
- **D-06:** Les tokens Soft-Clean (Partie 2 §1.1–1.3 : couleurs clair/sombre, rayons 32/24/16/28, ombre unique verticale 5 % clair / 30 % sombre) sont définis dès P1 dans le thème ; le **mapping vers les slots Material 3** (`colorScheme`, shapes) et l'implémentation de l'ombre unique (`Modifier.shadow` vs `drawBehind`) sont laissés à la discrétion de recherche/planification sous contrainte du résultat visuel normatif.

### Identité affichée
- **D-07:** Label launcher (`app_name`) = **« ShortifyLocal »** (plus court sous l'icône). Le nom complet « ShortifyLocal AI » reste utilisé dans le texte de partage `share_app`, l'à-propos et le futur listing store. Chaîne via ressources (`values/strings.xml`), jamais en dur. — **Reversibility:** reversible — une ressource à changer

### Claude's Discretion
- Mapping tokens Soft-Clean → slots MaterialTheme (D-06)
- Choix du composant de barre de navigation de la coquille (le composant définitif `BottomNavPill` arrive en Phase 6 ; la coquille P1 peut utiliser une NavigationBar standard stylée par les tokens)
- Structure fine des fichiers Gradle (convention plugins, ordre des blocs) sous contrainte : catalogue unique + zéro version inline + KSP2 uniquement

### Deferred Ideas (OUT OF SCOPE)
Aucune idée différée — la discussion est restée dans le périmètre de la phase.
</user_constraints>

**Project instructions note:** No `CLAUDE.md` exists at repo root or `.zcode/`. The `claude_md_path` configured in `.planning/config.json` (`./.claude/.clinerules`) does not exist either — verified this session. No project skill rules apply beyond GSD workflow skills.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROJ-01 | Le projet se compile en APK debug sur la toolchain épinglée (Kotlin 2.4.10, AGP 9.4.0, Gradle 9.7.1, JDK 17, minSdk 33, targetSdk 36) avec catalogue unique `gradle/libs.versions.toml` | Full Gradle file shapes verified (settings/root/app/catalog/properties); AGP 9 built-in Kotlin conflict resolved (`android.builtInKotlin=false` + `android.newDsl=false`); foojay resolver 1.0.0 for JDK 17 auto-provisioning; fork 8.1.7 resolvable with verified SHA-256 |
</phase_requirements>

## Summary

This phase scaffolds the entire Android project from zero (greenfield confirmed: no `.kt`, no Gradle, no manifest in the repo). Research resolved the five phase-specific gaps: (1) exact Gradle configuration shape for the pinned toolchain, (2) the ffmpeg-kit fork's Maven coordinate + checksum, (3) the Soft-Clean token-to-Material 3 theming pattern, (4) current-API Hilt/WorkManager/Room/DataStore wiring idioms, (5) Windows/Git Bash specifics. Every load-bearing discrete value was verified against first-party sources this session (Google Maven, Maven Central, Gradle Plugin Portal, developer.android.com, androidx-main source).

**Two major de-risks discovered:**

1. **The "arm64-only" fork concern is FALSE.** The `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` AAR was downloaded and inspected directly: it ships **both `jni/arm64-v8a/` and `jni/x86_64/`** `.so` sets (10 libs each). The x86_64 emulator will install natively — no ARM translation needed, no `abiFilters` needed. This closes the open blocker inherited from STATE.md ("disponibilité x86_64 du fork à confirmer"). Note: D-04's wording ("`.so` arm64-only") is factually outdated but the decision itself (emulator as fast target, physical ARM64 device as validation) is unaffected — it is *reinforced*.

2. **AGP 9 built-in Kotlin must be disabled.** AGP 9 enables built-in Kotlin by default; its embedded Kotlin is **2.2.10** (read from the AGP 9.4.0 POM), which contradicts the errata-pinned Kotlin 2.4.10 and the Compose compiler plugin requirement (version must match Kotlin). Opt-out = two `gradle.properties` flags (`android.builtInKotlin=false`, `android.newDsl=false`), verified from the official migration guide. Also note: the toolchain request (17) and the daemon JVM (Android Studio JBR = JDK 25.0.3, discovered on this machine) coexist by design — AGP 9.4 ships in the Studio generation using that JBR.

**One stale-docs trap:** the official Hilt and WorkManager pages still show `override fun getWorkManagerConfiguration()` — the actual `Configuration.Provider` interface in androidx source declares only the **Kotlin property** `val workManagerConfiguration`. The method form will not compile against WorkManager 2.11.2. Research prescribes the property form.

**Primary recommendation:** Scaffold exactly per the file shapes in "Architecture Patterns" (single-module, version catalog with ALL errata E2 entries, built-in Kotlin opted out, foojay 1.0.0, Room Gradle plugin `androidx.room` 2.8.4 for schema export, Hilt worker factory via property override, theme via token-driven `lightColorScheme`/`darkColorScheme` + `Modifier.shadow` with token colors), record checksum `374d3734…4658b` in the catalog comment, and validate with the deterministic grep/build/adb battery in "Validation Architecture".

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Build/toolchain reproducibility | Gradle (settings + wrapper + catalog) | — | D-02: foojay resolver + toolchain block own JDK provisioning; zero manual installs |
| Dependency pinning / supply chain | `gradle/libs.versions.toml` + repositories | — | Single catalog = errata E2 1:1; checksum recorded as catalog comment |
| Soft-Clean theme (tokens, light/dark) | presentation/theme (Compose) | — | Pure UI tier; MaterialTheme construction from hex tokens (D-06) |
| Theme persistence | device (DataStore) | presentation (ViewModel/flow) | Non-sensitive preference; DataStore owns atomic persistence (D-05) |
| DI plumbing | di/ (Hilt) | App class | @HiltAndroidApp + modules; KSP2 processors |
| WorkManager on-demand init | App class (Configuration.Provider) | manifest | Property override + initializer removal |
| Room v1 schema | data/local/room + Room Gradle plugin | — | Compile-time schema export; no entities until Phase 3 |
| Install/launch verification | adb (device + emulator tier) | — | Scripted, not in-app |

## Standard Stack

Versions below are **phase additions/corrections** on top of the already-verified `.planning/research/STACK.md` (2026-09-04). Do not re-litigate STACK.md entries.

### Core (verified this session)
| Library / Plugin | Version | Purpose | Provenance |
|---------|---------|---------|------------|
| `org.gradle.toolchains.foojay-resolver-convention` | **1.0.0** (latest) | JDK 17 auto-provisioning (D-02) | [VERIFIED: plugins.gradle.org maven-metadata `<release>1.0.0</release>` + docs.gradle.org current userguide snippet] |
| `androidx.hilt:hilt-work` | **1.4.0** (stable) | HiltWorkerFactory | [VERIFIED: Google Maven maven-metadata — 1.4.0 stable, 1.4.0-alpha01/beta01/rc01 above it; official docs page still shows 1.0.0 → docs lag, trust registry] |
| `androidx.hilt:hilt-compiler` | **1.4.0** (stable) | KSP processor for @HiltWorker | [VERIFIED: Google Maven maven-metadata — 1.4.0 stable] |
| `androidx.compose.material3` via BOM 2026.08.00 | **1.4.0** | M3 ColorScheme/Shapes API | [VERIFIED: compose-bom-2026.08.00.pom read directly] |
| `androidx.compose.material:material-icons-extended` via BOM | **1.7.8** | Tab/nav icons | [VERIFIED: compose-bom-2026.08.00.pom] |
| `androidx.activity:activity-compose` | **1.13.0** (latest stable; 1.14.0-alpha01 excluded) | setContent host | [VERIFIED: Google Maven maven-metadata] |
| `androidx.lifecycle:lifecycle-runtime-compose` | **2.11.0** (latest stable; 2.12.0-alpha02 excluded) | `collectAsStateWithLifecycle` | [VERIFIED: Google Maven maven-metadata] |
| `androidx.core:core-ktx` | **1.19.0** | Base ktx | [VERIFIED: Google Maven maven-metadata `<release>`] |
| `androidx.room` Gradle plugin (marker `androidx.room:androidx.room.gradle.plugin`) | **2.8.4** (= room version) | `room { schemaDirectory }` compile-time schema export | [VERIFIED: plugin marker maven-metadata at 2.8.4 on Google Maven] |
| `junit:junit` | **4.13.2** | Template unit-test dep | [VERIFIED: repo1.maven.org maven-metadata] |

### From STACK.md (already verified 2026-09-04 — restate for catalog completeness)
Kotlin 2.4.10, AGP 9.4.0, Gradle wrapper 9.7.1, KSP 2.3.11, Compose BOM 2026.08.00, Hilt 2.60.1, Room 2.8.4, Navigation Compose 2.10.0, WorkManager 2.11.2, security-crypto 1.1.0, play-services-ads 25.4.0, UMP 4.0.0, ML Kit face-detection 16.1.7, Media3 1.11.0, OkHttp 5.5.0, Coil 3.6.2, DataStore 1.2.1, coroutines 1.11.0, kotlinx-serialization-json 1.11.0, NewPipeExtractor v0.26.5 (JitPack), `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7`, whisper.cpp b4938 (comment only).

**AGP 9.4.0 environment requirements** [VERIFIED: developer.android.com/build/releases/gradle-plugin]: Gradle ≥ 9.6.0 (9.7.1 OK), JDK ≥ 17, build-tools 36.0.0 (installed), NDK default 28.2.13676358 (installed), max API 37.

### Package Legitimacy Audit

> The `gsd-tools query package-legitimacy` seam supports npm/pypi/crates only — no Maven verdicts. Maven-equivalent legitimacy was established by direct registry evidence (first-party registries only: `dl.google.com`, `repo1.maven.org`, `services.gradle.org`, `plugins.gradle.org`); no third-party mirrors, no `-SNAPSHOT`, no JitPack in P1.

| Package | Registry | Age / Published | Size | Source Repo | Verdict | Disposition |
|---------|----------|-----------------|------|-------------|---------|-------------|
| `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl` 8.1.7 | Maven Central | 2026-07-12 | 36,988,198 bytes (AAR) | github.com/ffmpegkit-maintained/ffmpeg-kit | OK — AAR downloaded, SHA-256 recomputed locally and matched published `.aar.sha256`; GPG `.asc` present | Approved — pin with checksum |
| `androidx.*` artifacts (all pinned) | Google Maven | per metadata | — | android.googlesource.com | OK | Approved |
| `com.google.dagger:hilt-*` 2.60.1 | Maven Central | per STACK.md | — | github.com/google/dagger | OK | Approved |
| `com.github.TeamNewPipe:NewPipeExtractor` v0.26.5 | JitPack | 2026-08-15 | — | github.com/TeamNewPipe/NewPipeExtractor | OK (STACK.md verified build "ok") | Approved — **catalog entry only in P1; defer the `jitpack.io` repository declaration to Phase 4** (tighter P1 supply chain; nothing resolves from JitPack until the extractor is wired) |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
Fresh clone ──> gradlew wrapper 9.7.1 ──> settings.gradle.kts (foojay 1.0.0)
                                              │ resolves JDK 17 toolchain (auto-download if absent)
                                              ▼
        gradle/libs.versions.toml (catalog = errata E2 1:1, ffmpeg checksum comment)
                                              │
            ┌─────────────────────────────────┴──────────────────────────────┐
            ▼                                                                ▼
   :app build (AGP 9.4.0, Kotlin 2.4.10, KSP2 2.3.11)              Maven Central + Google Maven
            │  KSP2 processors: Room (schema → app/schemas/…/1.json),  ──> dev.ffmpegkit-maintained:…:8.1.7
            │  Hilt, androidx.hilt                                          (AAR: arm64-v8a + x86_64 .so,
            ▼                                                                never dlopen'ed in P1)
   assembleDebug ──> app-debug.apk
            │
            ├──> adb install -r ──> x86_64 emulator (AVD, API 33+)  ──┐
            └──> adb install -r ──> ARM64 physical device (USB)      ──┤
                                                                      ▼
                                     MainActivity (setContent) ─> ShortifyLocalTheme(tokens)
                                                                      │
                                              DataStore("settings") <─┤ theme = SYSTEM|LIGHT|DARK
                                                                      ▼
                                     AppShell: NavigationBar 3 tabs (Accueil/Historique/Paramètres)
```

Entry point: Gradle wrapper → APK → launcher. Data flow: theme toggle → DataStore write → flow → recomposition; cold start → DataStore read (SYSTEM = follow `isSystemInDarkTheme()`).

### Recommended Project Structure

```
(shortifylocal repo root)
├── .gitattributes                  # gradlew eol=lf (Windows/Git Bash — see Pitfalls)
├── .gitignore                      # build/, .gradle/, local.properties, .idea/ (Android template)
├── settings.gradle.kts
├── build.gradle.kts                # root: alias(...) apply false only
├── gradle.properties               # builtInKotlin opt-out, auto-download, useAndroidX
├── gradle/
│   ├── libs.versions.toml          # SINGLE catalog — all E2 entries
│   └── wrapper/                    # gradle-wrapper.properties: 9.7.1
├── gradlew / gradlew.bat
├── local.properties                # gitignored (sdk.dir) — env ANDROID_HOME already set anyway
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/                    # committed — Room schema JSON (com.shortifylocal.ai.data.local.room.AppDatabase/1.json)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml  # app_name=ShortifyLocal, tab_accueil, tab_historique, tab_parametres
        └── java/com/shortifylocal/ai/
            ├── ShortifyLocalApp.kt        # @HiltAndroidApp + Configuration.Provider (property form)
            ├── MainActivity.kt
            ├── presentation/{ui/home,ui/history,ui/settings,navigation,theme}/
            ├── domain/{model,repository,usecase}/      # pure Kotlin — .gitkeep or placeholder package-info
            ├── data/local/room/AppDatabase.kt          # v1, zero entities, exportSchema=true
            ├── worker/                                  # empty this phase
            ├── di/                                      # DatabaseModule, AppModule
            └── util/
```

(Normative tree from docs/01 §3.2; P1 creates the skeleton with empty/placeholder packages. `domain/` must have zero `android.*`/`androidx.*` imports — grep criterion.)

### Pattern 1 — settings.gradle.kts with foojay resolver

```kotlin
// Source: docs.gradle.org current userguide (toolchains) + plugins.gradle.org metadata
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" // see inline-version exception below
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // jitpack.io deliberately ABSENT in P1 — added in Phase 4 with NewPipeExtractor
    }
}
rootProject.name = "ShortifyLocal AI"
include(":app")
```

> **"Zéro version inline" exception (planner: surface to user or accept as documented):** Gradle does not support version-catalog references in the *settings file's own* `plugins {}` block. The foojay resolver version `1.0.0` is the single unavoidable inline version. Alternative: none clean (composite builds are overkill).

### Pattern 2 — gradle.properties (the load-bearing file)

```properties
# Source: developer.android.com/build/migrate-to-built-in-kotlin (both flags REQUIRED together)
android.builtInKotlin=false
android.newDsl=false

org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.java.installations.auto-download=true
kotlin.code.style=official
```

**Why both flags** [VERIFIED: developer.android.com/build/migrate-to-built-in-kotlin]: AGP 9 enables built-in Kotlin by default ("AGP 9.0 already enables built-in Kotlin for all your modules where you apply AGP"). Applying `org.jetbrains.kotlin.android` with it active fails with `Cannot add extension with name 'kotlin', as there is an extension already registered with that name.` or `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.` The guide states the kotlin-android plugin "is not compatible with" the new DSL, so `android.newDsl=false` is required alongside. Built-in Kotlin embeds Kotlin **2.2.10** [VERIFIED: AGP 9.4.0 POM `kotlin-gradle-plugin 2.2.10`], which cannot honor the errata's Kotlin 2.4.10 / Compose plugin pairing [VERIFIED: developer.android.com/develop/ui/compose/compiler — "this version matches your Kotlin version"].

### Pattern 3 — root + app build files

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.shortifylocal.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shortifylocal.ai"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false   // release/signing = Phase 7
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }   // D-02: toolchain 17; foojay provisions if absent

room {
    schemaDirectory("$projectDir/schemas")   // [ASSUMED: DSL shape — plugin id+version VERIFIED; see Assumptions A1]
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)        // or explicit: ui, material3, material-icons-extended, foundation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.work)     // HiltWorkerFactory
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ffmpegkit.full.gpl)     // never executed in P1; .so arm64+x86_64 embedded
    testImplementation(libs.junit)
}
```

### Pattern 4 — complete `libs.versions.toml` (catalog = errata E2 1:1 + session-verified additions)

```toml
# Single source of versions — errata E2 (docs/ERRATA-2026-09-05.md). No inline versions anywhere else
# except the foojay resolver in settings.gradle.kts (Gradle limitation, documented).
[versions]
kotlin = "2.4.10"
agp = "9.4.0"
gradleWrapper = "9.7.1"
foojayResolver = "1.0.0"          # used in settings.gradle.kts plugins block
ksp = "2.3.11"                    # KSP2 standalone versioning; 2.3.10+ = Kotlin 2.4 compat
composeBom = "2026.08.00"
material3 = "1.4.0"               # informational only — resolved by the BOM
activityCompose = "1.13.0"
lifecycle = "2.11.0"
coreKtx = "1.19.0"
hilt = "2.60.1"
androidxHilt = "1.4.0"            # hilt-work / hilt-compiler / hilt-navigation-compose
room = "2.8.4"
navigationCompose = "2.10.0"
workManager = "2.11.2"
securityCrypto = "1.1.0"          # API deprecated; spec-mandated — Phase 3 scope
playServicesAds = "25.4.0"        # Phase 7 scope (catalog entry only)
ump = "4.0.0"                     # Phase 7 scope
mlkitFaceDetection = "16.1.7"     # Phase 5 scope
newPipeExtractor = "v0.26.5"      # JitPack — Phase 4 scope; repo added then
# ffmpeg-kit: dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl — replaces dead com.arthenica:ffmpeg-kit-full-gpl
# AAR 8.1.7 SHA-256 = 374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b
# (repo1.maven.org 2026-07-12, 36 988 198 bytes, ABI arm64-v8a + x86_64 — checked 2026-09-05)
ffmpegKit = "8.1.7"
whisperCpp = "b4938"              # source-only (CMake/NDK) — Phase 2, comment entry
media3 = "1.11.0"                 # Phase 5 scope
okhttp = "5.5.0"                  # Phase 4 scope
coil = "3.6.2"                    # Phase 6 scope
datastore = "1.2.1"
coroutines = "1.11.0"
serializationJson = "1.11.0"      # Phase 3/4 scope
junit = "4.13.2"

[libraries]
# — Compose (BOM-aligned)
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
# — AndroidX
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "workManager" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
# — Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-android-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHilt" }
# — Room
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
# — Kotlinx
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }
# — Pipeline / scope ultérieures (entrées catalogue E2, résolues seulement quand référencées)
ffmpegkit-full-gpl = { module = "dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl", version.ref = "ffmpegKit" }
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newPipeExtractor" }
play-services-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "playServicesAds" }
ump = { module = "com.google.android.ump:user-messaging-platform", version.ref = "ump" }
mlkit-face-detection = { module = "com.google.mlkit:face-detection", version.ref = "mlkitFaceDetection" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
# — Test
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

### Pattern 5 — Application class + manifest (WorkManager on-demand init, PROPERTY form)

```kotlin
// Source: androidx-main Configuration.kt (interface Provider { public val workManagerConfiguration: Configuration })
// WARNING: developer.android.com pages still show `override fun getWorkManagerConfiguration()` — STALE, will not compile.
@HiltAndroidApp
class ShortifyLocalApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

```xml
<!-- AndroidManifest.xml — remove default WorkManager initializer (on-demand init).
     Source: developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration -->
<application
    android:name=".ShortifyLocalApp"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:icon="@mipmap/ic_launcher"
    android:theme="@style/Theme.Material3.DayNight.NoActionBar">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        android:exported="false"
        tools:node="merge">
        <meta-data
            android:name="androidx.work.WorkManagerInitializer"
            android:value="androidx.startup"
            tools:node="remove" />
    </provider>
</application>
```

**P1 manifest declares NO permissions** (the shell does nothing networked; docs/01 §2.3 permissions belong to Phases 2–7). No `usesCleartextTraffic` attribute at all (HTTPS-only default). Deep link intent-filter (`shortify://invite`) is Phase 3 scope. The ffmpeg-kit AAR manifest adds no permissions (its libraries are never dlopen'ed in P1).

### Pattern 6 — Soft-Clean theme (D-06 recommended mapping)

Tokens (normative, docs/02 §1.1–1.3 — quote-verbatim from the doc this session):

| Token | Light | Dark |
|---|---|---|
| Background | `#EEF0F2` | `#0E0E10` |
| Card | `#FFFFFF` | `#1A1A1E` |
| PrimaryText | `#1A1A1A` | `#FFFFFF` |
| SecondaryText | `#8A8F98` | `#9A9AA2` |
| PrimaryButton | `#111111` (text `#FFFFFF`) | `#FFFFFF` (text `#000000`) |
| AccentActive | `#E4590C` | `#FF7A3D` |
| ScoreExcellent | `#22C55E` | `#22C55E` |
| Shadow | black 5%, blur 24, offset (0,8) | black 30%, blur 16, offset (0,6) |
| Radii | 32 large cards/modals · 24 images · 16 buttons/chips/fields · 28 bottom nav pill | same |

```kotlin
// presentation/theme/Color.kt — token layer (numbers are the spec, keep names normative)
object SoftCleanTokens {
    val BackgroundLight = Color(0xFFEEF0F2); val BackgroundDark = Color(0xFF0E0E10)
    val CardLight = Color(0xFFFFFFFF);       val CardDark = Color(0xFF1A1A1E)
    val PrimaryTextLight = Color(0xFF1A1A1A); val PrimaryTextDark = Color(0xFFFFFFFF)
    val SecondaryTextLight = Color(0xFF8A8F98); val SecondaryTextDark = Color(0xFF9A9AA2)
    val PrimaryButtonLight = Color(0xFF111111); val PrimaryButtonDark = Color(0xFFFFFFFF)
    val AccentActiveLight = Color(0xFFE4590C);  val AccentActiveDark = Color(0xFFFF7A3D)
    val ScoreExcellent = Color(0xFF22C55E)
    val ShadowLight = Color(0xFF000000).copy(alpha = 0.05f)
    val ShadowDark = Color(0xFF000000).copy(alpha = 0.30f)
}

// presentation/theme/Shape.kt — radii 32/24/16/28 mapped onto M3 slot sizes
val SoftCleanShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),  // fields, chips
    small = RoundedCornerShape(16.dp),       // buttons
    medium = RoundedCornerShape(24.dp),      // images, small cards
    large = RoundedCornerShape(28.dp),       // bottom nav pill
    extraLarge = RoundedCornerShape(32.dp),  // large cards, modals
)
```

Slot mapping is discretionary (D-06): the shapes above put each normative radius on the closest M3 slot; Phase 6 components may also pass explicit shapes. Both fine — components must render at the normative radii.

```kotlin
// Option A (recommended for P1): Modifier.shadow with token shadow color.
// Source: androidx-main Shadow.kt — signature verified:
// fun Modifier.shadow(elevation: Dp, shape: Shape = RectangleShape,
//     clip: Boolean = elevation > 0.dp,
//     ambientColor: Color = DefaultShadowColor, spotColor: Color = DefaultShadowColor)
fun Modifier.softShadow(shape: Shape, dark: Boolean): Modifier =
    shadow(
        elevation = 6.dp,   // approximate: M3 renders blur/offset from elevation
        shape = shape,
        ambientColor = if (dark) SoftCleanTokens.ShadowDark else SoftCleanTokens.ShadowLight,
        spotColor = if (dark) SoftCleanTokens.ShadowDark else SoftCleanTokens.ShadowLight,
    )

// Option B (exact spec compliance): drawBehind — full control of blur 24/16, offset (0,8)/(0,6)
// via DrawScope.drawIntoCanvas { paint.asFrameworkPaint().setShadowLayer(blur, dx, dy, color) }.
```

Both options are explicitly permitted by docs/02 §1.2 ("`Modifier.shadow()` Material 3 ou `drawBehind` custom"). **Recommendation: Option A for P1** — single shadow (neumorphism prohibition respected structurally), token-driven colors, zero custom rendering; Option B exists if visual review against docs/02 §0 finds the elevation approximation insufficient (judgment criterion, end-of-phase visual check).

```kotlin
// presentation/theme/Theme.kt — D-05 semantics: SYSTEM follows, LIGHT/DARK override
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ShortifyLocalTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),  // then override slots from tokens
        shapes = SoftCleanShapes,
        content = content,
    )
}
```

Map tokens onto slots after the base builder: `colorScheme = base.copy(background = bg, surface = bg, surfaceContainer/card colors = card, onBackground/onSurface = primaryText, onSurfaceVariant = secondaryText, primary = accent, ...)`. Exact slot choices are discretionary (D-06); the acceptance check inspects the *theme code* for the normative hex values.

### Pattern 7 — DataStore persistence (D-05)

```kotlin
// data (or presentation) — current DataStore 1.2.1 preferences idiom
private val Context.dataStore by preferencesDataStore(name = "settings")
val THEME_MODE_KEY = stringPreferencesKey("theme_mode")   // store enum name; absent = SYSTEM

class ThemeRepository(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { prefs -> prefs[THEME_MODE_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM }
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }
}
```

No schema/migration concerns in P1; DB name `shortify_local.db` and secure storage are separate (Phase 3) — theme must NOT go into Room or EncryptedSharedPreferences (docs/05 §1.3: non-sensitive → DataStore).

### Pattern 8 — Navigation shell

```kotlin
// Navigation Compose 2.10.0, three string routes (simplest P1 slice; type-safe routes also fine on 2.10)
val TABS = listOf(
    TabSpec("accueil", R.string.tab_accueil, Icons.Filled.Home),
    TabSpec("historique", R.string.tab_historique, Icons.Filled.History),
    TabSpec("parametres", R.string.tab_parametres, Icons.Filled.Settings),
)
// Scaffold(bottomBar = { NavigationBar { ... NavigationBarItem(selected = currentRoute == tab.route, onClick = { navController.navigate(tab.route) { launchSingleTop = true; restoreState = true; popUpTo(navController.graph.findStartDestination().id) { saveState = true } } }) } })
```

All labels from `strings.xml` (`tab_accueil`/`tab_historique`/`tab_parametres`), all alignments `Start`/`End`, no `Left`/`Right`, `supportsRtl="true"` in the manifest. Default AS-template launcher icon (mipmap) — final branding is Phase 7.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JDK provisioning | Manual install docs / JAVA_HOME scripts | foojay resolver 1.0.0 + `kotlin { jvmToolchain(17) }` | D-02; reproducible on any machine; downloads land in Gradle User Home `jdks/` |
| Worker injection | Custom WorkerFactory reflection | `androidx.hilt:hilt-work` + `HiltWorkerFactory` | Edge cases (assisted injection, scoping) already solved |
| Schema export JSON | Hand-written schema files | Room Gradle plugin `schemaDirectory` (or KSP arg) | Compile-time generated, diffable, migration-testable in Phase 3 |
| Persistence | SharedPreferences + custom async | DataStore Preferences | Atomic writes, Flow-first, coroutine-safe (spec forbids theme in secure storage anyway) |
| Checksum capture | Copy hash from a website | `curl …aar.sha256` + `sha256sum` on the Gradle-cached AAR | Two independent sources; scriptable in the acceptance check |
| Theme plumbing | CompositionLocal hand-rolled read/write | DataStore Flow → state → MaterialTheme | D-05 semantics need one source of truth; flows survive process death |

## Common Pitfalls

### Pitfall 1: Applying `kotlin-android` while AGP 9 built-in Kotlin is on
**What goes wrong:** Build fails at configuration time: `Cannot add extension with name 'kotlin'…` or `plugin is no longer required for Kotlin support since AGP 9.0`.
**Why it happens:** AGP 9 defaults `builtInKotlin=true`; its embedded Kotlin is 2.2.10 — silently wrong compiler vs errata even if it *were* usable.
**How to avoid:** Set BOTH `android.builtInKotlin=false` and `android.newDsl=false` in `gradle.properties`; apply `org.jetbrains.kotlin.android` 2.4.10 normally.
**Warning signs:** any catalog entry for kotlin "unused", or the two error strings above.

### Pitfall 2: Stale official docs — `getWorkManagerConfiguration()` method
**What goes wrong:** Copying the Hilt/WorkManager doc snippet verbatim fails to compile against WorkManager 2.11.2.
**Why it happens:** docs pages still show the pre-2.9 method; the androidx source declares only `public val workManagerConfiguration: Configuration` in `Configuration.Provider`.
**How to avoid:** use the property override shown in Pattern 5. **Warning signs:** "override fun getWorkManagerConfiguration' overrides nothing".

### Pitfall 3: `gradlew` broken by CRLF on Windows/Git Bash
**What goes wrong:** after a fresh clone on Windows with `core.autocrlf=true`, `./gradlew` dies with `$'\r': command not found` or `bad interpreter`.
**Why it happens:** Git checked the `gradlew` sh script out with CRLF.
**How to avoid:** commit `.gitattributes` with `gradlew text eol=lf` (and `*.bat text eol=crlf`) **in the same commit as the wrapper**; the wrapper must be generated/normalized with LF endings. `./gradlew` runs fine from Git Bash when LF and `JAVA_HOME` are set (both true here).
**Warning signs:** the error strings above on first `./gradlew` invocation.

### Pitfall 4: Version catalog cannot feed the settings plugins block
**What goes wrong:** trying `alias(libs.plugins.foojay)` inside `settings.gradle.kts` — version catalogs aren't available to the settings build script's own plugins block.
**How to avoid:** single documented inline version `id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"`. Surface as the one sanctioned exception to "zéro version inline".

### Pitfall 5: Room schema directory not committed / lost
**What goes wrong:** acceptance "schéma JSON commité" fails because `app/schemas/` was gitignored by the Android template (`*.json` rules or IDE caches) or the schemaLocation arg pointed at `build/`.
**How to avoid:** point `room { schemaDirectory("$projectDir/schemas") }` outside `build/`, verify `app/schemas/com.shortifylocal.ai.data.local.room.AppDatabase/1.json` exists post-build and is not gitignored.
**Warning signs:** empty schemas dir after `assembleDebug`.

### Pitfall 6: Fake confidence in "arm64-only" fork
**What goes wrong:** planner adds emulator workarounds (ARM system images, `abiFilters`, splits) for a constraint that doesn't exist.
**How to avoid:** the 8.1.7 AAR contains `jni/arm64-v8a/` AND `jni/x86_64/` (verified by unzip listing this session). Keep `abiFilters` unset; x86_64 emulator installs natively. APK debug will be large (~100 MB, both ABIs) — expected, not a bug.

### Pitfall 7: Accidental prohibition violations (grep-fail criteria)
**What goes wrong:** one hardcoded string, one `Alignment.Left`, one `fallbackToDestructiveMigration()`, one `kapt`, one `com.arthenica` in a comment fails an acceptance grep.
**How to avoid:** never import `androidx.compose.ui.Modifier`-adjacent `Alignment.Left/Right` (use `Start`/`End`); scaffold contains zero instances of the banned tokens — and keep it that way; catalog comment references the fork by its live coordinate only.
**Warning signs:** run the grep battery from Validation Architecture before each commit.

### Pitfall 8: Toolchain/daemon JVM mismatch surprises
**What goes wrong:** confusion when `java -version` (Microsoft OpenJDK 17.0.10 on PATH) differs from `JAVA_HOME` (Android Studio JBR = OpenJDK **25.0.3**).
**How to avoid:** this is fine by design — Gradle daemon may run on JBR 25 (AGP 9.4 targets the Studio generation shipping that JBR), while compilation uses the declared toolchain 17; foojay provisions/locates a JDK 17 for the toolchain and Gradle auto-detects installed MSIs. Do NOT commit `org.gradle.java.home` (machine-specific, breaks fresh clone).
**Warning signs:** toolchain log lines about provisioning a JDK 17 on first build.

### Pitfall 9: Expecting docs-version `androidx.hilt` artifacts
**What goes wrong:** copying `androidx.hilt:hilt-work:1.0.0` / `hilt-compiler:1.3.0` from the official Hilt docs page (still showing those) while registry latest stable is **1.4.0 for both**.
**How to avoid:** pin 1.4.0 (both stable, registry-verified); docs lag the registry.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AGP applies `org.jetbrains.kotlin.android` | AGP 9 built-in Kotlin (default ON); opt-out via 2 flags | AGP 9.0 | Opt-out is a documented transition path — removal planned in AGP 10; migration to built-in Kotlin becomes future tech debt (flag, don't fix in P1) |
| Room 2.x (`androidx.room:*`) | Room 3.0 released as **`androidx.room3:room3-runtime`** 3.0.2 (docs default to it, updated 2026-08-26) | 2026 | Errata pins Room 2.8.4 — 2.x docs are now legacy/stale (v2 page still shows 2.6.1). Any upgrade = errata amendment (migration guide `room/migration-2-to-3` exists), never a local choice |
| `Configuration.Provider` method override | Kotlin **property** `workManagerConfiguration` | WorkManager 2.9.0 | Official doc snippets still stale — use property |
| kapt / KSP1 | KSP2 standalone versioning (2.3.11) | KSP 2.x | Prohibition already in spec; built-in Kotlin is kapt-incompatible anyway |
| `com.arthenica` ffmpeg-kit | `dev.ffmpegkit-maintained` fork publishes `.aar.sha256` + GPG sig | 2026 | Checksum pinning is directly supportable by registry artifacts |

## Runtime State Inventory

**Skipped — greenfield phase.** No rename/refactor/migration; repository contains no runtime state (no code, no datastores, no services) — verified by the scout (CONTEXT.md code_context: "aucun `.kt`, aucun Gradle, aucun manifest").

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Room Gradle plugin DSL `room { schemaDirectory("$projectDir/schemas") }` (plugin id+version are VERIFIED; exact DSL shape from training knowledge) | Pattern 3 | Build-config error at first compile; trivial fix or fallback to `ksp { arg("room.schemaLocation", …) }` — one line |
| A2 | KSP extension `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` as fallback shape | Pitfall 5 / A1 | Same as A1 (fallback of the fallback) |
| A3 | Empty-entities `@Database(entities = [], version = 1, exportSchema = true)` compiles and emits `1.json` | Pattern 3/5 | Rare corner; if rejected, add a throwaway entity — but that violates "zero entities"; escalate to spec owner. Low probability (Room historically supports empty DBs) |
| A4 | M3 Shapes slot mapping (16→extraSmall/small, 24→medium, 28→large, 32→extraLarge) | Pattern 6 | Pure design decision (D-06 discretion); no API risk |
| A5 | `Modifier.shadow(elevation=6.dp)` visually approximates blur 24 / offset (0,8) acceptably | Pattern 6 | Visual-review judgment at phase gate; Option B (drawBehind) is the sanctioned exact-spec fallback |
| A6 | Foojay resolver successfully provisions JDK 17 on this machine if toolchain detection finds none | Environment | Low risk locally (JDK 17.0.10 already on PATH, Gradle detects common Windows locations); foojay is the documented mechanism — first build confirms |
| A7 | Catalog entries for later-phase deps (ads, UMP, ML Kit, JitPack extractor…) can sit unresolved in the catalog without a build cost | Pattern 4 | None — catalog entries resolve lazily on first reference; JitPack repo deliberately deferred to Phase 4 |

## Open Questions (RESOLVED)

1. **Foojay inline version vs "zéro version inline" constraint**
   - What we know: Gradle cannot resolve catalog aliases in `settings.gradle.kts`' own plugins block.
   - What's unclear: whether the user accepts the single documented inline exception.
   - Recommendation: proceed with `version("1.0.0")` + TOML comment; mention in plan (no user gate required for a Gradle-platform limitation).
   - **RESOLVED (2026-09-05):** 01-01-PLAN.md Task 1 — le bloc `plugins` du settings déclare foojay `1.0.0` inline avec le commentaire d'exception documentée ; Task 3 audite « zéro version inline » en tolérant uniquement cette occurrence.

2. **Physical device not currently attached** (`adb devices` empty this session)
   - What we know: D-03 requires an ARM64 Android 13+ phone over adb USB for R7's second target.
   - What's unclear: availability at execution time.
   - Recommendation: emulator fully validates everything except the literal "physical ARM64" acceptance line; keep device checks as end-of-phase manual verification (matches `human_verify_mode: end-of-phase`).
   - **RESOLVED (2026-09-05):** 01-04-PLAN.md Task 3 — émulateur comme cible automatisée d'install/launch ; l'appareil physique ARM64 passe en vérification humaine de fin de phase (conforme `human_verify_mode: end-of-phase`).

3. **`allowBackup` default (true) in P1**
   - What we know: SEC-04 sets `allowBackup=false` in Phase 7; P1 stores only the theme.
   - Recommendation: leave default in P1 (spec has no P1 criterion), or set `false` now as free forward-compatibility — planner's call; zero risk either way.
   - **RESOLVED (2026-09-05):** 01-01-PLAN.md Task 2 — le manifest pose `android:allowBackup="false"` dès P1 (forward-compat SEC-04 gratuite, zéro risque).

## Environment Availability

Probed this session (Windows 10.0.26200, Git Bash):

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 (PATH) | Toolchain compile | ✓ | OpenJDK 17.0.10 (Microsoft) | foojay auto-download |
| JDK 25 (JAVA_HOME) | Gradle daemon / Studio JBR | ✓ | OpenJDK 25.0.3 JBR | — (by design, see Pitfall 8) |
| Android SDK | AGP | ✓ | `$LOCALAPPDATA/Android/Sdk`, `ANDROID_HOME` set | — |
| Platform android-36 | compileSdk 36 | ✓ | installed | — |
| Platform android-33 | minSdk floor | ✓ | installed | — |
| Build-tools 36.0.0 | AGP 9.4 default | ✓ | installed | — |
| NDK 28.2.13676358 | AGP 9.4 default (Phase 2 real use) | ✓ | installed | — |
| SDK licenses accepted | auto-download of missing pieces | ✓ | all licenses present | — |
| adb | install/launch checks | ✓ | 1.0.41 (37.0.1-15733141) | — |
| Emulator + AVDs | first verification target | ✓ | AVDs `Medium_Phone_2`, `Pixel_9a` on android-36 google_apis_playstore images | physical device |
| Physical ARM64 Android 13+ device | R7 second target (D-03) | ✗ (not connected now) | — | end-of-phase manual step |
| Global Gradle | must be ABSENT (acceptance) | ✓ correctly absent | — | wrapper only |

**Missing dependencies with no fallback:** none blocking scaffold work; the physical device is an execution-time requirement only for acceptance line 4's second target.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 via AGP default unit-test slot [VERIFIED: repo1.maven.org] |
| Config file | none needed (AGP convention `src/test/java`) |
| Quick run command | `./gradlew :app:testDebugUnitTest` |
| Full suite command | `./gradlew :app:assembleDebug :app:testDebugUnitTest` |

This phase's requirements are overwhelmingly **mechanical/deterministic** — the validation battery is greps + build + adb, not unit tests. One trivial unit test (e.g., `ThemeMode` round-trip through enum names) keeps the test lane warm.

### Phase Requirements → Test Map
| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| R1 | Fresh clone builds; catalog = errata E2 1:1 | build + diff script | `./gradlew :app:assembleDebug`; catalog↔E2 comparison (script or manual checklist) | ❌ Wave 0 (script) |
| R2 | 3 tabs navigable; tokens in theme; no Left/Right; no hardcoded strings | smoke (adb) + grep | `adb shell am start -n com.shortifylocal.ai/.MainActivity`; `grep -rnE "Alignment\.(Left|Right)|TextAlign\.(Left|Right)" app/src/main/java` (expect 0); `grep -rn '"' app/src/main/java/com/shortifylocal/ai/presentation --include=*.kt` reviewed for literals | ❌ Wave 0 (script) |
| R3 | Theme toggle survives force-stop | scripted manual (adb steps) + judgment | `adb shell am force-stop com.shortifylocal.ai` → relaunch → visual check | manual-only (visual) — justified: theme state is perceptual |
| R4 | `domain/` pure Kotlin | grep | `grep -rnE "^import (android|androidx)\." app/src/main/java/com/shortifylocal/ai/domain/` (expect 0) | ❌ Wave 0 |
| R5 | Hilt live; Room schema committed; no destructive migration | grep + artifact check | `ls app/schemas/com.shortifylocal.ai.data.local.room.AppDatabase/1.json`; `grep -rn "fallbackToDestructiveMigration\|kapt" app/build.gradle.kts gradle/` (expect 0) | ❌ Wave 0 |
| R6 | Fork resolves; checksum matches; arthenica banned | grep + checksum script | `grep -rn "com\.arthenica" settings.gradle.kts build.gradle.kts app/build.gradle.kts gradle/` (expect 0); `curl -s <aar>.sha256` vs `sha256sum` of the AAR in `~/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained/...` | ❌ Wave 0 |
| R7 | Install + launch on emulator AND physical | scripted manual (adb) | `adb install -r app/build/outputs/apk/debug/app-debug.apk` + `am start` on both targets | manual-only (hardware) |
| — | Manifest prohibitions | grep | `grep -nE "READ_MEDIA|READ_EXTERNAL_STORAGE|ACCESS_.*_LOCATION|READ_CONTACTS|usesCleartextTraffic" app/src/main/AndroidManifest.xml` (expect 0) | ❌ Wave 0 |

Key adb vocabulary (all verified available): `adb devices`, `adb install -r`, `adb shell am start -n com.shortifylocal.ai/.MainActivity`, `adb shell am force-stop com.shortifylocal.ai`.

### Sampling Rate
- **Per task commit:** the grep battery (instant) + targeted build task
- **Per wave merge:** `./gradlew :app:assembleDebug` green (first run = multi-minute, cached runs fast; ffmpeg AAR 37 MB downloads once)
- **Phase gate:** full suite + emulator install/launch + theme-persistence check green before `/gsd:verify-work`; physical-device pass at end-of-phase human verification

### Wave 0 Gaps
- [ ] Verification script (bash, Git Bash compatible) bundling the grep battery + checksum check — covers R2/R4/R5/R6/mechanical acceptance
- [ ] `.gitattributes` (gradlew LF) — must land with the wrapper commit
- [ ] `app/schemas/` directory + `.gitkeep` committed before first Room build
- [ ] Test framework install: none needed beyond the catalog `junit` entry (AGP provides the lane)

*(No pre-existing test infrastructure — greenfield.)*

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1` (config.json). P1 exposes almost no attack surface: no network, no user input, no sensitive storage, no permissions.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | n/a (no accounts — local-first) |
| V3 Session Management | no | n/a |
| V4 Access Control | no | n/a (no privileged actions) |
| V5 Input Validation | marginal | No user input in P1 shell beyond the theme toggle; URL validation regex is Phase 4 (`util/YouTubeRegex`) |
| V6 Cryptography | no | None in P1; SEC-01/02 land Phase 3 (never hand-roll) |
| V14 Config (manifest/hardening) | yes | Minimal manifest: zero permissions, no `usesCleartextTraffic` attribute, `exported="true"` only on the launcher activity |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Dependency confusion / dead-artifact mirror (slopsquatting `ffmpeg-kit`) | Tampering (supply chain) | Pinned coordinates + recorded SHA-256 (verified vs two independent sources), Maven Central only, no JitPack repo in P1, no `-SNAPSHOT` |
| Cleartext traffic downgrade | Information disclosure | Manifest never sets `usesCleartextTraffic` (default HTTPS-only); OkHttp config in Phase 4 reinforces |
| Permission overreach creep | Information disclosure | Acceptance grep for banned permission families; P1 declares none |
| Debug-exported surface | Elevation | Debug APK local-install only; release hardening (R8, allowBackup=false) is SEC-04/Phase 7 |

## Sources

### Primary (HIGH confidence — fetched this session)
- `repo1.maven.org/maven2/dev/ffmpegkit-maintained/ffmpeg-kit-full-gpl/8.1.7/` — directory listing (all checksum artifacts + GPG `.asc`), `.aar.sha256` content, AAR downloaded (36,988,198 B), SHA-256 recomputed and matched, `unzip -l` ABI listing (arm64-v8a + x86_64, 10 libs each)
- `developer.android.com/build/releases/gradle-plugin` — AGP 9.4.0 compat (Gradle ≥ 9.6.0, JDK 17, build-tools 36.0.0, NDK 28.2.13676358, API max 37)
- `developer.android.com/build/migrate-to-built-in-kotlin` — built-in Kotlin default, exact opt-out flags, incompatibility errors, kapt incompatibility
- `dl.google.com/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0.pom` — embedded `kotlin-gradle-plugin 2.2.10`
- `docs.gradle.org/current/userguide/toolchains.html` — foojay snippet (plugin id, version 1.0.0, settings placement, `java { toolchain }`)
- `plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/maven-metadata.xml` — `<release>1.0.0</release>`
- `dl.google.com/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom` — material3 1.4.0, material-icons-extended 1.7.8
- Google Maven maven-metadata: `androidx/hilt/{hilt-work,hilt-compiler,hilt-navigation-compose}` (1.4.0), `androidx/activity/activity-compose` (1.13.0), `androidx/lifecycle/lifecycle-runtime-compose` (2.11.0), `androidx/core/core-ktx` (1.19.0), `androidx/room/androidx.room.gradle.plugin` (2.8.4 marker)
- github.com/androidx/androidx (androidx-main) `work/work-runtime/.../Configuration.kt` — `interface Provider { public val workManagerConfiguration: Configuration }`; `compose/ui/ui/.../Shadow.kt` — full `Modifier.shadow` signature with ambientColor/spotColor
- `developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration` — initializer-removal XML verbatim; `developer.android.com/training/dependency-injection/hilt-jetpack` — HiltWorker pattern (versions on page stale → registry wins)
- `developer.android.com/develop/ui/compose/compiler` — compose plugin "version matches your Kotlin version"
- `developer.android.com/training/data-storage/room` (+ `/v2` legacy) — Room 3.0 is the doc default (`androidx.room3` 3.0.2); 2.x page stale at 2.6.1

### Secondary (MEDIUM)
- `.planning/research/STACK.md` (2026-09-04) — all other E2 versions; its "arm64-only (à confirmer)" note is now **refuted** by the AAR inspection above

### Tertiary (LOW)
- None — no training-data-only claims survive into the stack tables (see Assumptions Log for the 7 [ASSUMED] residuals)

## Metadata

**Confidence breakdown:**
- Gradle/toolchain shape: HIGH — every flag/plugin/version read from official registries or docs this session
- Hilt/WorkManager/Room wiring: HIGH for APIs (androidx source), MEDIUM for the Room plugin DSL shape (A1, one-line fallback exists)
- Theming: HIGH for tokens (normative doc quoted) and shadow API (source-verified); slot mapping is discretionary by design
- Supply chain: HIGH — artifact downloaded, hash double-verified, signature available

**Research date:** 2026-09-05
**Valid until:** ~2026-10-05 (stable pinned stack; watch KSP/Hilt patch releases only if a build failure appears)
