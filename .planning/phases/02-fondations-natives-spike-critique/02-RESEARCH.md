# Phase 2: Fondations natives (spike critique) - Research

**Researched:** 2026-09-06
**Domain:** NDK/CMake/JNI (whisper.cpp b4938), ffmpeg-kit fork 8.1.7, alignement 16 KB, instrumentation Android, méthodes de mesure on-device, TTS système
**Confidence:** HIGH (sources primaires : sources brutes du dépôt ggml-org/whisper.cpp au commit épinglé, AAR du fork inspecté localement, sources AOSP, developer.android.com)

## Summary

L'épin `whisperCpp = "b4938"` résout vers le commit complet `371b5a7561823ab2bb32142d2751e35e7534727b`, qui est **le commit de release de whisper.cpp v1.9.3** (2026-08-20) [VERIFIED: api.github.com/repos/ggml-org/whisper.cpp/commits/b4938]. L'API C nécessaire à la preuve mot-à-mot y est complète et a été lue verbatim dans `include/whisper.h` à ce commit : `whisper_full_params` expose `token_timestamps`, `max_len`, `split_on_word` ; les timestamps (tokens ET segments) sont des `int64_t` en **centisecondes** (×10 → ms) ; l'abort callback est le typedef ggml `bool (*)(void * data)`, invoqué après chaque passe d'encodeur et à chaque step de décodage, et un abort fait retourner à `whisper_full` un code **négatif** (-6/-8/-9) que le wrapper doit dissocier d'une vraie erreur. L'exemple Android officiel du dépôt (`examples/whisper.android`) donne le pattern d'intégration : un seul `.so` SHARED contenant ggml (statique) + whisper.cpp + le pont JNI, compilé via `externalNativeBuild` CMake ; à b4938, ggml gère explicitement Android (variants CPU `android_armv8.x`) et `GGML_OPENMP` vaut ON par défaut — à forcer à OFF pour un contrôle déterministe de `n_threads` en benchmark. Côté fork ffmpeg-kit, l'AAR 8.1.7 résolu dans le cache Gradle local expose exactement l'API `com.arthenica.ffmpegkit` attendue (`FFmpegKit.execute/executeAsync/cancel(sessionId)`, `FFprobeKit.getMediaInformation`, `ReturnCode.SUCCESS/CANCEL`, `Session.cancel()`), ses callbacks asynchrones tournent sur un pool fixe de 10 threads (jamais le main), **les 10 `.so` par ABI sont déjà alignés 16 KB (LOAD = 0x4000)**, et sa classe `WhisperKit` s'avère être un **stub « tier Pro »** sans `libwhisperkit.so` dans l'AAR full-gpl — elle ne peut ni ne doit servir de raccourci (pas de timestamps de tokens, whisper 1.7.5, échec `createFromFile` garanti).

Pour la vérification 16 KB, la doc officielle confirme : NDK r28+ aligne par défaut (les flags de l'errata restent des ceintures de sécurité inoffensives), le contrôle mécanique est `llvm-objdump -p`/`llvm-readelf -l` → LOAD `align 2**14` (0x4000/16384 ; 32768 accepté), et `zipalign -v -c -P 16 4` exige build-tools ≥ 35.0.0 (installés : 35→37). La dernière NDK stable est **r29 (`29.0.14206865`)** ; la machine a r28.2 (`28.2.13676358`) et une r30 pré-release (`30.0.14904198`) — le pin catalogue doit donc soit viser r29 stable (téléchargement), soit l'r28.2 déjà installée, les deux satisfaisant ≥ r28. Les mesures on-device ont une voie 100 % app-accessible : statut thermique catégoriel via `PowerManager.getCurrentThermalStatus()`/listener (les °C CPU in-app sont fermés — `HardwarePropertiesManager` jette `SecurityException` hors device owner), température °C via le sticky broadcast `ACTION_BATTERY_CHANGED` (`EXTRA_TEMPERATURE`), % batterie via `BATTERY_PROPERTY_CAPACITY`, pic RAM via `VmHWM` de `/proc/self/status`, stay-awake via `adb shell svc power stayon`. Enfin, le contrat TTS (`synthesizeToFile` = résultat de **mise en file** seulement, complétion via `onDone`/`onError(utteranceId, errorCode)`, `getMaxSpeechInputLength()`=4000, **aucun timestamp de mot** — `onRangeStart` optionnel côté moteur) rend la stratégie D-08 (synthèse mot-à-mot + silences 200 ms + concat FFmpeg → manifest par construction) non seulement faisable mais la seule voie robuste.

**Primary recommendation:** Construire `whisper-native/` sur le pattern de l'exemple officiel Android de b4938 (un `.so` par ABI : ggml statique + whisper + JNI), wrapper Kotlin suspend dans `data/native/whisper/` traduisant les centisecondes natives en ms, épingler la NDK r29 stable `29.0.14206865` (repli : r28.2 installée), et faire tourner toutes les assertions (16 KB, FFprobe, manifest ±200 ms, triple assert) dans `connectedDebugAndroidTest` + `verify_p2.sh` selon les patterns P1 déjà en place.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

### Modèles ggml (provenance, pin, stockage, variante)
- **D-01:** Les binaires Tiny/Base/Small sont obtenus par un **script hôte versionné `scripts/fetch_models_p2.sh`** (dev-time, hors app — respecte le MUST NOT réseau) : téléchargement depuis la source officielle ggerganov/whisper.cpp (HuggingFace) ; licence Whisper (MIT modifié) ∈ liste blanche E1, **vérifiée à l'exécution** ; SHA-256 des 3 fichiers épinglés au repo dans un **manifest versionné** (valeurs copiées depuis les sommes officielles publiées, source + date consignées — **pas de TOFU**) et vérifiés au téléchargement (échec si divergence) ; push via adb ; zéro `.bin` versionné au repo ; SHA-256 + URL + date également consignés dans `02-BENCHMARK.md` pour audit.
- **D-02:** Sur l'appareil, modèles, WAV et sorties de run vivent dans le **stockage externe app-scoped (`getExternalFilesDir`), persistant** — inspection adb des artefacts sans teardown, pas de nettoyage automatique.
- **D-03:** Le benchmark P2 mesure les **bins officiels standard** ggerganov/whisper.cpp (fp16, SHA-256 épinglés — référence conservative, même si plus lourds que l'anti-sèche « Base ~57 Mo »). La **quantization est une décision Phase 3** (Whisper Manager) ; si la variante embarquée diffère, **re-benchmark obligatoire**. Aucune source quantizée hors repo officiel en P2 (empêche tout miroir non officiel). — **Reversibility:** costly

### API du wrapper JNI (data/native/whisper/)
- **D-04:** Forme : **suspend one-shot** — `suspend fun transcribe(wav, model, abort: () -> Boolean): List<WordTimestamp>` exécutée sur `Dispatchers.Default` (jamais le main thread) ; data class `WordTimestamp(word, startMs, endMs)` mappée côté Kotlin = **contrat typé P2 et P4** ; la sérialisation JSON Fonction B est différée (détail d'implémentation, pas le contrat du wrapper). — **Reversibility:** costly
- **D-05:** Annulation double circuit : le wrapper honore **l'annulation coroutine (`ensureActive()` entre chunks) combinée en OU avec le paramètre `abort: () -> Boolean`** — les deux alimentent l'abort callback natif (ERRATA E3, vérifié entre chunks).
- **D-06:** Contrat d'erreur : **`Result<List<WordTimestamp>, WhisperError>` sealed exhaustif, aucune exception levée comme chemin contractuel** ; l'OOM au chargement est une branche **distinguable** (variante dédiée ou cause typée de LoadFailed) pour que le benchmark consigne le verdict « OOM » sans ambiguïté ; la branche **Aborted est exercée en P2** par un test d'instrumentation qui déclenche l'abort callback — preuve mécanique E3 niveau wrapper.

### Orchestration du benchmark
- **D-07:** Un **`@Test` d'instrumentation dédié** (ex. `WhisperBenchmarkTest`) dans la même suite `connectedDebugAndroidTest`, **off par défaut** (le spike fonctionnel reste exécutable sans la session ~1 h), activé par arg instrumentation `bench=on`. Les mesures sont écrites dans un **JSON du external filesDir, écrit uniquement sur run complété** (run interrompu → aucun JSON partiel, rejeté et refait — contrat atomique du SPEC) ; pullé par script hôte/adb ; **le JSON est la source mécanique unique de `02-BENCHMARK.md`** (mesures par modèle : RTF 2 déc./température/pic RAM/verdict + métadonnées session : batterie départ, baseline thermique, nb threads, SHA-256 WAV + manifest) ; **zéro parsing logcat pour les métriques**.

### Contenu TTS du WAV fonctionnel
- **D-08:** Texte **français riche de ~90-110 mots distincts, versionné dans le script de génération** : aucun nom propre, aucun chiffre (écrits en toutes lettres), aucune abréviation/trait d'union/apostrophe, ponctuation limitée à points et virgules ; manifest dérivé par construction.
- **D-09:** **Garde TTS dégradé** — pré-assert mécanique du WAV généré AVANT le test fonctionnel : |durée WAV − durée manifest| ≤ 0,5 s (manifest = référence par construction) ET volume moyen (volumedetect) au-dessus d'un plancher de silence → sinon **abort explicite au même rang que TTS muet** (jamais de preuve dégradée).

### Claude's Discretion
- Structure CMake du submodule (add_subdirectory vs cible importée), flags de compilation whisper.cpp, organisation des sources du module
- Méthodes exactes de mesure température / pic RAM (BatteryManager, Debug.MemoryInfo…) et de détection du throttle — la méthode throttle nommée dans la fiche, non normative
- Nommage exact des classes/fichiers (WhisperTranscriber, WhisperError, WhisperBenchmarkTest…) sous contrainte du contrat D-04/D-06
- Détails du format JSON benchmark et du script hôte de pull, sous contrainte du contenu minimal D-07

### Deferred Ideas (OUT OF SCOPE)
- **Quantization des modèles ggml** (variante embarquée plus légère que fp16) → décision Phase 3 (Whisper Manager), avec **re-benchmark obligatoire** si variante différente de celle mesurée en P2.
- **Streaming de progression (Flow de tokens)** → la progression chunks/checkpoints est un sujet PIPE-03/Phase 4 ; le contrat suspend one-shot de P2 est volontairement minimal.

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROJ-02 | Les bibliothèques natives (whisper.cpp, ffmpeg-kit et son substitut) sont alignées 16 KB page size et l'app s'installe et se lance sur Android 13+ | Fork AAR 8.1.7 : 10/10 `.so` par ABI déjà alignés 0x4000 (vérifié mécaniquement) ; whisper.cpp à compiler avec NDK r28+ (alignement 16 KB par défaut) + flags errata en redondance ; outillage de vérification (`llvm-readelf`/`llvm-objdump` NDK, `zipalign -P 16` build-tools 35+) présent et documenté — cf. sections « 16 KB » et « Standard Stack » |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Build natif whisper.cpp (submodule b4938, ggml, flags 16 KB, 2 ABIs) | Module `whisper-native/` (CMake/NDK) | Gradle `externalNativeBuild` | Le build C/C++ vit dans le module library ; AGP orchestre via `ndkVersion` + `externalNativeBuild.cmake.path` |
| Bindings JNI bruts (`extern "C"`/jni.c) | Module `whisper-native/` | — | Couche la plus basse : traduction JVM↔C, sans logique métier |
| API de transcription typée + abort + annulation coroutine | `:app` couche `data/native/whisper/` | — | Décision D-04/D-05 verrouillée (anti-sèche) ; contrat `Result` + `WordTimestamp` |
| Preuve FFmpeg (WAV 16 kHz mono, volumedetect, FFprobe) | `:app` androidTest (instrumentation) | fork 8.1.7 (exécution) | Le spike vit exclusivement en `connectedDebugAndroidTest` (SPEC R2) |
| Génération asset TTS + manifest | Script versionné + instrumentation (TTS système) | FFmpeg (silences/concat/rééchantillonnage) | `android.speech.tts` aucune dépendance ; manifest par construction (D-08) |
| Vérification 16 KB + intégrité `.so` | `scripts/verify_p2.sh` (hôte) | llvm-readelf/llvm-objdump/zipalign SDK | Pattern batterie locale P1, zéro CI cloud (SPEC R5) |
| Benchmark Tiny/Base/Small + JSON métrique | `WhisperBenchmarkTest` (instrumentation) | script hôte pull adb | D-07 : JSON source mécanique unique, off par défaut (`bench=on`) |
| Mesures thermiques/RAM/batterie | Instrumentation (APIs Android in-app) | script hôte (adb sysfs en secours °C) | `HardwarePropertiesManager` interdit aux apps normales (SecurityException) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| whisper.cpp | b4938 = `371b5a75…` = **v1.9.3** | Transcription on-device mot-à-mot (`token_timestamps`) | Pin verrouillé E2 ; API C lue verbatim à ce commit [VERIFIED: api.github.com commit b4938 → sha 371b5a7561823ab2bb32142d2751e35e7534727b, message « release : v1.9.3 (#4000) », 2026-08-20] |
| Android NDK | **r29 stable = `29.0.14206865`** (repli immédiat : r28.2 = `28.2.13676358` déjà installée) | Toolchain CMake/clang, alignement 16 KB par défaut | Dernière stable (clause fraîcheur E2) [VERIFIED: developer.android.com/ndk/downloads — « Latest Stable Version: r29 », bloc exemple `ndkVersion "29.0.14206865"` ; LTS = r27d `27.3.13750724`] ; r30 n'est pas stable (r30 RC/beta listée sur la même page) |
| CMake (SDK) | 3.22.1 (installée) | Orchestration build natif AGP | `cmake_minimum_required(VERSION 3.5)` de whisper.cpp b4938 compatible ; 4.1.2 aussi installée [VERIFIED: %LOCALAPPDATA%/Android/Sdk/cmake = {3.22.1, 4.1.2} ; CMakeLists.txt b4938 ligne 1] |
| ffmpeg-kit fork | `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` | Conversion WAV 16 kHz mono, volumedetect, ffprobe | Dépendance existante `:app` ; API drop-in vérifiée dans l'AAR résolu [VERIFIED: javap sur classes.jar de l'AAR en cache Gradle] |
| AndroidX Test (instrumentation) | runner **1.7.0**, ext:junit **1.3.0**, core **1.7.0** | Suite `connectedDebugAndroidTest` (première du dépôt) | Registre officiel Google Maven [VERIFIED: dl.google.com/android/maven2 metadata `<latest>` 2026-09-06] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| build-tools `zipalign` | 35.0.0+ (installées 35.0.0, 36.0.0, 36.1.0, 37.0.0) | Vérification alignement zip 16 KB APK | `verify_p2.sh` (doc officielle : « Requires Android SDK Build-Tools 35.0.0+ ») [VERIFIED: developer.android.com/guide/practices/page-sizes] |
| NDK `llvm-readelf` / `llvm-objdump` | embarqué NDK r28.2 + r30 (installées) | Contrôle LOAD align des `.so` extraits de l'APK | Chemin : `<ndk>/toolchains/llvm/prebuilt/windows-x86_64/bin/` [VERIFIED: exécution locale llvm-readelf sur les 10 `.so` du fork] |
| Kotlinx coroutines | 1.11.0 (catalogue, déjà présente) | `Dispatchers.Default` + `invokeOnCancellation` (D-04/D-05) | Déjà dans `libs.versions.toml` |
| kotlinx-serialization-json | 1.11.0 (catalogue) | Sérialisation JSON du manifest TTS et du JSON benchmark | Détail d'implémentation (D-04 : la sérialisation n'est pas le contrat du wrapper) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Module `whisper-native/` (pin b4938) | Classe `WhisperKit` du fork 8.1.7 | **Rejetée** : stub « Pro tier » sans `libwhisperkit.so` dans l'AAR full-gpl — `createFromFile` jette `IOException("WhisperKit requires the Pro or Pro GPL tier…")` [VERIFIED: sources.jar du fork, WhisperKit.java, statique `AVAILABLE=false` faute de `libwhisperkit.so`] ; whisper.cpp v1.7.5 ≠ b4938 ; aucune API token-timestamps (segments seulement) ; contredit le pin E2 |
| ggml statique dans un seul `.so` JNI (pattern exemple officiel) | `BUILD_SHARED_LIBS=ON` → libwhisper.so + libggml*.so séparés | Un seul `.so` = packaging/verify_p2 plus simples et un chargement `System.loadLibrary` unique ; le pattern officiel whisper.android compile `src/whisper.cpp` + `jni.c` ensemble [VERIFIED: examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt à b4938] |
| TTS mot-à-mot + silences (D-08) | `onRangeStart()` pour dériver les timestamps | **Rejeté** : `onRangeStart` « Only called if the engine supplies timing information » (optionnel côté moteur) — pas de garantie mécanique [VERIFIED: UtteranceProgressListener.java, javadoc ligne 135-136] |

**Installation:**
```bash
# Aucune nouvelle dépendance réseau : le fork est déjà résolu, whisper.cpp arrive par submodule.
git submodule add https://github.com/ggml-org/whisper.cpp.git whisper-native/whisper.cpp
cd whisper-native/whisper.cpp && git checkout 371b5a7561823ab2bb32142d2751e35e7534727b   # = b4938
# Entrées catalogue à ajouter (gradle/libs.versions.toml) :
#   ndk = "29.0.14206865"          (ou "28.2.13676358" déjà installée — décision plan)
#   androidxTestRunner = "1.7.0" ; androidxTestExtJunit = "1.3.0"
# Modèles (script hôte D-01, URLs officielles + SHA-256 vérifiés le 2026-09-06) :
#   https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin    77 691 713 o  be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21
#   https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin   147 951 465 o  60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe
#   https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin  487 601 967 o  1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b
```

**Version verification:** tailles + SHA-256 des modèles obtenus via l'API officielle HuggingFace (`https://huggingface.co/api/models/ggerganov/whisper.cpp/tree/main`, lfs.oid = SHA-256) le 2026-09-06 [VERIFIED: huggingface.co API]. **Attention anti-sèche WHSP-01 (39/57/184 Mo)** : les bins fp16 officiels font **75/142/466 Mo** — le CONTEXT (D-03) assume déjà cet écart (« même si plus lourds que l'anti-sèche ») ; Small fp16 (~466 Mo téléchargé, empreinte RAM ≈ 1 Go+ en inférence) rend la branche « verdict OOM » réellement plausible sur un appareil 4-6 Go.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl 8.1.7 | Maven Central (repo1.maven.org) | publié 2026-07-12 | n/a (Maven Central n'expose pas de compteur) | github.com/ffmpegkit-maintained/ffmpeg-kit | OK | Déjà dépendance `:app` épinglée (catalogue ligne 30, SHA-256 AAR noté ligne 27) — aucun nouveau téléchargement |
| androidx.test:runner 1.7.0 / androidx.test.ext:junit 1.3.0 / androidx.test:core 1.7.0 | Google Maven (dl.google.com) | première-party Google | n/a | androidx (source AOSP) | OK | Ajout au catalogue pour l'androidTest — artefacts first-party officielle Google |
| whisper.cpp b4938 (submodule source, pas un paquet) | GitHub ggml-org | commit 2026-08-20, signé (« verification: valid ») | ~40k+ stars (repo référence) | github.com/ggml-org/whisper.cpp | OK | Submodule épinglé au SHA complet |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
**Fausse piste écartée :** `WhisperKit` du fork (stub Pro tier) — voir Alternatives Considered.

## Architecture Patterns

### System Architecture Diagram

```
                        HÔTE (dev machine)                                APPAREIL ARM64 Android 13+ (ANDROID_SERIAL)
 ┌───────────────────────────────────────────┐          ┌────────────────────────────────────────────────────────────┐
 │ scripts/fetch_models_p2.sh                │          │ connectedDebugAndroidTest (triple assert AVANT tout)        │
 │  HF officiel → SHA-256 (manifest repo)    │  adb push│                                                            │
 │  └─ models ggml Tiny/Base/Small ──────────┼─────────▶│ getExternalFilesDir/ : models/, wavs/, out/   (D-02)       │
 │ scripts: génération TTS (in-app)          │          │                                                            │
 │  texte FR (D-08) ──▶ synthesizeToFile     │          │  [1] TTS mot-à-mot ─▶ WAVs par mot ──▶ FFmpeg concat        │
 │  mot par mot, silences 200 ms             │          │      (onDone/onError par utteranceId)   + 16 kHz mono      │
 │  └─ manifest {word,start,end} par constr. │          │      └─▶ garde D-09 (|duréeWAV−duréeManifest|≤0,5 s         │
 │                                           │          │           + volumedetect > plancher) sinon ABORT            │
 │  verify_p2.sh                             │  adb pull│  [2] FFmpegKit.executeAsync(conversion 16 kHz mono)          │
 │  ├─ unzip APK → lib*.so                   │◀─────────┤      rc==0 ; volumedetect rc==0 (volume dans les LOGS)      │
 │  ├─ llvm-readelf -l : LOAD ≥ 0x4000       │          │      FFprobeKit.getMediaInformation :                       │
 │  ├─ zipalign -v -c -P 16 4                │          │         16000 Hz / 1 canal / pcm_s16le / durée±0,1 s / >0 o │
 │  ├─ SHA-256 .so fork == .so AAR (pin P1)  │          │  [3] Whisper JNI : transcribe(wav, model, abort)            │
 │  └─ grep workflows cloud = 0              │          │      └─▶ List<WordTimestamp(word,startMs,endMs)>  (D-04)    │
 │  (JSON benchmark pullé pour la fiche)     │◀─────────┤      assert : normalisation + appariement par rang vs       │
 └───────────────────────────────────────────┘          │      manifest : |Δstart| ≤ 200 ms, monotone, end ≥ start    │
                                                        │  [4] (bench=on) WhisperBenchmarkTest Tiny→Base→Small        │
 │  WhisperKit fork = STUB Pro (non utilisable)          │      cooldown thermique ; JSON unique sur run complété      │
 └───────────────────────────────────────────────────────┴────────────────────────────────────────────────────────────┘
 Mesures in-app : PowerManager.getCurrentThermalStatus/addThermalStatusListener · ACTION_BATTERY_CHANGED EXTRA_TEMPERATURE (°C)
                  · BatteryManager BATTERY_PROPERTY_CAPACITY (%) · /proc/self/status VmHWM (pic RAM) · adb svc power stayon (hôte)
```

### Recommended Project Structure
```
whisper-native/                      # nouveau module Android library (settings.gradle.kts: include(":whisper-native"))
├── build.gradle.kts                 # android library ; ndkVersion depuis catalogue ; externalNativeBuild → CMakeLists
└── src/main/
    ├── cpp/
    │   ├── CMakeLists.txt           # add_subdirectory(whisper.cpp) + lib JNI SHARED unique par ABI
    │   └── whisper_jni.c            # bindings bruts : load/transcribe/free + trampoline abort_callback
    └── AndroidManifest.xml          # minimal (library)
whisper-native/whisper.cpp/          # SUBMODULE épinglé 371b5a75… (b4938) — jamais modifié
app/src/main/java/com/shortifylocal/ai/data/native/whisper/
├── WhisperTranscriber.kt            # suspend fun transcribe(...) — contrat D-04/D-06 (nommage = discrétion)
├── WordTimestamp.kt                 # data class (word, startMs, endMs)
└── WhisperError.kt                  # sealed (LoadFailed(+OOM), Aborted, InferenceFailed, InvalidInput…)
app/src/androidTest/java/com/shortifylocal/ai/
├── FfmpegForkTest.kt                # R2 : conversion + volumedetect + 5 assertions FFprobe
├── WordTranscriptionTest.kt         # R3 : mot-à-mot vs manifest (±200 ms, monotone, end≥start, ≥1 mot)
├── AbortPropagationTest.kt          # D-06 : abort callback exercé au niveau wrapper (E3)
├── TtsAssetGenerationTest.kt        # R4 : WAV ~1 min + manifest ; garde D-09 ; WAV benchmark [15,20] min
└── WhisperBenchmarkTest.kt          # R7 : off par défaut, arg instrumentation bench=on ; JSON unique (D-07)
scripts/
├── fetch_models_p2.sh               # D-01 : URLs officielles + SHA-256 manifest versionné + push adb
├── gen_tts_asset_recipe.md|sh       # recette de lancement (le TTS tourne on-device dans le test)
└── verify_p2.sh                     # R5 : 16 KB + SHA-256 .so fork vs AAR + greps (pattern verify_p1.sh)
```

### Pattern 1: intégration CMake « exemple officiel » (un seul `.so` par ABI)
**What:** `add_subdirectory(whisper.cpp)` depuis le CMakeLists du module ; ggml construit statiquement et lié dans une unique lib SHARED JNI par ABI. C'est la structure de `examples/whisper.android` à b4938, qui compile `src/whisper.cpp` + `jni.c` en SHARED et tire ggml du dépôt local (aucun téléchargement réseau : `FetchContent` y pointe vers le `SOURCE_DIR` local du dépôt ; `WHISPER_CURL` OFF par défaut) [VERIFIED: examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt + CMakeLists.txt racine à b4938].
**When to use:** toujours en P2 — minimise le nombre de `.so` à vérifier en 16 KB et l'ordre de chargement JNI.
**Options à poser AVANT `add_subdirectory` (noms verbatim à b4938) :**
```cmake
set(WHISPER_BUILD_EXAMPLES OFF)  # option b4938 : "whisper: build examples"
set(WHISPER_BUILD_TESTS    OFF)  # option b4938 : "whisper: build tests"
set(WHISPER_BUILD_SERVER   OFF)  # option b4938 : "whisper: build server example"
set(GGML_OPENMP            OFF)  # option ggml b4938 : "ggml: use OpenMP" (défaut ON !) — déterminisme n_threads
# flags linker 16 KB de l'errata (redondants avec NDK r28+ par défaut, exigés par l'errata E2) :
target_link_options(whisper_jni PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
```
**Garde submodule absent → échec CMake actionnable :**
```cmake
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/CMakeLists.txt")
  message(FATAL_ERROR
    "whisper.cpp submodule absent. Exécuter : git submodule update --init --recursive")
endif()
```

### Pattern 2: trampoline abort_callback (D-05, errata E3)
**What:** l'abort callback natif reçoit un `void* user_data` qui pointe sur un état alloué par le JNI (flag atomique + référence JVM). Chaque invocation C teste un `atomic_bool` posé depuis Kotlin (`ensureActive()` OU `abort: () -> Boolean`) ; à l'appel, le trampoline pose le flag côté JVM et retourne `true` pour stopper.
**When to use:** une seule instance de contexte/transcription à la fois (whisper_full n'est pas thread-safe sur un même contexte — verbatim : « Not thread safe for same context » [VERIFIED: whisper.h commentaire au-dessus de whisper_full]).
**Granularité réelle mesurée à b4938 :** le callback est évalué après **chaque passe encodeur** (~1× par fenêtre ~30 s d'audio) ET **à chaque step de décodage** ; un abort fait échouer `whisper_encode_internal`/`whisper_decode_internal` et `whisper_full` retourne alors un code négatif (-6 encode, -8/-9 decode) [VERIFIED: src/whisper.cpp lignes 2461, 2983, 7057-7059, 7183-7185, 7495-7499 à b4938]. Le wrapper doit donc interpréter « code négatif + flag abort posé » = `WhisperError.Aborted`, et « code négatif sans flag » = `InferenceFailed`.

### Pattern 3: attente TTS mot-à-mot (D-08)
**What:** pour chaque mot : `synthesizeToFile(mot, params, file, utteranceId=idx)` (retour = succès de **mise en file**) puis continuation suspendue jusqu'à `onDone(utteranceId)`/`onError(utteranceId, errorCode)` (ContinueSignal/CompletableDeferred). Durée de chaque WAV mesurée (FFprobeKit) → start/end cumulés + 200 ms de silence inséré par concat FFmpeg entre mots.
**When to use:** génération de l'asset fonctionnel + manifest exact par construction.
**Garde minimale :** `setLanguage(Locale.FRENCH)` → vérifier retour `isLanguageAvailable` ≠ `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED` sinon abort explicite (D-09/SPEC R4).

### Anti-Patterns to Avoid
- **Confondre centisecondes et millisecondes** : toutes les valeurs temporelles de l'API C (segments et tokens) sont en centisecondes ; multiplier par 10 exactement (voir Pitfall 1).
- **Laisser `language` par défaut** : à b4938, `whisper_full_default_params` pose `language = "en"` — sur un asset français, transcription fausse ou hallucinée ; poser `"fr"` explicitement.
- **Parier sur `HardwarePropertiesManager`** pour les °C CPU in-app : `SecurityException` hors device owner/VR service.
- **Régénérer le build avec `GGML_OPENMP` par défaut sur Android** : `find_package(OpenMP)` peut réussir (libomp dans le NDK) et interagit avec la maîtrise fine de `n_threads` exigée par le benchmark.
- **Patcher un `.so` du fork** pour l'alignement (prohibition SPEC P7) — ils sont déjà alignés ; toute divergence SHA-256 APK vs AAR est un échec.
- **Utiliser Gradle Managed Devices** pour la recette : ils ignorent `ANDROID_SERIAL` (AGP crée/détruit ses propres émulateurs) — rester sur le `connectedDebugAndroidTest` classique + triple assert in-test.
- **Parser logcat pour les métriques du benchmark** (interdit D-07) — tout vient du JSON écrit par l'instrumentation.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Vérification alignement ELF 16 KB | parseur ELF maison | `llvm-readelf -l` / `llvm-objdump -p` (NDK) — LOAD `align 2**14` | La doc officielle décrit exactement ce check + fournit `check_elf_alignment.sh` de référence [CITED: developer.android.com/guide/practices/page-sizes] |
| Alignement zip de l'APK | réalignement manuel | `zipalign -v -c -P 16 4` (build-tools 35+) | Outil officiel ; sortie « Verification successful » |
| Durée/propriétés audio | parseur WAV maison | `FFprobeKit.getMediaInformation` (fork) + `volumedetect` | Sample rate/canaux/codec/durée déjà structurés (`StreamInformation.getSampleRate()`, `getChannelLayout()`, `getCodec()`, `MediaInformation.getDuration()/getSize()`) [VERIFIED: javap AAR] |
| Timestamps de mots | DTW ou alignement maison | `token_timestamps=true, max_len=1, split_on_word=true` (paramètres natifs verrouillés par la SPEC) | `whisper_wrap_segment` à b4938 coupe aux débuts de mots (`txt[0] == ' '`) en comptant des **caractères UTF-8** (accents français corrects) [VERIFIED: whisper.cpp 6062-6124] |
| Vérification d'intégrité fichiers | hash maison | `sha256sum` (hôte) / `java.security.MessageDigest` (app) | Pin D-01 et check R5 ; comparaison temps constant non requise ici (pas un secret) |
| Silences 200 ms / concat / rééchantillonnage | mixage manuel PCM | FFmpeg du fork (`anullsrc`, `concat`, `-ar 16000 -ac 1 -c:a pcm_s16le`) | Déjà embarqué et prouvé par la CI du fork ; même librairie que le pipeline réel |
| Stay-awake pendant la session | service foreground custom | `adb shell svc power stayon true` (recette hôte) | Commande AOSP officielle : « Set the 'keep awake while plugged in' setting » [VERIFIED: cmds/svc PowerCommand.java] |

**Key insight:** toute la chaîne critique (timestamps, alignement, propriétés audio, hash) a déjà des outils officiels — le risque P2 est concentré dans le collage (JNI, unités, threads), pas dans les primitives.

## Common Pitfalls

### Pitfall 1: centisecondes ≠ millisecondes
**What goes wrong:** `whisper_full_get_token_t0/t1`, `whisper_token_data.t0/t1` et les t0/t1 de segments sont des `int64_t` en **centisecondes** ; restitués tels quels, tous les timestamps sont 10× trop courts et le contrat ±200 ms du manifest échoue de façon trompeuse.
**Why it happens:** les commentaires de l'en-tête disent « in centiseconds » (tokens) et l'exemple CLI divise par 100.0 pour des secondes.
**How to avoid:** conversion unique `ms = cs * 10L` dans le mapping JNI→Kotlin ; assertion croisée dans le test (start du 1er mot < 2 s ; durée totale ≈ durée WAV).
**Warning signs:** manifest ±200 ms qui échoue systématiquement d'un facteur 10.

Verbatim à b4938 [VERIFIED: include/whisper.h 668-677 et 131-151 ; examples/cli/cli.cpp 910-918] :
```c
// Get the start/end time of the specified token, in centiseconds. ...
WHISPER_API int64_t whisper_full_get_token_t0(...)
WHISPER_API int64_t whisper_full_get_token_t1(...)
```
```c
// token-level timestamp data
int64_t t0;        // start time of the token
int64_t t1;        //   end time of the token
```

### Pitfall 2: `GGML_OPENMP` par défaut ON sur Android
**What goes wrong:** à b4938, `option(GGML_OPENMP "ggml: use OpenMP" ON)` et `find_package(OpenMP)` dans `ggml/src/CMakeLists.txt` — le NDK embarque libomp, donc OpenMP peut s'activer silencieusement ; le contrôle thread-par-thread du benchmark (nb threads consigné) devient ambigu (pool omp vs pool std::thread de ggml-cpu).
**Why it happens:** défaut pensé desktop ; le cross-compile Android ne le désactive pas.
**How to avoid:** `-DGGML_OPENMP=OFF` explicite dans les arguments CMake du module ; consigner `n_threads` passé à `whisper_full_params` dans le JSON benchmark (D-07).
**Warning signs:** build log mentionne OpenMP ; variance anormale de RTF entre runs.

[VERIFIED: ggml/CMakeLists.txt ligne 245 (« option(GGML_OPENMP "ggml: use OpenMP" ON) ») ; ggml/src/CMakeLists.txt 225-240 (find_package + GGML_USE_OPENMP)] — l'efficacité du désactivage sur la stabilité du RTF est une recommandation d'ingénierie [ASSUMED], non normative (discrétion Claude).

### Pitfall 3: granularité et codes de l'abort callback
**What goes wrong:** croire que l'abort est testé « entre chunks d'audio » à fréquence fixe, ou traiter tout code de retour non nul comme une erreur fatale — l'abort produit précisément des codes négatifs (-6/-8/-9) via l'échec encode/decode.
**Why it happens:** la sémantique n'est visible que dans le code source de `whisper_full_with_state`.
**How to avoid:** combiner « return code négatif » avec le flag posé côté Kotlin pour élire `WhisperError.Aborted` (D-06) ; exercer mécaniquement via le test d'abort P2 (SPEC) ; utiliser `progress_callback`/`new_segment_callback` comme points de rappel complémentaires si besoin de réactivité entre fenêtres.
**Warning signs:** transcription « réussie » tronquée au lieu d'être marquée Aborted.

### Pitfall 4: `language` par défaut = "en" et `print_progress` = true
**What goes wrong:** transcription française dégradée (défaut `"en"`) ; sorties console natives polluent logcat (`print_progress=true` par défaut).
**How to avoid:** poser `language="fr"`, `print_progress=false`, `print_realtime=false`, `print_timestamps=false` dans le wrapper ; les valeurs par défaut verbatim à b4938 [VERIFIED: src/whisper.cpp 5949-5975] : `n_threads = std::min(4, hardware_concurrency())`, `token_timestamps=false`, `max_len=0`, `split_on_word=false`, `language="en"`.

### Pitfall 5: sémantique exacte de `max_len=1` + `split_on_word=true`
**What goes wrong:** s'attendre à un token = un segment ; en réalité `whisper_wrap_segment` coupe quand le cumul dépasse `max_len` **caractères UTF-8** ET que le token suivant commence par un espace (début de mot dans le vocabulaire BPE). Résultat attendu : **un segment par mot**, mais la ponctuation collée (`chien,`) reste dans le segment du mot, et certains tokens de ponctuation peuvent former leurs propres segments.
**How to avoid:** garder la normalisation du SPEC (minuscules, retrait ponctuation) AVANT appariement par rang ; ne pas parser les tokens un à un (le contrat P2 lit des segments-mots).
**Warning signs:** comptage de segments ≠ comptage de mots du manifest → la normalisation/le rang absorbe l'écart ; échec si l'écart devient structurel (revoir le texte D-08, pas le code d'appariement).

[VERIFIED: whisper.cpp 6062-6066 (`should_split_on_word`: `txt[0] == ' '`), 6070-6078 (utf8_len), 6094-6124 (wrap), 7690-7691/7738-7739 (appel si `params.max_len > 0`)]

### Pitfall 6: TTS — résultat de file d'attente, pas de synthèse
**What goes wrong:** traiter le retour de `synthesizeToFile` (0/-1) comme le succès de la synthèse ; écrire le mot suivant avant la fin du précédent.
**Why it happens:** javadoc : « returns ERROR or SUCCESS of **queuing** the synthesizeToFile operation » — asynchrone.
**How to avoid:** 1 utteranceId par mot ; suspension jusqu'à `onDone` ; `onError(utteranceId, errorCode)` → abort explicite (codes -3…-9, STOPPED=-2) ; `getMaxSpeechInputLength()`=4000 (largement suffisant mot par mot) ; silence d'un moteur absent/data manquante → `LANG_MISSING_DATA(-1)`/`LANG_NOT_SUPPORTED(-2)` via `isLanguageAvailable`.
**Warning signs:** WAVs tronqués ou vides → la garde D-09 (volumedetect + durée) doit couper avant le test fonctionnel.

[VERIFIED: AOSP TextToSpeech.java 91-197 (constantes verbatim), 1909-1958 (synthesizeToFile(File)), 2553-2555 (`return 4000`); UtteranceProgressListener.java 24/35/60 (onStart/onDone/onError(utteranceId, errorCode))]

### Pitfall 7: callbacks FFmpegKit sur un pool interne
**What goes wrong:** supposer que `FFmpegSessionCompleteCallback` arrive sur le main thread ou sur le thread appelant.
**Why it happens:** `executeAsync` soumet à `asyncExecutorService = Executors.newFixedThreadPool(10)` (défaut `asyncConcurrencyLimit = 10`) [VERIFIED: FFmpegKitConfig.java 150-153, 723-727 + AsyncFFmpegExecuteTask.java].
**How to avoid:** les tests suspendent sur la complétion de session (future/callback) ; ne jamais toucher l'UI depuis les callbacks ; `cancel(sessionId)` (et non `cancel()` global qui émet SIGINT pour **toutes** les sessions) pour le câblage `invokeOnCancellation` (E3).

### Pitfall 8: volumedetect — résultat dans les logs, pas dans rc
**What goes wrong:** chercher mean_volume dans le return code ou la sortie standard.
**How to avoid:** `-af volumedetect -f null -` écrit `[Parsed_volumedetect…] mean_volume: …` dans les logs de session : `session.getAllLogsAsString()` (ou LogCallback) puis regex [ASSUMED pour le format exact de la ligne — standard FFmpeg, à figer dans le test par une assertion tolérante].

### Pitfall 9: choix de la NDK pin vs machine locale
**What goes wrong:** épingler `ndkVersion` à une version absente du disque déclenche un téléchargement silencieux par AGP (licensing/latence) ; l'inverse (r30 `30.0.14904198` installée) épinglerait une **pre-release**.
**How to avoid:** décider au plan : r29 stable `29.0.14206865` (fraîcheur E2 maximale, installation par AGP au premier build) ou r28.2 `28.2.13676358` (déjà installée, satisfait ≥ r28) ; consigner version + date de vérification dans la fiche et PROJECT.md (AC).
[VERIFIED: répertoires ndk/ locaux = 28.2.13676358 + 30.0.14904198 ; page NDK downloads = stable r29 `29.0.14206865`, r30 listé RC/beta]

### Pitfall 10: push adb vers le stockage app-scoped
**What goes wrong:** `adb push` vers `/sdcard/Android/data/<pkg>/files/…` peut être refusé selon la surcouche (Android 11+ restreint Android/data pour MTP, mais le shell adb conserve en général l'accès) — ne pas laisser ce point au hasard le jour de la recette.
**How to avoid:** tenter le push direct vers `getExternalFilesDir` ; repli garanti sur app debuggable : `adb shell run-as com.shortifylocal.ai cp /data/local/tmp/<fichier> files/…` (l'APK debug des tests est debuggable) [ASSUMED — à valider à la recette UAT, tolérance haute].

### Pitfall 11: chargement JNI et ordre des `.so`
**What goes wrong:** `UnsatisfiedLinkError` au premier appel si le nom de la lib diffère du nom déclaré, ou si le build produit ggml en `.so` séparés non packagés.
**How to avoid:** pattern un-seul-`.so` (Pattern 1) + `System.loadLibrary("whisper_jni")` unique dans l'objet compagnon Kotlin ; sur clone frais, l'échec submodule doit être le FATAL_ERROR CMake actionnable (AC R1).

### Pitfall 12: compatibilité whisper.cpp v1.9.3 vs anciens tutos
**What goes wrong:** copier des flags/options d'anciens tutos (`WHISPER_METAL`, `WHISPER_CUBLAS`, `GGML_OPENMP` absent…) — à b4938 plusieurs options whisper ont été déléguées à ggml (helpers de transition `whisper_option_depr` : `WHISPER_OPENMP→GGML_OPENMP`, `WHISPER_METAL→GGML_METAL`, etc.) et l'API a des nouveautés (VAD : `params.vad=false` par défaut — ne pas activer en P2, il changerait la timeline des timestamps).
**How to avoid:** ne se fier qu'aux fichiers lus à b4938 (cette recherche) ; laisser `vad=false` (défaut).
**Warning signs:** options CMake inconnues → erreurs `whisper_option_depr` ou no-op silencieux.

## Code Examples

### CMakeLists du module (squelette conforme aux faits b4938)
```cmake
cmake_minimum_required(VERSION 3.22)   # ≥ 3.5 exigé par whisper.cpp b4938 ; 3.22.1 installée dans le SDK
project(whisper_native LANGUAGES C CXX)

set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp")
if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "whisper.cpp submodule absent — git submodule update --init --recursive")
endif()

set(BUILD_SHARED_LIBS OFF)           # ggml+whisper statiques, un seul .so JNI (pattern exemple officiel)
set(WHISPER_BUILD_EXAMPLES OFF)
set(WHISPER_BUILD_TESTS OFF)
set(WHISPER_BUILD_SERVER OFF)
set(GGML_OPENMP OFF)                 # déterminisme n_threads (défaut b4938 = ON)
add_subdirectory(${WHISPER_DIR})     # fournit les cibles ggml et whisper (src/CMakeLists.txt: add_library(whisper …))

add_library(whisper_jni SHARED whisper_jni.c)
target_link_libraries(whisper_jni PRIVATE whisper log)
target_include_directories(whisper_jni PRIVATE
  ${WHISPER_DIR}/include             # whisper.h
  ${WHISPER_DIR}/ggml/include)       # ggml.h (typedef ggml_abort_callback)
target_link_options(whisper_jni PRIVATE
  -Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384)     # errata E2 — redondant avec NDK r28+ mais prescrit
```
[CIBLES VERIFIED: src/CMakeLists.txt b4938 ligne 106 `add_library(whisper …)`, ligne 152 `target_link_libraries(whisper PUBLIC ggml Threads::Threads)`]

### Trampoline abort_callback (C)
```c
// ggml/include/ggml.h (b4938) — signature exacte :
//   typedef bool (*ggml_abort_callback)(void * data);
typedef struct { _Atomic int cancelled; } abort_state_t;

static bool jni_abort_callback(void *data) {
    abort_state_t *st = (abort_state_t *)data;
    return atomic_load(&st->cancelled) != 0;   // true => computation aborted
}
// params.abort_callback = jni_abort_callback;
// params.abort_callback_user_data = &state;   // posé par le JNI depuis le flag Kotlin (D-05)
```

### Wrapper Kotlin (contrat D-04/D-05/D-06)
```kotlin
class WordTimestamp(val word: String, val startMs: Long, val endMs: Long)   // ms = cs × 10

sealed interface WhisperError {
    data class LoadFailed(val isOutOfMemory: Boolean) : WhisperError        // branche OOM distinguable (D-06)
    data object Aborted : WhisperError
    data class InferenceFailed(val code: Int) : WhisperError                // code négatif natif (-6/-8/-9…)
    data object InvalidInput : WhisperError
}

class WhisperTranscriber private constructor(private val ctx: Long) {
    companion object {
        init { System.loadLibrary("whisper_jni") }
        fun load(modelPath: String): Result<WhisperTranscriber, WhisperError>  // OOM distingué ici
    }
    suspend fun transcribe(wav: File, model: File, abort: () -> Boolean): List<WordTimestamp> =
        withContext(Dispatchers.Default) {
            // ensureActive() relayé dans le trampoline entre fenêtres/steps (D-05, OU logique avec abort)
            // invokeOnCancellation → pose le flag du trampoline + FFmpegSession.cancel(sessionId) côté FFmpeg (E3)
            nativeTranscribe(ctx, wav.absolutePath, abort)   // mapping cs→ms ici, jamais avant
        }
    // loadLibrary au chargement de classe ; free du contexte via Closeable/use
}
```

### vérification 16 KB dans verify_p2.sh (pattern P1)
```bash
READELF="$SDK/ndk/$NDK_VER/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
ZIPALIGN="$SDK/build-tools/37.0.0/zipalign.exe"      # ≥ 35.0.0 requis
export MSYS_NO_PATHCONV=1                             # pattern P1 (Git Bash réécrit /sdcard/...)
unzip -o -q "$APK" 'lib/*' -d "$TMP"
find "$TMP/lib" -name 'lib*.so' | while read -r so; do
  # chaque ligne LOAD doit finir par align 2**14 (16384) ou 2**15 (32768)
  echo "$so: $("$READELF" -l "$so" | grep LOAD)"
  "$READELF" -l "$so" | grep LOAD | awk '{d=$NF; sub("2\\*\\*","",d)} strtonum("0x" 2)**d < 16384 { exit 1 }'
done
"$ZIPALIGN" -v -c -P 16 4 "$APK" | tail -1           # « Verification successful » attendu
# SHA-256: chaque lib*.so du fork extrait de l'APK == même lib dans l'AAR résolu (pin P1 374d3734…)
```
[Vérifié mécaniquement ce jour sur les 10 `.so` arm64-v8a du fork : `llvm-readelf -l` → colonne align `0x4000` pour tous — la forme de sortie readelf est `… align 0x4000` ; la doc officielle illustre `llvm-objdump -p` → `align 2**14`. Le script doit accepter les deux notations (0x4000 / 16384 / 32768).]

### Triple assert anti-émulateur (in-test, avant toute exécution)
```kotlin
private fun assertPhysicalArm64(ad: UiDevice? = null) {   // pas de UiDevice requis — Build/props suffisent
    val qemu  = System.getProperty("ro.kernel.qemu") ?: "" // via reflection sur SystemProperties si besoin
    // Implémentation réaliste : android.os.SystemProperties (masqué) → reflection, OU
    // lecture directe des sysprops via `adb shell getprop` côté script AVANT `connectedDebugAndroidTest`
    // (pattern P1 : asserts dans verify_p2.sh = garde opérateur ; garde in-test = défense en profondeur).
    check(!qemu.toBoolean()) { "ÉMULATEUR DÉTECTÉ (ro.kernel.qemu=1) — spike ARM64 physique uniquement" }
    check(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "ABI != arm64-v8a" }
    check(Build.HARDWARE !in setOf("goldfish", "ranchu")) { "HARDWARE émulateur: ${Build.HARDWARE}" }
}
```
Note factuelle : `ro.kernel.qemu`/`ro.hardware` ne sont pas des propriétés système lisibles par `System.getProperty` en app — passer par `android.os.SystemProperties` (reflection) pour `ro.kernel.qemu`, tandis que `Build.HARDWARE`/`Build.SUPPORTED_ABIS` sont API publique ; le double niveau (script hôte `getprop` + asserts in-test) est le montage le plus robuste [ASSUMED pour la lisibilité in-app de `ro.kernel.qemu` — à trancher à l'implémentation ; le script hôte P1 already asserts via getprop].

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (404 Maven) | fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` | 2025-04-01 / décision 2026-09-05 | API drop-in vérifiée dans l'AAR ; WhisperKit = stub Pro ignoré |
| NDK r27 : flags 16 KB manuels | NDK r28+ : alignement 16 KB par défaut | r28 (stable févr. 2025) | Les flags errata deviennent redondants mais restent prescrits ; délai Play : 1er févr. 2027 [CITED: page-sizes] |
| whisper.cpp 1.7.x (WhisperKit du fork) | b4938 = v1.9.3 | 2026-08-20 | VAD intégré (désactivé en P2), variants CPU Android ggml, unités inchangées (cs) |
| Options `WHISPER_*` GPU/OMP | Délégation `GGML_*` (helpers de transition) | avant b4938 | Config CMake : poser les options `GGML_*` |

**Deprecated/outdated:**
- `WHISPER_OPENMP`/`WHISPER_NATIVE`/`WHISPER_METAL`… : avertissements de dépréciation à b4938 → utiliser `GGML_OPENMP`, etc.
- `synthesizeToFile(String, HashMap, …)` : dépréciée depuis API 21 → signature `CharSequence, Bundle, File, String`.
- `HardwarePropertiesManager.getDeviceTemperatures` : légal en pratique seulement pour device owner/VR — pas pour cette app.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | L'unité de `EXTRA_TEMPERATURE` est le dixième de °C (int) | Pitfalls/Mesure | Température fausse d'un facteur 10 dans le JSON benchmark — détectable immédiatement (valeur absurde) |
| A2 | La ligne de log volumedetect (`mean_volume: X dB`) est stable en format et parseable | Pitfall 8 | Le garde-fou D-09 (plancher de silence) doit être relu — assertion tolérante recommandée |
| A3 | `adb push` vers `/sdcard/Android/data/<pkg>/files` fonctionne sur l'appareil cible (repli `run-as` documenté) | Pitfall 10 | Recette bloquée le jour J — repli garanti sur APK debuggable |
| A4 | `GGML_OPENMP=OFF` améliore le déterminisme du RTF/n_threads sur Android | Pitfall 2 | Si faux : impact qualité de mesure, pas de justesse ; l'option reste POSABLE (défaut ON ≠ obligation) |
| A5 | La lecture in-app de `ro.kernel.qemu` requiert `android.os.SystemProperties` par reflection | Code Examples (triple assert) | Un assert in-test qui ne lit pas la prop → garde portée par le script hôte `getprop` (pattern P1) — pas de perte de garantie AC si tranché au plan |
| A6 | Les checksums SHA-256 HuggingFace (lfs.oid) sont stables dans le temps pour des fichiers LFS inchangés | Standard Stack | Le manifest D-01 re-vérifie au téléchargement ; un changement upstream = échec explicite (comportement voulu) |

## Open Questions

1. **(RESOLVED — tranché par 02-01-PLAN.md Task 1 : pin r29 `29.0.14206865` au catalogue, repli documenté `28.2.13676358` hors ligne)** **NDK : r29 stable (`29.0.14206865`) ou r28.2 installée (`28.2.13676358`) ?**
   - What we know: les deux satisfont ≥ r28 ; r29 = dernière stable (fraîcheur E2) ; r28.2 est déjà sur disque ; whisper.android officiel est encore sur ndkVersion 25.2 à b4938 (exemple, pas une contrainte).
   - What's unclear: le coût d'installation r29 (téléchargement AGP) vs la fraîcheur exigée.
   - Recommendation: épingler **r29 `29.0.14206865`** (clause fraîcheur E2 littérale), consigner version + date ; repli documenté r28.2 si l'installation échoue hors ligne.
2. **(RESOLVED — tranché par 02-04-PLAN.md Task 1 : baseline + cooldown sur la température batterie via broadcast sticky `ACTION_BATTERY_CHANGED`, `EXTRA_TEMPERATURE`/10.0 °C)** **Méthode °C retenue pour le cooldown « baseline + 2 °C » (discrétion Claude, non normative)**
   - What we know: in-app, la seule source °C fiable est la température **batterie** (`EXTRA_TEMPERATURE`) ; les zones sysfs CPU ne sont pas garanties lisibles depuis l'app (SELinux) mais le sont via `adb shell` sur la plupart des appareils.
   - What's unclear: le comportement de l'appareil UAT cible (zones sysfs lisibles ?), non encore accédé.
   - Recommendation: baseline + cooldown mesurés sur la température batterie in-app (méthode nommée dans la fiche), sondage sysfs via adb en parallèle pour le diagnostic ; statut `PowerManager` comme signal de throttle catégoriel (« zéro thermal throttle »).
3. **(RESOLVED — tranché par 02-01-PLAN.md Task 1 : `abiFilters` inclut x86_64 compile-only, jamais exécuté)** **`x86_64` : build du module uniquement (jamais exécuté)**
   - What we know: ggml b4938 gère le cross x86_64 Android (défauts SSE/AVX OFF en cross-compile car `GGML_NATIVE` passe OFF quand `CMAKE_CROSSCOMPILING`) [VERIFIED: ggml/CMakeLists.txt 105-123].
   - Recommendation: aucune option x86 spéciale ; l'assert ABI du spike empêche toute exécution.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK + platform android-37 | Build `:app` (compileSdk 37) | ✓ | platforms android-29…37.0 | — |
| NDK ≥ r28 | whisper-native (R1) | ✓ | r28.2 `28.2.13676358` + r30 pré-release `30.0.14904198` ; **r29 stable `29.0.14206865` non installée** | AGP télécharge la version épinglée ; sinon pin r28.2 |
| CMake | build natif | ✓ | 3.22.1 et 4.1.2 (SDK) | — |
| build-tools (zipalign ≥ 35) | verify_p2.sh (R5) | ✓ | 33.0.1, 35.0.0, 36.0.0, 36.1.0, 37.0.0 | — |
| llvm-readelf/llvm-objdump | verify_p2.sh (R5) | ✓ | embarqué NDK (chemin vérifié, testé sur le fork) | — |
| adb | recette ARM64 + pulls | ✓ | 37.0.1-15733141 | — |
| JDK 17 toolchain | Gradle build | ✓ | provisionné foojay (pattern P1) | — |
| AAR fork 8.1.7 | exécution FFmpeg | ✓ | cache Gradle local, SHA-256 = pin P1 | — |
| whisper.cpp b4938 | submodule | ✗ (non cloné) | — | `git submodule update --init` (AC R1 prévoit le clone frais) |
| Modèles ggml Tiny/Base/Small | benchmark | ✗ (non téléchargés) | — | `fetch_models_p2.sh` (D-01), URLs + SHA-256 dans cette recherche |
| Appareil physique ARM64 Android 13+ | R6 (exclusif) | UAT ouverte (jambe physique P1 non soldée) | — | Prérequis de clôture — blocage humain documenté au SPEC |
| TTS français (moteur système) | asset fonctionnel (R4) | dépend de l'appareil UAT | — | `isLanguageAvailable` → abort explicite (D-09) ; repli CC0 = runs benchmark seulement |

**Missing dependencies with no fallback:** appareil physique ARM64 (UAT P1 à solder) — prérequis de clôture déjà posé au SPEC, hors portée du code.
**Missing dependencies with fallback:** NDK r29 (téléchargement AGP) ; modèles (script D-01) ; submodule (clone).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + AndroidX Test (instrumentation) — runner 1.7.0, ext:junit 1.3.0 [VERIFIED Google Maven 2026-09-06] ; junit 4.13.2 déjà au catalogue |
| Config file | none — la suite `app/src/androidTest/` n'existe pas encore (Wave 0) ; `testInstrumentationRunner` + dépendances catalogue à créer |
| Quick run command | `./gradlew :app:connectedDebugAndroidTest --tests "com.shortifylocal.ai.WordTranscriptionTest"` (ANDROID_SERIAL imposé) |
| Full suite command | `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest` puis `bash scripts/verify_p2.sh` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| R1 | Build 2 ABIs + échec CMake actionnable sans submodule | build (script) | `./gradlew :whisper-native:assembleDebug` (+ clone frais en recette) | ❌ Wave 0 (module) |
| R2 | FFmpeg conversion rc=0 + volumedetect rc=0 + 5 props FFprobe | instrumentation | `:app:connectedDebugAndroidTest` (classe FfmpegForkTest) | ❌ Wave 0 |
| R3 | Mot-à-mot vs manifest (≥1 mot, ±200 ms, monotone, end≥start) | instrumentation | id. (WordTranscriptionTest) | ❌ Wave 0 |
| R4 | Asset TTS + manifest + bornes [15,20] min + abort TTS muet | instrumentation | id. (TtsAssetGenerationTest) | ❌ Wave 0 |
| R5 | 16 KB + zipalign + SHA-256 .so vs AAR + zéro workflow cloud | bash (hôte) | `bash scripts/verify_p2.sh` | ❌ Wave 0 |
| R6 | Triple assert + ANDROID_SERIAL, refus émulateur avant lancement | instrumentation + bash | asserts hôte `getprop` + garde in-test | ❌ Wave 0 |
| R7 | Benchmark Tiny/Base/Small → JSON unique (bench=on) | instrumentation (off par défaut) | `adb shell am instrument -e bench on …` / via Gradle `-Pandroid.testInstrumentationRunnerArguments.bench=on` | ❌ Wave 0 |
| D-06 | Abort callback exercé au niveau wrapper (E3) | instrumentation | AbortPropagationTest | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:assembleDebug :whisper-native:assembleDebug` (compile) + `bash scripts/verify_p2.sh` (mécanique sans appareil)
- **Per wave merge:** suite `connectedDebugAndroidTest` complète sur le serial physique
- **Phase gate:** suite verte + verify_p2.sh vert + JSON benchmark pullé → `02-BENCHMARK.md` avant `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Module `whisper-native/` + entrée `include(":whisper-native")` (settings.gradle.kts)
- [ ] Entrées catalogue : `ndk`, `androidxTestRunner`, `androidxTestExtJunit` (+ runner configuré `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`)
- [ ] `app/src/androidTest/` (inexistant — confirmé) avec les 5 classes de test
- [ ] `scripts/verify_p2.sh` + `scripts/fetch_models_p2.sh` (+ manifest SHA-256)
- [ ] Submodule whisper.cpp épinglé `371b5a7561823ab2bb32142d2751e35e7534727b`

## Security Domain

### Applicable ASVS Categories (level 1)
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | aucun compte/serveur (local-first) |
| V3 Session Management | no | pas de session |
| V4 Access Control | no | spike instrumentation-only, aucune UI (SPEC out-of-scope) |
| V5 Input Validation | yes | WAV/manifest validés mécaniquement (D-09, R3, FFprobe asserts) ; abort sur divergence SHA-256 (D-01, R5) |
| V6 Cryptography | yes (integrity only) | SHA-256 via `sha256sum`/`java.security.MessageDigest` — jamais de hash maison ; aucun chiffrement requis en P2 |

### Known Threat Patterns for (NDK + modèles + TTS on-device)
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Modèle ggml substitué/corrompu (miroir non officiel) | Tampering | Pin SHA-256 manifest versionné (D-01), échec au téléchargement en cas de divergence ; interdiction miroirs (D-03) |
| `.so` du fork remplacé/patché dans l'APK | Tampering | verify_p2.sh : SHA-256 `.so` APK == `.so` AAR résolu (pin P1) ; patch ELF interdit (P7) |
| Injection d'arguments FFmpeg par chemin fichier | Tampering | Chemins construits depuis `getExternalFilesDir` (pas d'input utilisateur en P2) ; `executeWithArguments(String[])` évite le parsing d'espaces [VERIFIED: API fork] |
| TTS muet/dégradé → preuve biaisée | Repudiation | Garde D-09 (durée ±0,5 s + plancher volumedetect) → abort explicite, jamais de PASS vacuo |

## Sources

### Primary (HIGH confidence)
- ggml-org/whisper.cpp @ `371b5a7561823ab2bb32142d2751e35e7534727b` (= b4938, v1.9.3) : `include/whisper.h` (params, token_data, t0/t1 centiseconds, WHISPER_SAMPLE_RATE, init/free API), `src/whisper.cpp` (défauts, abort -6/-8/-9, wrap_segment), `CMakeLists.txt` + `src/CMakeLists.txt` + `ggml/CMakeLists.txt` + `ggml/src/CMakeLists.txt` (cibles, options, OpenMP, variants Android), `examples/whisper.android/**` (pattern JNI/CMake officiel) — récupérés via raw.githubusercontent.com au SHA épinglé
- AAR fork 8.1.7 résolu localement (`~/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained/`) : `javap` des classes (`FFmpegKit`, `FFprobeKit`, `ReturnCode`, `FFmpegSession`, `Session`, `SessionState`, `FFmpegKitConfig`, `MediaInformation`, `StreamInformation`, `WhisperKit`, `AbiDetect`) + sources.jar (`AsyncFFmpegExecuteTask`, `FFmpegKitConfig` pool 10, `WhisperKit` stub Pro) + inspection `jni/` (10 `.so` × 2 ABI, LOAD 0x4000 via llvm-readelf NDK r30)
- AOSP (android.googlesource.com, refs/heads/main) : `TextToSpeech.java` (constantes verbatim, synthesizeToFile, getMaxSpeechInputLength=4000), `UtteranceProgressListener.java` (onStart/onDone/onError, onRangeStart optionnel), `PowerManager.java` (THERMAL_STATUS_*, getCurrentThermalStatus 2683, addThermalStatusListener 2757/2768), `HardwarePropertiesManager.java` (SecurityException device owner), `BatteryManager.java` (BATTERY_PROPERTY_CAPACITY=4, EXTRA_TEMPERATURE, EXTRA_LEVEL), `cmds/svc/…/PowerCommand.java` (stayon usage)
- developer.android.com : /guide/practices/page-sizes (16 KB : flags, llvm-objdump/readelf, zipalign -P 16 build-tools 35+, AGP 8.5.1+, échéance 2027-02-01), /ndk/downloads (stable r29 = 29.0.14206865 ; LTS r27d = 27.3.13750724 ; r30 = RC/beta), /ndk/downloads/revision_history, /studio/test/gradle-managed-devices (Managed Devices ignorent ANDROID_SERIAL)
- man7.org proc_pid(5) : VmHWM = « Peak resident set size ("high water mark") »
- HuggingFace API `api/models/ggerganov/whisper.cpp/tree/main` : tailles + SHA-256 (lfs.oid) des bins officiels

### Secondary (MEDIUM confidence)
- Registre Google Maven (dl.google.com metadata) : androidx.test runner/ext:junit/core latest — mécanique, mais choix de version à confirmer avec la BOM du projet au plan
- Recommandations d'ingénierie issues des faits (GGML_OPENMP OFF, un-`.so`, temp batterie pour le cooldown) — marquées comme telles dans le texte

### Tertiary (LOW confidence)
- Format exact de la ligne de log `volumedetect` sur le fork (standard FFmpeg, à figer par assertion tolérante dans le test)
- Comportement `adb push` vers Android/data sur l'appareil UAT cible (repli run-as prévu)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — chaque version/coordonnée vérifiée contre sa source primaire ce jour (commit GitHub, AAR local, Google Maven, NDK downloads)
- Architecture: HIGH — patterns extraits du code source à b4938 et de l'exemple Android officiel du même commit
- Pitfalls: HIGH pour les faits d'API (unités, défauts, codes de retour, options CMake) ; MEDIUM pour les recommandations non normatives (OpenMP, thermie batterie) explicitement séparées

**Research date:** 2026-09-06
**Valid until:** 2026-10-06 (stable — le seul point mouvant est le « latest stable NDK » ; le pin b4938 et l'AAR 8.1.7 sont figés par décision)
