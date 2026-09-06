# Phase 2 : Fondations natives (spike critique) — Carte des patterns

**Mapped:** 2026-09-06
**Files analyzed:** 19 nouveaux + 3 modifiés
**Analogs found:** 14 / 22 (concrets) — les 8 restants sont des primaires natifs C/CMake/JNI sans équivalent dans le repo (patterns RESEARCH.md § Code Examples)

**Particularité de la phase :** presque tout est NOUVEAU (module Gradle `whisper-native/`, package `data/native/whisper/`, première suite androidTest du dépôt, scripts P2). Les analogues existants portent donc sur les **conventions** (structure Gradle, discipline catalogue, batterie bash, style Kotlin/tests commentés en français avec références décisionnelles D-xx) et non sur la logique métier, qui n'a pas d'équivalent (JNI/whisper.cpp = première occurrence).

---

## File Classification

| Fichier (nouveau/modifié) | Rôle | Data flow | Analogue le plus proche | Qualité du match |
|---------------------------|------|-----------|-------------------------|------------------|
| `whisper-native/build.gradle.kts` (NOUVEAU) | config (module android library + externalNativeBuild) | — | `app/build.gradle.kts` | role-match (application → library) |
| `whisper-native/src/main/cpp/CMakeLists.txt` (NOUVEAU) | config (build natif CMake) | — | aucun (RESEARCH.md § Pattern 1 + Code Examples) | none |
| `whisper-native/src/main/cpp/whisper_jni.c` (NOUVEAU) | native bridge JNI | transform (cs→ms, abort trampoline) | aucun (RESEARCH.md § Code Examples) | none |
| `whisper-native/src/main/AndroidManifest.xml` (NOUVEAU) | config (manifest library minimal) | — | `app/src/main/AndroidManifest.xml` | role-match (structure only) |
| `settings.gradle.kts` (MODIFIÉ : `include(":whisper-native")`) | config | — | lui-même (`include(":app")` ligne 31) | exact |
| `gradle/libs.versions.toml` (MODIFIÉ : ndk, androidxTest*, plugin android-library) | config | — | lui-même (blocs commentés P2/Phase 4 lignes 26-31, 68-69) | exact |
| `build.gradle.kts` racine (MODIFIÉ : `alias(libs.plugins.android.library) apply false`) | config | — | lui-même (lignes 1-10) | exact |
| `app/build.gradle.kts` (MODIFIÉ : dep `:whisper-native` + androidTest config) | config | — | lui-même (bloc dependencies lignes 39-62) | exact |
| `app/src/main/java/com/shortifylocal/ai/data/native/whisper/WhisperTranscriber.kt` (NOUVEAU) | service (couche data, wrapper JNI) | request-response (suspend one-shot) | `app/src/main/java/com/shortifylocal/ai/data/local/preferences/ThemeRepository.kt` | role-match |
| `app/src/main/java/com/shortifylocal/ai/data/native/whisper/WordTimestamp.kt` (NOUVEAU) | model (data class) | — | aucun data class en prod ; conventions via `ThemeRepository.kt` KDoc | partial |
| `app/src/main/java/com/shortifylocal/ai/data/native/whisper/WhisperError.kt` (NOUVEAU) | model (sealed exhaustif) | — | aucun sealed dans le repo (RESEARCH.md § Code Examples) | none |
| `app/src/androidTest/java/com/shortifylocal/ai/FfmpegForkTest.kt` (NOUVEAU) | test instrumentation | event-driven (callbacks FFmpegKit) | `app/src/test/.../ThemeModeTest.kt` (conventions JUnit4) | role-match (conventions) |
| `app/src/androidTest/java/com/shortifylocal/ai/WordTranscriptionTest.kt` (NOUVEAU) | test instrumentation | transform/assert | `ThemeModeTest.kt` | role-match (conventions) |
| `app/src/androidTest/java/com/shortifylocal/ai/AbortPropagationTest.kt` (NOUVEAU) | test instrumentation | event-driven (abort callback) | `ThemeModeTest.kt` | role-match (conventions) |
| `app/src/androidTest/java/com/shortifylocal/ai/TtsAssetGenerationTest.kt` (NOUVEAU) | test instrumentation (TTS + garde D-09) | file-I/O | `ThemeModeTest.kt` + `verify_emulator_p1.sh` (pattern garde/abort) | role-match (conventions) |
| `app/src/androidTest/java/com/shortifylocal/ai/WhisperBenchmarkTest.kt` (NOUVEAU) | test instrumentation (benchmark off par défaut) | batch | `ThemeModeTest.kt` | role-match (conventions) |
| `scripts/fetch_models_p2.sh` (NOUVEAU) | utility (script hôte, téléchargement + SHA-256 + push adb) | batch / file-I/O | `scripts/verify_p1.sh` (checksum SHA-256, fail/exiger) + `verify_emulator_p1.sh` (adb_, ANDROID_SERIAL) | exact (même famille bash) |
| `scripts/verify_p2.sh` (NOUVEAU) | utility (batterie de vérification hôte) | verification | `scripts/verify_p1.sh` | exact |
| manifest SHA-256 modèles (NOUVEAU, ex. `scripts/models_manifest_p2.txt`) | config (pins D-01) | — | checksum AAR épinglé dans `libs.versions.toml` lignes 27 + `verify_p1.sh` ligne 17 | role-match |
| (option) `scripts/gen_tts_asset_recipe.md` (NOUVEAU) | doc/recette | — | `.planning/` docs (non normatif) | partial |

---

## Pattern Assignments

### `whisper-native/build.gradle.kts` (config, module android library)

**Analog :** `app/build.gradle.kts` — conventions modules Gradle du dépôt : plugins par alias du catalogue (jamais de version inline), toolchain posée explicitement, commentaires français référencant les décisions/errata, dépendances uniquement via `libs.*`.

**Plugins par alias** (lignes 1-8 de `app/build.gradle.kts`) :
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ...
}
```
→ Transposition pour `whisper-native` : `alias(libs.plugins.android.library)` (nouvelle entrée catalogue à créer — cf. Shared Pattern 1).

**Toolchain + commentaire décisionnel** (ligne 33 de `app/build.gradle.kts`) :
```kotlin
kotlin { jvmToolchain(17) }   // D-02 : toolchain 17 ; foojay provisionne si absente
```
→ Reproduire le style : valeur de config + commentaire `// E2 / D-xx : pourquoi`.

**`ndkVersion` + `externalNativeBuild` à créer** — aucune occurrence dans le repo (première intégration NDK) ; discipline à respecter : `ndkVersion = libs.versions.ndk.get()` (zéro version inline, cf. Shared Pattern 1). Blocs attendus (DSL AGP) :
```kotlin
android {
    namespace = "com.shortifylocal.ai.whisper_native"   // pas le namespace :app
    compileSdk = 37   // même amendement E2 que :app (commentaire identique requis)
    defaultConfig {
        minSdk = 33
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }   // SPEC : 2 ABIs, x86_64 build-only
        externalNativeBuild { cmake { arguments += listOf("-DGGML_OPENMP=OFF") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
```

**Dépendance croisée modifiée** dans `app/build.gradle.kts` (bloc dependencies, lignes 39-62) — pattern d'insertion existant avec commentaire de bord :
```kotlin
    implementation(libs.ffmpegkit.full.gpl)          // jamais exécutée en P1 ; .so arm64+x86_64 embarqués
    testImplementation(libs.junit)
```
→ Ajouter `implementation(project(":whisper-native"))` + les `androidTestImplementation(libs.androidx.test.runner)` / `androidTestImplementation(libs.androidx.test.ext.junit)` + `defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` — première suite androidTest du dépôt (aucun `app/src/androidTest/` existant, confirmé).

---

### `whisper-native/src/main/AndroidManifest.xml` (config, manifest library minimal)

**Analog :** `app/src/main/AndroidManifest.xml` — style XML du dépôt (déclaration `xmlns:android`, pas d'attribut superflu). Le manifest library doit rester MINIMAL (pas d'`<application>` riche, pas de permissions — le SPEC P1 interdit déjà tout élargissement de permissions) :
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
```
→ Un manifest library vide (2-3 lignes) suffit ; AGP fournit le reste.

---

### `settings.gradle.kts` (MODIFIÉ)

**Analog :** lui-même. Le point d'extension est déjà prêt en fin de fichier (lignes 30-31) :
```kotlin
rootProject.name = "ShortifyLocal AI"
include(":app")
```
→ Ajouter `include(":whisper-native")` juste après — une ligne, aucun changement de repositories (le submodule n'a pas besoin de dépôt Maven).

---

### `gradle/libs.versions.toml` (MODIFIÉ)

**Analog :** lui-même — la discipline « source unique + provenance consignée » est déjà formalisée dans l'en-tête (lignes 1-4) :
```toml
# Source unique des versions — errata E2 (docs/ERRATA-2026-09-05.md).
# Versions vérifiées contre registres officiels le 2026-09-05 (Google Maven, Maven Central,
# services.gradle.org, plugins.gradle.org). Zéro version inline ailleurs dans le dépôt,
# excepté le resolveur foojay dans settings.gradle.kts (limitation Gradle, documentée).
```

**Pattern « entrée épinglée avec SHA-256 + date de vérification »** (lignes 26-30) — à reproduire verbatim pour l'entrée NDK :
```toml
# ffmpeg-kit : dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl — fork maintenu, coordonnée vivante.
# AAR 8.1.7 SHA-256 = 374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b
# (repo1.maven.org 2026-07-12, 36 988 198 octets, ABI arm64-v8a + x86_64 —
#  vérification registre 2026-09-05, clause de fraîcheur appliquée et notée)
ffmpegKit = "8.1.7"
whisperCpp = "b4938"              # source seule (CMake/NDK) — Phase 2, entrée commentaire
```
→ L'entrée `whisperCpp = "b4938"` existe déjà (ligne 31) ; ajouter `ndk = "29.0.14206865"` (ou `28.2.13676358` — décision plan, cf. RESEARCH.md Open Question 1) avec le même commentaire de provenance/date, puis `androidxTestRunner = "1.7.0"`, `androidxTestExtJunit = "1.3.0"`.

**Pattern « section phases ultérieures »** (lignes 68-69) — les entrées de P2 suivent la même convention :
```toml
# — Pipeline / phases ultérieures (entrées catalogue E2, résolues seulement quand référencées)
ffmpegkit-full-gpl = { module = "dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl", version.ref = "ffmpegKit" }
```

**Plugin `android-library` manquant** — le bloc `[plugins]` (lignes 80-87) n'a que :
```toml
android-application = { id = "com.android.application", version.ref = "agp" }
```
→ Ajouter `android-library = { id = "com.android.library", version.ref = "agp" }` (même ref `agp`), puis `alias(libs.plugins.android.library) apply false` dans `build.gradle.kts` racine (lignes 1-10, pattern « racine — alias seulement, aucune logique »).

---

### `app/src/main/java/com/shortifylocal/ai/data/native/whisper/WhisperTranscriber.kt` (service, request-response)

**Analog :** `app/src/main/java/com/shortifylocal/ai/data/local/preferences/ThemeRepository.kt` — le seul fichier Kotlin de production complet du repo ; il fixe les conventions de la couche data.

**Imports triés par groupe** (lignes 1-12) :
```kotlin
package com.shortifylocal.ai.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
// ...
import com.shortifylocal.ai.presentation.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
```
Ordre : `android.*` → `com.shortifylocal.ai.*` → `dagger/javax` → `kotlinx`. → `WhisperTranscriber.kt` suit le même tri (plus `kotlinx.coroutines` et `java.io.File`).

**KDoc français justifiant chaque décision** (lignes 20-24) — signature stylistique du dépôt :
```kotlin
/**
 * Source de vérité persistée du thème (D-05) — DataStore « settings » uniquement.
 * La préférence thème ne va JAMAIS dans Room ni dans le stockage chiffré
 * (non sensible ; le coffre-fort est une décision Phase 3, SEC-01).
 */
```
→ `WhisperTranscriber.kt` documente ainsi D-04/D-05/D-06 (contrat suspend one-shot, double circuit d'annulation, sealed errors) et les conversions cs→ms.

**Structure de classe** (lignes 25-28) — injection Hilt par constructeur ; le wrapper P2 reste exercé par instrumentation seule (pas de wiring DI requis, cf. CONTEXT § Reusable Assets), mais la forme de classe (constructeur explicite, encapsulation) reste la référence :
```kotlin
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
```

**Core pattern du wrapper** (contrat D-04/D-05/D-06, cf. RESEARCH.md § Code Examples — SQUELETTE à câbler au JNI, pas un pattern du repo) :
```kotlin
suspend fun transcribe(wav: File, model: File, abort: () -> Boolean): List<WordTimestamp> =
    withContext(Dispatchers.Default) { ... }   // ensureActive() relayé au trampoline + invokeOnCancellation
```

---

### `WordTimestamp.kt` + `WhisperError.kt` (model)

**Analog le plus proche :** aucun data class/sealed en production (domain/model et worker ne contiennent que des `.gitkeep`). Conventions à déduire de `ThemeRepository.kt` (KDoc FR + référence décisionnelle) et du test `ThemeModeTest.kt` (usage d'un type de domaine pur). Contrats verrouillés (RESEARCH.md § Code Examples) :
```kotlin
class WordTimestamp(val word: String, val startMs: Long, val endMs: Long)   // ms = cs × 10

sealed interface WhisperError {
    data class LoadFailed(val isOutOfMemory: Boolean) : WhisperError        // branche OOM distinguable (D-06)
    data object Aborted : WhisperError
    data class InferenceFailed(val code: Int) : WhisperError                // code négatif natif (-6/-8/-9…)
    data object InvalidInput : WhisperError
}
```

---

### Tests d'instrumentation `app/src/androidTest/java/com/shortifylocal/ai/*.kt` (5 classes)

**Analog (conventions) :** `app/src/test/java/com/shortifylocal/ai/presentation/theme/ThemeModeTest.kt` — seule suite de test du dépôt. Aucune androidTest n'existe (Wave 0 : à créer, avec `testInstrumentationRunner` + runner 1.7.0 / ext:junit 1.3.0 au catalogue).

**Imports JUnit4 minimalistes** (lignes 1-3) :
```kotlin
package com.shortifylocal.ai.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test
```

**KDoc référencant les décisions normatives + noms de test en backticks français** (lignes 6-21) :
```kotlin
/**
 * Tests de la sémantique D-05 : l'enum ThemeMode et sa logique pure de bascule.
 *
 * Sémantique normative (01-CONTEXT.md D-05) :
 * - tant qu'aucun choix explicite n'existe, le thème suit le système ;
 */
class ThemeModeTest {

    @Test
    fun `valueOf sur le nom fait un aller-retour exact pour les 3 valeurs`() {
```
→ Reproduire : KDoc citant le requirement SPEC (R2/R3/R4/R7/D-06) + `@Test fun \`mot-à-mot vs manifest respecte ±200 ms\`()`. JUnit4 pur (pas de runners custom) ; la garde triple assert (R6) et l'activation `bench=on` (D-07, arg instrumentation) sont des ajouts P2 sans analogue — cf. RESEARCH.md § Code Examples (triple assert) et Pitfall 7 (callbacks FFmpegKit : suspendre sur complétion de session, jamais toucher l'UI depuis les callbacks).

---

### `scripts/verify_p2.sh` (utility, batterie de vérification hôte)

**Analog :** `scripts/verify_p1.sh` — extension directe demandée par le SPEC R5. C'est l'analogue le plus fort de la phase.

**En-tête + contrat d'exécution** (lignes 1-15) :
```bash
#!/usr/bin/env bash
# verify_p1.sh — Batterie mécanique de vérification Phase 1 (plan 01-04, Task 1).
# ...
# Usage : bash scripts/verify_p1.sh   (depuis n'importe où — racine déduite du script)
# Exit 0 = « P1 OK » ; premier échec = « ÉCHEC: <check> » + exit 1.
# Aucun repli silencieux : un fichier absent est un échec, pas un 0.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
```

**Helpers `fail` / `check_present` / `exiger_fichier`** (lignes 21-24, 48-58) — à copier tels quels :
```bash
fail() {
  echo "ÉCHEC: $1" >&2
  exit 1
}

# check_present <description> <motif fixe> <fichier>
check_present() {
  local desc="$1" motif="$2" fichier="$3"
  if ! grep -qF "$motif" "$fichier"; then
    fail "$desc — « $motif » introuvable dans $fichier"
  fi
  echo "  [OK] présent — $desc"
}

exiger_fichier() {
  [ -f "$1" ] || fail "prérequis — fichier manquant : $1"
}
```

**Check SHA-256 épinglé** (lignes 112-119) — pattern exact pour le check « `.so` APK == `.so` AAR » et l'intégrité des modèles :
```bash
AAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained/" -name 'ffmpeg-kit-full-gpl-8.1.7.aar' 2>/dev/null | head -1)"
[ -n "$AAR" ] || fail "checksum — AAR ffmpeg-kit-full-gpl-8.1.7.aar introuvable dans ~/.gradle/caches/..."
SHA_ACTUEL="$(sha256sum "$AAR" | awk '{print $1}')"
if [ "$SHA_ACTUEL" != "$CHECKSUM_ATTENDU" ]; then
  fail "checksum — SHA-256 inattendu : $SHA_ACTUEL (attendu $CHECKSUM_ATTENDU) sur $AAR"
fi
echo "  [OK] checksum $SHA_ACTUEL — $AAR"
```

**Récapitulatif final avec compteur de sections** (lignes 121-122) :
```bash
echo ""
echo "P1 OK — 7/7 sections vertes : interdits build, interdits UI, pureté domain, manifest, catalogue E2, absence tranche Room, checksum fork."
```

→ `verify_p2.sh` : mêmes squelettes + sections nouvelles (unzip APK → `llvm-readelf -l` LOAD ≥ 0x4000/16384/32768 sur chaque `lib*.so`, `zipalign -v -c -P 16 4`, SHA-256 `.so` fork APK vs AAR, grep zéro workflow cloud, assert getprop triple, pull JSON benchmark). Motifs readelf : accepter `align 0x4000` ET `align 2**14` (RESEARCH.md § Code Examples).

---

### `scripts/fetch_models_p2.sh` (utility, batch/file-I/O + push adb)

**Analogs :** `scripts/verify_p1.sh` (vérification SHA-256, fail explicite) + `scripts/verify_emulator_p1.sh` (toute interaction adb).

**Wrapper adb paramétrable + garde Git Bash** (`verify_emulator_p1.sh` lignes 28-34) — OBLIGATOIRE pour le push modèles et les `getprop` :
```bash
# Git Bash (MSYS) réécrit les arguments commençant par « / » en chemins Windows
# (ex. /sdcard/x → C:/Program Files/Git/sdcard/x) — ce qui casse les chemins distants
# adb (pull, uiautomator dump). Inerte hors MSYS. (Fix Rule 3, plan 01-04 Task 2.)
export MSYS_NO_PATHCONV=1

# Toutes les commandes adb passent par ce wrapper (paramétrable par ANDROID_SERIAL)
adb_() { adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} "$@"; }
```

**Assert cible imposée via getprop** (`verify_emulator_p1.sh` lignes 48-50, 59-65) — pattern du triple assert hôte (R6) :
```bash
if [ -n "${ANDROID_SERIAL:-}" ]; then
  adb devices | grep -q "^${ANDROID_SERIAL}[[:space:]]" || fail "ANDROID_SERIAL=$ANDROID_SERIAL absent de « adb devices »"
  echo "-- Cible imposée : $ANDROID_SERIAL"
```
```bash
  BOOT="$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
```
→ Noter le `tr -d '\r'` systématique sur toute sortie `adb shell` (Windows) et la boucle bornée d'attente. Les `getprop` à ajouter : `ro.product.cpu.abi` = arm64-v8a, `ro.kernel.qemu` = vide/0, `ro.build.version.sdk` ≥ 33.

**Rejet d'erreur avec message actionnable** (`verify_emulator_p1.sh` lignes 71-80) — pattern pour l'échec de divergence SHA-256 (D-01) :
```bash
INSTALL_OUT="$(adb_ install -r "$APK" 2>&1)" || {
  echo "$INSTALL_OUT" >&2
  case "$INSTALL_OUT" in
    *INSTALL_FAILED_NO_MATCHING_ABIS*|*INSTALL_FAILED_CPU_ABI_INCOMPATIBLE*)
      echo "BLOCAGE ABI : escalade au propriétaire de spec — ne changer NI la cible NI les dépendances." >&2
```
→ `fetch_models_p2.sh` : même structure — téléchargement depuis les URLs HuggingFace officielles (RESEARCH.md § Installation), `sha256sum` vs manifest versionné, `fail "modèle X — SHA-256 divergent : attendu <pin>, obtenu <lu>"`, puis `adb_ push` vers `getExternalFilesDir` (repli `run-as` documenté, Pitfall 10).

**Manifest SHA-256 des modèles** : pattern du pin consigné — l'équivalent fichier du bloc `libs.versions.toml` lignes 26-30 + constante `verify_p1.sh` ligne 17 :
```bash
CHECKSUM_ATTENDU="374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b"
```
→ Fichier versionné listant `URL | taille | sha256 | source | date` (pas de TOFU, D-01).

---

## Shared Patterns

### 1. Discipline catalogue — zéro version inline
**Source :** `gradle/libs.versions.toml` (lignes 1-4, 26-31), `settings.gradle.kts` (lignes 1-5 — l'unique exception documentée), `build.gradle.kts` racine (lignes 1-2).
**Apply to :** `gradle/libs.versions.toml` (modifs), `whisper-native/build.gradle.kts`, `app/build.gradle.kts` (modifs), `build.gradle.kts` racine (modif).
- Toute version nouvelle (ndk, androidxTest runner/ext-junit, plugin android-library) passe par le catalogue, avec commentaire de provenance + date de vérification (copier le bloc ffmpegKit lignes 26-30 comme gabarit).
- Toute valeur inline hors catalogue = exception documentée par commentaire — à éviter.

### 2. Batterie bash mécanique (scripts hôte)
**Source :** `scripts/verify_p1.sh` (squelette complet) + `scripts/verify_emulator_p1.sh` (adb).
**Apply to :** `scripts/verify_p2.sh`, `scripts/fetch_models_p2.sh`.
- `set -euo pipefail` ; `ROOT` déduit de `BASH_SOURCE` ; `fail()` écrit « ÉCHEC: … » sur stderr + exit 1 ; « aucun repli silencieux : un fichier absent est un échec ».
- `export MSYS_NO_PATHCONV=1` AVANT tout adb (Git Bash sur cette machine) ; wrapper `adb_() { adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} "$@"; }` ; `tr -d '\r'` sur toute sortie `adb shell`.
- Helpers `check_present`/`check_absent`/`exiger_fichier` copiés de `verify_p1.sh` lignes 28-61.
- Sortie : `[OK]` par check, récap final « P2 OK — n/n sections vertes ».

### 3. Style Kotlin — KDoc français à référence décisionnelle
**Source :** `ThemeRepository.kt` (lignes 14-24), `ThemeModeTest.kt` (lignes 6-13).
**Apply to :** les 3 fichiers `data/native/whisper/` et les 5 classes androidTest.
- KDoc de chaque classe/contrat cite les décisions (D-04, D-05, D-06…) et l'errata (E2, E3) ; les noms de tests sont des backticks français descriptifs.
- Imports groupés : `android.*` → `com.shortifylocal.ai.*` → tiers → `kotlinx`/`java`.

### 4. Contrat d'erreur — pas d'exception comme chemin contractuel
**Source :** RESEARCH.md § Code Examples (aucun analogue repo — première occurrence). Voir aussi `verify_p1.sh` pour l'esprit « échec explicite » côté scripts.
**Apply to :** `WhisperTranscriber.kt`, `WhisperError.kt`, `AbortPropagationTest.kt`, `WhisperBenchmarkTest.kt`.
- `Result<List<WordTimestamp>, WhisperError>` sealed exhaustif ; « code natif négatif + flag abort posé » = `Aborted`, « négatif sans flag » = `InferenceFailed` ; OOM = branche distinguable de `LoadFailed` (D-06).

### 5. Conventions Gradle Kotlin DSL
**Source :** `app/build.gradle.kts` + `build.gradle.kts` racine.
**Apply to :** `whisper-native/build.gradle.kts`, modifs `:app`/racine.
- Plugins par `alias(libs.…)` ; racine = alias `apply false` seulement ; commentaires `// <réf décision> : raison` sur chaque valeur non évidente (ex. ligne 12 : `compileSdk = 37   // amendement E2 2026-09-05…`).

---

## No Analog Found

Fichiers sans équivalent dans le codebase (le planner DOIT utiliser les patterns RESEARCH.md, qui citent le code source whisper.cpp b4938 verbatim) :

| Fichier | Rôle | Data flow | Raison |
|---------|------|-----------|--------|
| `whisper-native/src/main/cpp/CMakeLists.txt` | config build natif | — | Premier (et seul) CMakeLists du dépôt ; squelette complet dans RESEARCH.md § Code Examples (options `WHISPER_BUILD_*`/`GGML_OPENMP=OFF`, garde submodule FATAL_ERROR, flags linker 16 KB) |
| `whisper-native/src/main/cpp/whisper_jni.c` | native bridge JNI | transform | Premier code C du dépôt ; trampoline abort_callback + signature `ggml_abort_callback` verbatim dans RESEARCH.md § Code Examples |
| `WordTimestamp.kt` / `WhisperError.kt` | model | — | Aucune data class/sealed en production (`domain/model/` vide, `.gitkeep`) ; contrat figé D-04/D-06 dans RESEARCH.md § Code Examples |
| Garde triple assert in-test (R6) + arg instrumentation `bench=on` | test harness | event-driven | Première instrumentation du dépôt ; implémentation (SystemProperties reflection, `AndroidJUnitRunnerArguments`) détaillée RESEARCH.md § Code Examples + Open Question/ASSUMED A5 |

## Metadata

**Analog search scope :** racine Gradle (`*.gradle.kts`, `gradle/`), `app/src/main/java/com/shortifylocal/ai/` (toutes couches), `app/src/test/`, `scripts/`, `app/src/main/AndroidManifest.xml` — tout le dépôt est petit (~5 fichiers Kotlin de production, dont 3 `.gitkeep`) ; recherche exhaustive.
**Files scanned :** 11 fichiers lus intégralement (`app/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts`, `scripts/verify_p1.sh`, `scripts/verify_emulator_p1.sh`, `ThemeRepository.kt`, `ThemeModeTest.kt`, `AndroidManifest.xml`, + sondages structure).
**Gate tracked-source :** tous les analogues nommés vérifiés `git ls-files` (11/11 tracked).
**Pattern extraction date :** 2026-09-06
