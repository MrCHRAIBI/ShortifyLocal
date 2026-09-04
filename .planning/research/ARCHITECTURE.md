# Recherche Architecture

**Domaine :** Application Android de traitement média/IA on-device (pipeline YouTube → Shorts verticaux, 100 % local)
**Recherché :** 2026-09-04
**Confiance :** MEDIUM (HIGH sur les points couverts par la documentation officielle Android, l'API `whisper.h` et Maven Central ; MEDIUM sur le fork FFmpegKit et les patterns communautaires)

## Architecture Standard

### Vue d'ensemble du système

L'architecture du cahier des charges (Clean Architecture + MVVM, pipeline WorkManager) est **validée** par les recommandations officielles Android : le guide d'architecture official recommande un minimum de 2 couches (UI + data) et une couche **domain optionnelle** recommandée précisément pour « encapsuler une logique métier complexe » — ce qui est le cas ici (heuristique de découpage viral, économie de tokens, machine à états du pipeline). UDF (unidirectional data flow), SSOT (Room), state holders (`ViewModel` + `StateFlow<UiState>`) et Hilt sont tous explicitement recommandés par Google (confiance HIGH).

```
┌──────────────────────────────────────────────────────────────────────┐
│ PRESENTATION (Compose)  HomeScreen │ DetailScreen │ History │ Settings│
│   ViewModels @HiltViewModel → StateFlow<XUiState> + SharedFlow<UiEvent>│
└───────────────▲──────────────────────────────────────┬───────────────┘
                │ StateFlow (état)                      │ événements (UDF)
┌───────────────┴──────────────────────────────────────▼───────────────┐
│ DOMAIN (Kotlin pur, zéro import Android)                              │
│   models │ interfaces repository │ use cases (orchestration métier)   │
└───────▲──────────────────▲───────────────────────────▲────────────────┘
        │ implémente       │ appelle (workers→use cases)│
┌───────┴──────────────────┴───────────────────────────┴────────────────┐
│ DATA                            │ WORKER                              │
│  Room (SSOT)  SecureStorage     │  PipelineWorker (foreground         │
│  CacheManager (GC 7j, fichiers) │    dataProcessing, machine à états  │
│  NewPipeExtractorWrapper        │    DownloadingAudio→…→Done|Error)   │
│  GeminiClient  ZipExporter      │  RenderWorker (re-rendu à la demande)│
│  ┌── Wrappers natifs ──────────────────────────────────────────────┐  │
│  │ FFmpegWrapper (suspend)   WhisperWrapper (suspend, JNI)          │  │
│  │ FaceTrackerWrapper (ML Kit bundled)                              │  │
│  └──────────────────────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────────────────────┤
│ NATIF (hors JVM)                                                       │
│  libwhisper_jni.so (compilé du source, CMake/NDK, submodule git)       │
│  libffmpeg-kit .so (AAR fork 16 KB aligné)   ML Kit (AAR, modèle local)│
└────────────────────────────────────────────────────────────────────────┘
```

### Responsabilités des composants

| Composant | Responsabilité | Implémentation typique |
|-----------|----------------|------------------------|
| `HomeViewModel` / `DetailViewModel`… | État UI immuable, validation Regex, déclenchement pipeline | `@HiltViewModel`, `StateFlow<XUiState>`, `stateIn(viewModelScope)` |
| `domain/usecase` | Orchestration métier testable sans Android | Use cases Kotlin purs, injectés partout (VM **et** workers) |
| `domain/repository` (interfaces) | Contrats : `YouTubeRepository`, `TranscriptionRepository`, `RenderRepository`, `TokenRepository`, `VideoRepository`, `GeminiRepository`, `SecureStorageRepository` | Interfaces Kotlin pur ; implémentations en `data/` |
| `data/local/room` | SSOT des projets/shorts/états ; survit à la mort du process | Room 2.8 KSP, `@Transaction` POJOs, `Flow` |
| `data/local/secure` | Solde HMAC, clé Gemini, compteurs | EncryptedSharedPreferences + Keystore, `StateFlow<Int>` |
| `CacheManager` | Arborescence `filesDir/`, GC 7 jours, purge `frames_tmp/` | Pur Kotlin + `java.io`, déclenché au lancement/fermeture projet |
| `data/remote/youtube` | Métadonnées + URL flux audio/vidéo + téléchargement Range | NewPipeExtractor (JitPack) + OkHttp ; **isolé derrière l'interface** |
| `data/native/whisper` | Transcription suspend + horodatages mot à mot | JNI CMake : `token_timestamps=true`, `max_len=1`, `split_on_word=true` |
| `data/native/ffmpeg` | Toutes commandes FFmpeg en `suspend` annulable | `suspendCancellableCoroutine` + `FFmpegKit.cancel(sessionId)` |
| `data/native/mlkit` | Centre X du plus grand visage par frame | ML Kit `face-detection` bundled, frames réduites ~480 px |
| `PipelineWorker` | Machine à états A→F, notification foreground, progression | `@HiltWorker CoroutineWorker` + `setForeground(dataProcessing)` + `setProgress` |
| `ModelManager` (Whisper Manager) | Catalogue Tiny/Base/Small, téléchargement, SHA-256, stockage | OkHttp + `DigestInputStream`, renommage atomique, `filesDir/whisper_models/` |
| `di/` | Câblage, `HiltWorkerFactory` | Modules Hilt ; `Application : Configuration.Provider` |

### Validation de la structure normative du cahier des charges

- **Couches** : conformes au guide officiel (UI/data/domain). La règle « domain sans import Android » correspond au principe officiel de testabilité. ✅
- **UDF + `StateFlow<UiState>` + `SharedFlow<UiEvent>`** : exactement le pattern officiel state holder/events. ✅
- **`domain` en pur Kotlin** : permet de tester l'heuristique de découpage (Fonction C) et l'économie de tokens en JVM pur. ✅
- **Ajustements requis par la recherche** (sans contredire le cahier des charges, en vertu de sa clause de fraîcheur §2.2) :
  1. **`com.arthenica:ffmpeg-kit-full-gpl:6.0-2` est mort** : projet officiellement retiré (annoncé janvier 2025, binaires supprimés de Maven Central le 1ᵉʳ avril 2025, dépôt archivé en lecture seule). L'artefact ne se résout plus, et ses `.so` sont alignés 4 KB seulement (incompatibles 16 KB). → Utiliser le **fork de continuation** `io.github.maitrungduc1410:ffmpeg-kit-full-gpl` (Maven Central, API Java `com.arthenica.ffmpegkit` identique, builds alignés 16 KB). Confiance MEDIUM (vérifié par recherche + Central Sonatype, pas par build réel).
  2. Le type foreground `dataProcessing` est le **bon choix** : contrairement à `dataSync` et au nouveau type `mediaProcessing` (Android 15), `dataProcessing` n'a **pas de limite de 6 h/24 h** documentée — crucial pour un pipeline de 2 h+ de vidéo. Confiance HIGH.

## Structure de Projet Recommandée

Arborescence normative du cahier des charges (Partie 1 §3.2), complétée des éléments exigés par les intégrations natives :

```
app/src/main/java/com/shortifylocal/ai/
├── presentation/            # écrans, ViewModels, design system, navigation, thème
├── domain/                  # models, interfaces repository, use cases (pur Kotlin)
├── data/
│   ├── repository/          # implémentations des interfaces
│   ├── local/room/          # AppDatabase, DAOs, Entities, Converters
│   ├── local/secure/        # EncryptedSharedPreferences + HMAC
│   ├── local/files/         # CacheManager, AssSubtitleGenerator, ZipExporter
│   ├── remote/youtube/      # NewPipeExtractorWrapper (métadonnées + Range)
│   ├── remote/gemini/       # GeminiClient (structured outputs)
│   └── native/
│       ├── ffmpeg/          # FFmpegWrapper (suspend + annulation)
│       ├── whisper/         # bindings Kotlin ↔ JNI (interface seulement)
│       └── mlkit/           # FaceTrackerWrapper
├── worker/                  # PipelineWorker, RenderWorker
├── di/                      # modules Hilt (+ DatabaseModule, NativeModule…)
└── util/                    # YouTubeRegex, LocaleHelper, ModelCatalog (SHA-256)

whisper-native/                              # module Gradle Android library séparé
├── src/main/jni/whisper/CMakeLists.txt      # compile whisper.cpp + jni.c
├── src/main/jni/whisper/jni.c               # pont JNI (encapsule abort_callback)
├── src/main/java/…/whisper/                 # API Kotlin : WhisperContext, JniLib
└── whisper.cpp/            # SUBMODULE git épinglé (tag de release) → pas un fork copié

app/src/main/assets/fonts_render/           # Montserrat-ExtraBold.tff copié → extrait
                                            # vers filesDir/fonts/ au premier rendu
                                            # (libass ne lit PAS dans l'APK)
```

### Justification de la structure

- **`whisper-native/` en module séparé** : c'est le pattern canonique du dépôt whisper.cpp (`examples/whisper.android/lib` : module `com.android.library` avec `externalNativeBuild { cmake { path = file("src/main/jni/whisper/CMakeLists.txt") } }`, compilation de `src/whisper.cpp` + `jni.c` en une seule lib SHARED, ggml via FetchContent, cibles par ABI `whisper_v8fp16_va` (arm64, `-march=armv8.2-a+fp16`) / `whisper_vfpv4` (armeabi-v7a), `-O3 -fvisibility=hidden -Wl,--gc-sections -flto` en release). Module séparé = compilation incrémentale, cache Gradle, et isolation du code C/C++ hors de l'app. Confiance HIGH (vérifié dans le dépôt source).
- **whisper.cpp en submodule épinglé** : le projet évolue vite ; figer un tag et pouvoir `git submodule update` + rebuild est plus sûr qu'un vendor copy. Avec **NDK r28+** l'alignement 16 KB est par défaut ; en r27 et moins, ajouter `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` dans `target_link_options`. Vérification obligatoire en CI : `llvm-objdump -p *.so | grep LOAD` (→ `align 2**14`), `zipalign -c -P 16 -v 4 app-release.apk`, APK Analyzer. Confiance HIGH.
- **Polices de rendu dans `filesDir/fonts/`** : libass (filtre `subtitles` de FFmpeg) ne peut pas lire `res/font` (ressources compressées dans l'APK). Copier `Montserrat-ExtraBold.ttf` des assets vers `filesDir/fonts/` au premier rendu, puis passer `fontsdir` au filtre. Confiance MEDIUM (pattern documenté QtAV/libass + communautés FFmpeg).
- **Modèles Whisper hors APK** : 39–184 Mo par modèle, téléchargement runtime obligatoire (le cahier des charges le prévoit) — jamais dans `assets/` (poids AAB inacceptable).

### Ordre de construction recommandé (dépendances entre composants)

Le risque technique est massivement concentré côté natif et côté pipeline. Le chemin critique doit prouver l'intégration native **avant** tout investissement UI :

1. **Étape 0 — Scaffolding** (tout est parallélisable) : catalogue `libs.versions.toml`, Hilt, Room (4 entités, KSP), coquille Compose + Navigation + DataStore thème. Aucune dépendance externe.
2. **Étape 1 — Fondations natives** ⚠️ chemin critique :
   1. Intégration ffmpeg-kit **fork maitrungduc1410** + test d'une commande trivialle (`-version`, puis conversion 16 kHz mono).
   2. Module `whisper-native/` (submodule whisper.cpp, CMake, JNI) + test de transcription d'un WAV embarqué avec `token_timestamps=true`.
   3. Vérification 16 KB des deux libs dans le CI (`llvm-objdump`/`zipalign`).
   - *Pourquoi d'abord :* un échec ici (build CMake, alignement, ABI) invaliderait ou retarderait tout le reste. Une fois `TranscriptionRepository.transcribe(wav): List<WordTimestamp>` qui marche sur un vrai téléphone, 80 % du risque technique est levé.
3. **Étape 2 — Contrats domain** : interfaces + modèles (`WordTimestamp`, `ClipSegment`, `TokenBalance`…) + implémentations fake pour tests. Parallélisable avec l'étape 1 côté personnes différentes.
4. **Étape 3 — Data layer non-native** : Room complète, `CacheManager` (GC 7 j), `NewPipeExtractorWrapper` (métadonnées + téléchargement audio), `SecureStorageRepository` (HMAC), `ModelManager` + écran Whisper Manager. → **À ce stade on peut déjà télécharger un modèle et un audio.**
5. **Étape 4 — Pipeline A→C** : `PipelineWorker` (foreground + `setProgress` + machine à états) câblé sur A (smart download + conversion), B (transcription), C.1 (heuristique volumedetect + filtre sémantique). → Fin d'étape : les segments 15–60 s et scores s'affichent (même en logs/debug UI).
6. **Étape 5 — Pipeline D→F** : extraction 4 fps + ML Kit + lissage, `AssSubtitleGenerator` (`.ass` karaoke `\K`), téléchargement vidéo partiel Range + commande FFmpeg combinée (crop/scale/subtitles/loudnorm/x264), export MediaStore + ZIP. → **Premier Short complet rendu.**
7. **Étape 6 — UI complète** : les 4 écrans branchés sur le vrai état du pipeline (stepper Détails, lecteur Media3, Historique recherche, Paramètres BYOK). Peut démarrer en parallèle dès l'étape 2 avec des fakes.
8. **Étape 7 — Monétisation & durcissement** : AdManager + UMP, économie de tokens complète (contrats déjà posés en étape 2/3), i18n 7 langues + RTL, R8 + stripping logs, polices par écriture.

**Parallélisable** : design system/presentation (dès étape 0, avec fakes) ∥ monétisation (dès étape 3) ∥ i18n (dès étape 6). **Séquentiel** : natif → pipeline → UI live.

## Patterns Architecturaux

### Pattern 1 : Pipeline WorkManager foreground à machine à états persistée (worker unique + étapes en use cases)

**Quoi :** un seul `PipelineWorker` long-running (foreground `dataProcessing`) qui avance dans la machine à états `DownloadingAudio → … → Done|Error`, persiste l'étape courante dans Room à chaque transition, et délègue chaque étape à un use case domain. Un `RenderWorker` séparé gère le re-rendu haute qualité à l'achat (−3 tokens).

**Quand l'utiliser :** quand une tâche multi-étapes doit survivre à la mort du process (whisper peut durer des dizaines de minutes), exposer une progression fine à l'UI, et être annulable proprement.

**Trade-offs :** vs *chaîne de workers* (`beginUniqueWork().then()`) : la chaîne donne des étapes isolées et des retries par étape, mais complique la progression (une `WorkInfo` par worker), le partage de fichiers et la reprise au bon état. Le worker unique + état Room est le choix le plus simple qui satisfait les 3 exigences (progression, annulation, reprise) — recommandé ici. La reprise se fait par re-enqueue (`KEEP`) et lecture de l'étape persistée (les artefacts intermédiaires sont sur disque : WAV, JSON transcription, segments).

**Exemple :**
```kotlin
@HiltWorker
class PipelineWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val downloadAudio: DownloadAudioUseCase,      // domain
    private val transcribe: TranscribeUseCase,
    private val segment: SegmentUseCase,
    private val stateBus: PipelineStateBus,               // @Singleton StateFlow bus
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getLong(KEY_VIDEO_ID, -1)
        setForeground(ForegroundInfo(                     // type explicite requis API 34+
            NOTIF_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_DATA_PROCESSING))
        return try {
            runStep(PipelineState.DOWNLOADING_AUDIO) { downloadAudio(videoId) }
            runStep(PipelineState.TRANSCRIBING)      { transcribe(videoId) }
            runStep(PipelineState.SEGMENTING)        { segment(videoId) }
            // … Reframing, Subtitling, Rendering (idem)
            Result.success()
        } catch (e: CancellationException) {
            throw e                                   // rethrow : WorkManager marque CANCELLED
        } catch (e: PipelineException) {
            setProgress(workDataOf(STATE to "ERROR", CODE to e.code))
            Result.failure()                          // retry ciblé selon e.code si pertinent
        }
    }

    private suspend fun runStep(state: PipelineState, block: suspend () -> Unit) {
        stateBus.emit(state); setProgress(workDataOf(STATE to state.name, PCT to 0))
        block()
    }
}
```
Points clés vérifiés (confiance HIGH) : `setProgress(Data)` est suspend ; l'UI observe via `WorkManager.getWorkInfoByIdFlow(id)` / `getWorkInfosForUniqueWorkFlow(name)` — chaque émission porte `WorkInfo.progress` (dernier Data) et `WorkInfo.state` ; `cancelWorkById` annule la coroutine `doWork` (une `CancellationException` au prochain point de suspension) ; les payloads `Data` ne contiennent que états/pourcentages/id interne (jamais de données sensibles — règle Partie 5).

Câblage Hilt-Work (confiance HIGH) : `androidx.hilt:hilt-work` + `ksp(androidx.hilt:hilt-compiler)`, worker `@HiltWorker @AssistedInject(Context, WorkerParameters, deps…)`, et `Application : Configuration.Provider` avec `HiltWorkerFactory` injecté.

### Pattern 2 : Wrapper FFmpegKit suspend avec propagation d'annulation native

**Quoi :** encapsuler chaque commande FFmpeg dans une fonction `suspend` qui résout via `suspendCancellableCoroutine` et appelle `FFmpegKit.cancel(sessionId)` dans `invokeOnCancellation`. C'est la réalisation directe de la règle normative « l'annulation d'un job Kotlin doit stopper les commandes FFmpeg en cours » (Partie 1 §3.3).

**Quand :** toutes les étapes A (conversion 16 kHz), C.1 (volumedetect/ebur128), D (extraction frames), F (rendu combiné).

**Trade-offs :** l'API callback d'FFmpegKit est asynchrone par nature ; le wrapper suspend ajoute une couche mais rend le code séquentiel, testable (fake repository), et surtout **annulable au niveau natif** (le process ffmpeg est réellement interrompu, pas seulement ignoré). Vérification du return code : `ReturnCode.isSuccess` / `isCancel` / sinon échec (confiance HIGH — wiki officiel FFmpegKit).

**Exemple :**
```kotlin
class FFmpegWrapper @Inject constructor() : RenderRepository.NativeEngine {
    suspend fun run(vararg args: String): FFmpegResult =
        suspendCancellableCoroutine { cont ->
            val session = FFmpegKit.executeAsync(args.joinToString(" "),
                { s ->                                   // CompleteCallback
                    when {
                        ReturnCode.isSuccess(s.returnCode) -> cont.resume(FFmpegResult.OK)
                        ReturnCode.isCancel(s.returnCode)  -> cont.cancel(CancellationException("ffmpeg"))
                        else -> cont.resumeWithException(FFmpegException(s.failStackTrace))
                    }
                },
                { log -> /* capture stderr pour diagnostic, JAMAIS de secret */ })
            cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }  // ← kill natif
        }
}
```
Alternative d'orchestration (utile pour la progression du rendu F) : passer aussi un `StatisticsCallback` (time traité / durée → pourcentage) et re-émettre via `setProgress`.

### Pattern 3 : Wrapper whisper.cpp JNI suspend avec abort natif

**Quoi :** l'API Kotlin du module natif expose `transcribe(wavPath, modelPath, onProgress): List<WordTimestamp>` ; en interne, `whisper_full` est bloquant en C — l'annulation passe par le **`abort_callback`** (`ggml_abort_callback` dans `whisper_full_params`, et les callbacks retournant `false` interrompent le calcul — vérifié dans `include/whisper.h`, confiance HIGH). Le wrapper Kotlin arme un flag atomique dans `invokeOnCancellation` que le callback C lit.

**Quand :** Fonction B. `token_timestamps = true` + `max_len = 1` + `split_on_word = true` donnent un horodatage par token → `whisper_full_get_token_data(ctx, seg, tok).t0/t1` sont **en centièmes de seconde** (confiance HIGH — doc `whisper.h`) : c'est nativement l'unité du tag karaoke `.ass` `{\K}` (Fonction E) — zéro conversion complexe (×10 pour des ms si besoin).

**Trade-offs :** le contexte whisper consomme la RAM du modèle (Tiny ~150 Mo–Small ~500 Mo en mémoire selon quantification). Ne pas garder le contexte chargé entre projets : charger au début de l'étape B, libérer dans un `finally`. Exécuter sur un dispatcher dédié (`Dispatchers.Default.limitedParallelism(1)`) : whisper.cpp n'est pas thread-safe sur un même contexte et la transcription est CPU-bound mono-process.

**Exemple (côté Kotlin) :**
```kotlin
class WhisperJniBridge {                       // dans le module whisper-native
    private external fun nativeTranscribe(
        wavPath: String, modelPath: String, abortFlag: AtomicBoolean): String  // JSON
    fun transcribe(...) = /* exécuté sur dispatcher CPU dédié */
}

suspend fun TranscriptionRepository.transcribeSuspend(...): List<WordTimestamp> =
    withContext(whisperDispatcher) {           // limitedParallelism(1)
        val abort = AtomicBoolean(false)
        try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { abort.set(true) }   // ← abort_callback C
                cont.resume(parseJson(nativeTranscribe(wav, model, abort)))
            }
        } finally { releaseContext() }         // RAM native libérée même en cas d'annulation
    }
```

### Pattern 4 : ModelManager (téléchargement, checksum, stockage)

**Quoi :** catalogue en dur dans `util/ModelCatalog` (nom, taille, URL, SHA-256) pointant vers la source officielle `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-<modèle>.bin` (confiance HIGH — script officiel). Le script de téléchargement officiel **ne vérifie aucun checksum** : la vérification SHA-256 imposée par la Partie 5 §4 est donc réellement nécessaire et doit être implémentée par l'app.

**Flux :** OkHttp GET streamé → `DigestInputStream` (SHA-256 calculé pendant l'écriture dans `whisper_models/<fichier>.part`) → comparaison au catalogue (HMAC non requis ici, simple égalité de hash) → si OK : renommage atomique `.part` → `.bin` ; si KO : suppression du `.part` + erreur localisée. Reprise : `Range: bytes=<taille_du_.part>-` → 206 (si 200 : repartir de zéro, confiance HIGH — MDN).

**Point d'UI :** le Whisper Manager (Paramètres) observe la progression via un `StateFlow<ModelDownloadState>` ; le pipeline B refuse de démarrer sans modèle vérifié (redirection paramètres, comme spécifié).

### Pattern 5 : Extraction de frames à débit mémoire constant (Fonction D)

**Quoi :** pour « 1 frame / 250 ms », **ne pas** mettre les frames en RAM. Deux options validées, dans l'ordre de préférence :
1. **FFmpeg lui-même** : `-i segment.mp4 -vf "fps=4,scale=480:-2" frames_tmp/f_%04d.jpg` (une session, écrit sur disque, déjà réduite pour ML Kit). Évite le pattern lent et buggé `MediaMetadataRetriever.getFrameAtTime(OPTION_CLOSEST)` en boucle (surcoût de seek par appel, documenté officiellement — confiance MEDIUM/HIGH).
2. Si besoin d'API Android : `getFramesAtIndex()` (décodage consécutif efficace) plutôt que `getFrameAtTime` répété.

**Traitement ML Kit :** décoder les JPEG **un par un**, `InputImage.fromBitmap` (min recommandé 480×360, visage ≥100×100 px, `PERFORMANCE_MODE_FAST`, confiance HIGH — doc ML Kit), ne conserver que `centerX` du plus grand `boundingBox`, recycler le bitmap (`Bitmap.recycle()`), puis lissage moyenne mobile 1 s + interpolation (pur Kotlin dans `domain`). Après le rendu : purge `frames_tmp/` (règle Partie 4 §6.1). Coût mémoire quasi constant quelle que soit la durée du segment.

## Flux de Données

### Flux de requête (pipeline A→F)

```
[HomeScreen] clic valider
    ↓ (Regex OK, débit tokens −5 via UpdateTokensUseCase)
HomeViewModel → EnqueuePipelineUseCase
    ↓ WorkManager.enqueueUniqueWork("pipeline-<videoId>", KEEP, …)
PipelineWorker (foreground dataProcessing, notif progression)
    ↓ chaque étape = use case domain
DownloadAudio → Transcribe → Segment → Reframe → Subtitle → Render
    ↓ délègue à            ↓ persiste artefacts
data/repository impl ───→ filesDir/{audio_raw,frames_tmp,subtitles,shorts}
    │                      └→ Room (sourceStatus, paths, shorts)
    └→ FFmpegKit / whisper JNI / ML Kit / NewPipe+OkHttp
    ↑ progression
setProgress(Data) ──→ WorkInfo.progress ──┐
stateBus (StateFlow<PipelineUi>) ─────────┴→ DetailViewModel → stepper Écran 2
```

### Gestion d'état

```
[Room (SSOT projets/shorts)]  [SecureStorage (SSOT solde)]  [WorkManager (SSOT tâches)]
        │ Flow                        │ StateFlow<Int>             │ WorkInfo Flow
        ▼                             ▼                            ▼
   Repositories (data) ──implémentent──► interfaces (domain) ◄──injectés dans── VM/Workers
        ▼
ViewModels (StateFlow<UiState>) → Compose (rendu déclaratif)
        ▲
        └── événements UI remontent (UDF) : clic → ViewModel → UseCase → Repository
```

### Flux de données clés

1. **Pipeline A→F** : audio seul (~128 kbps) → WAV 16 kHz mono → JSON mots horodatés → segments 15–60 s scorés → keyframes centerX lissées → fichier `.ass` → téléchargement vidéo **partiel** (Range) → MP4 final 1080×1920 hardsub + loudnorm → cache → MediaStore à l'achat.
2. **Progression** : double canal — `WorkInfo.progress` (source de vérité WorkManager, survit au process) + bus `StateFlow` singleton (latence faible pour l'UI) ; le ViewModel fusionne les deux (le bus pour l'instantané, WorkInfo au retour foreground).
3. **Tokens** : `SecureStorageRepository.balanceFlow: StateFlow<Int>` — toute mutation passe par `updateTokens()`, l'UI se met à jour par collecte passive (aucun refresh manuel).
4. **Modèles** : Paramètres → ModelManager → `StateFlow<ModelDownloadState>(progress, vérifié)` ; le pipeline lit seulement le chemin d'un modèle vérifié.
5. **Vidéo partielle** : `NewPipeExtractor` → URL flux vidéo → OkHttp `Range: bytes=start-end` (206 attendu ; 200 → téléchargement complet + découpe par `-ss/-to` de toute façon présents dans la commande F — l'app doit tolérer les deux réponses).

## Considérations de Montée en Charge

Pour une app 100 % on-device, l'« échelle » n'est pas le nombre d'utilisateurs mais la **capacité de l'appareil** et la **durée des vidéos** :

| Échelle | Ajustements d'architecture |
|---------|---------------------------|
| Entrée de gamme (3–4 Go RAM, vidéo 15 min) | Whisper Tiny quantisé (q5_1), frames déjà réduites (~480 px) avant ML Kit, 1 seul job pipeline à la fois, purge `frames_tmp` immédiate |
| Milieu de gamme (6–8 Go, ~1 h) | Whisper Base (défaut spec), pipeline strictement séquentiel, GC cache 7 j actif |
| Haut de gamme (12 Go+, >2 h) | Whisper Small envisageable, pré-téléchargement du segment vidéo suivant pendant le rendu du précédent |

### Priorités de montée en charge

1. **Premier goulot : RAM pendant transcription + rendu.** Ne jamais faire cohabiter contexte whisper chargé et rendu x264 : étapes strictement séquentielles (la machine à états l'impose déjà), contexte whisper libéré en `finally`.
2. **Deuxième goulot : disque.** Une vidéo 2 h ≈ 150–300 Mo d'audio + vidéo partielle. Vérifier `StatFs` avant chaque étape volumineuse et mapper `SQLiteFullException`/`IOException` → `DB_DISK_FULL` (Partie 4 §7 déjà prévu).
3. **Troisième goulot : batterie/thermie.** Un job foreground unique, séquentiel ; pas de parallélisme dispatch par défaut.

## Anti-Patterns

### Anti-Pattern 1 : épingler `com.arthenica:ffmpeg-kit-full-gpl:6.0-2`

**Ce que les gens font :** suivre la doc/le wiki historique et ajouter l'artefact officiel.
**Pourquoi c'est faux :** le projet est **retiré** — binaires supprimés de Maven Central (avril 2025), dépôt archivé ; le build ne se résout plus, et même récupéré « en local », ses `.so` sont alignés 4 KB → rejet par les appareils 16 KB (Android 15+/16) et Play.
**À faire à la place :** fork `io.github.maitrungduc1410:ffmpeg-kit-full-gpl` (Même package Java `com.arthenica.ffmpegkit`, builds 16 KB alignés) — cohérent avec la clause de fraîcheur §2.2 du cahier des charges. Vérifier l'alignement au build (confiance MEDIUM sur le fork, HIGH sur la retraite de l'original).

### Anti-Pattern 2 : appeler `FFmpegKit.execute` synchrone sur le thread principal

**Ce que les gens font :** commande rapide « juste pour tester » en synchrone.
**Pourquoi c'est faux :** ANR garanti dès que le média dépasse quelques secondes ; violation de la contrainte normative « aucun traitement lourd sur le thread UI ».
**À faire à la place :** toujours passer par le wrapper suspend (Pattern 2) ; tout le pipeline vit dans le `CoroutineWorker` (Default dispatcher).

### Anti-Pattern 3 : accumuler les frames en mémoire (OOM sur vidéo longue)

**Ce que les gens font :** `List<Bitmap>` des frames extraites, ou `getFrameAtTime(OPTION_CLOSEST)` en boucle.
**Pourquoi c'est faux :** 4 fps × 60 s × 8 Mo/frame (1080p ARGB) ≈ 2 Go → `OutOfMemoryError` ; et `OPTION_CLOSEST` bouclé est extrêmement lent (seek par appel).
**À faire à la place :** Pattern 5 — frames sur disque via FFmpeg, décodage séquentiel, downscale avant ML Kit, purge `frames_tmp/` après chaque rendu.

### Anti-Pattern 4 : fuite d'annulation des processus natifs

**Ce que les gens font :** `launch { ffmpeg.run(...) }` puis annulation du job sans brancher `invokeOnCancellation`.
**Pourquoi c'est faux :** le process natif ffmpeg/whisper continue de brûler CPU/batterie en arrière-plan après l'annulation ou l'arrêt du worker ; la notification reste, l'état diverge.
**À faire à la place :** `suspendCancellableCoroutine` + `FFmpegKit.cancel(sessionId)` / flag `abort_callback` whisper / `OkHttp call.cancel()` ; et au démarrage de l'app, réconcilier : tout projet en état non terminal sans worker RUNNING → repasser en `Error`/re-enqueue.

### Anti-Pattern 5 : hardsub libass sans répertoire de polices ni échappement

**Ce que les gens faire :** `-vf subtitles=/data/.../temp_subs.ass` en espérant la police système.
**Pourquoi c'est faux :** fontconfig est absent sur Android → libass ne trouve pas Montserrat et retombe sur une police par défaut (le rendu karaoke est dégradé) ; et les chemins Android contenant `:`/`'`/espaces cassent le parsing du filtre.
**À faire à la place :** copier la police dans `filesDir/fonts/` et passer `subtitles=filename='…':fontsdir='…/fonts'` (nom de famille **sans extension** dans le `.ass`) ; générer des noms de fichiers temporaires sans caractères spéciaux (`subs_<id>.ass`) ; échapper systématiquement les chemins (confiance MEDIUM).

### Anti-Pattern 6 : données sensibles dans les payloads WorkManager / logs de callbacks

**Ce que les gens font :** passer le solde, l'URL complète avec tokens, ou des résultats de transcription dans `Data`/`Log.d`.
**Pourquoi c'est faux :** `Data` WorkManager est persisté en clair (SQLite de WorkManager) et les logs natifs d'FFmpeg finissent dans logcat — violation directe de la Partie 5 §0/§5.
**À faire à la place :** ne transmettre que `videoId` + états + pourcentages ; rendre l'URL YouTube par `videoId` depuis Room dans le worker ; filtrer les `LogCallback` d'FFmpegKit.

### Anti-Pattern 7 : traiter NewPipeExtractor comme une dépendance stable

**Ce que les gens font :** intégrer l'extractor partout dans le code data.
**Pourquoi c'est faux :** « quand YouTube change quelque chose, NewPipeExtractor casse instantanément » (doc officielle des mainteneurs) — c'est structurel, pas accidentel.
**À faire à la place :** isoler 100 % de l'extractor derrière `YouTubeRepository` ; épingler la version JitPack ; prévoir un chemin de hotfix (bump de version = seul changement) ; mapper les erreurs d'extraction vers un état `Error` propre avec retry (l'UI a déjà le bouton retry).

### Anti-Pattern 8 : ignorer la mort du process pendant whisper/rendu

**Ce que les gens font :** garder l'état du pipeline uniquement en mémoire (ou dans le `Worker`).
**Pourquoi c'est faux :** Android tue volontairement les process longs ; sans état persistant, la reprise repart de zéro ou reste bloquée sur un état fantôme.
**À faire à la place :** Room = SSOT de l'étape courante + artefacts sur disque + `setProgress` à chaque transition ; reprise idempotente par étape (chaque use case vérifie la présence/validité de son artefact d'entrée avant de retravailler).

## Points d'Intégration

### Services externes

| Service | Pattern d'intégration | Pièges |
|---------|----------------------|--------|
| YouTube (via NewPipeExtractor, JitPack) | `data/remote/youtube/NewPipeExtractorWrapper` derrière `domain/repository/YouTubeRepository` ; OkHttp pour les flux | Cassures fréquentes côté YouTube (hotfix obligatoires) ; URL de flux temporisées → télécharger immédiatement après extraction ; Range → 206 sinon 200 |
| Hugging Face (modèles ggml) | OkHttp streamé + `DigestInputStream` SHA-256 + resume Range ; HTTPS only (`usesCleartextTraffic=false`) | Le script officiel ne vérifie aucun hash → catalogue SHA-256 embarqué obligatoire (Partie 5 §4) ; gros fichiers → jamais en mémoire, toujours stream→disque |
| Gemini API (BYOK) | `GeminiClient` (structured outputs, schéma strict C.2) ; clé uniquement depuis `SecureStorageRepository` | Seul point de sortie de données de l'app ; ne jamais logger la clé ; échec/quota → basculer silencieusement sur l'heuristique C.1 |
| AdMob + UMP | `AdManager` @Singleton préchargé ; UMP **avant** `MobileAds.initialize()` en prod EEA/UK | Callback récompense sur main thread ; timeout 10 s → `ad_unavailable` ; jamais de blocking UI |

### Frontières internes

| Frontière | Communication | Notes |
|-----------|---------------|-------|
| presentation ↔ domain | Use cases + `StateFlow<UiState>` / événements | Aucun import Android dans domain ; VM n'appelle jamais data directement |
| worker ↔ domain | Workers appellent **uniquement** des use cases (règle normative) | C'est ce qui permet de rejouer une étape hors worker (tests, re-rendu) |
| data/native ↔ app | Interfaces domain (`TranscriptionRepository`, `RenderRepository`) ; module `whisper-native` isolé | Les wrappers sont les **seuls** à toucher JNI/FFmpegKit/ML Kit ; fake implementations pour tests UI |
| whisper-native ↔ Gradle | `externalNativeBuild` CMake, submodule épinglé, `implementation(projects.whisperNative)` | Vérif 16 KB en CI ; ABI filter arm64-v8a prioritaire (armeabi-v7a optionnel — minSdk 33 = matériel très majoritairement arm64) |
| data/local/secure ↔ tout | `SecureStorageRepository` seule porte d'accès au stockage chiffré | Le worker ne lit jamais directement EncryptedSharedPreferences |

## Sources

**Documentation officielle Android (confiance HIGH)**
- [Guide d'architecture des apps](https://developer.android.com/topic/architecture) — couches, domain optionnel, UDF, SSOT, Hilt recommandé
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) — exigence Play (cible API 35+, blocage updates après février 2027), NDK r28+ default, flags linker r27−, vérification `llvm-objdump`/`zipalign`
- [Foreground service types requis (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — permissions `FOREGROUND_SERVICE_DATA_PROCESSING`
- [Behavior changes Android 15](https://developer.android.com/about/versions/15/behavior-changes-15) — timeout 6 h `dataSync`/`mediaProcessing`, `Service.onTimeout()` ; `dataProcessing` non concerné
- [WorkManager — référence WorkManager/WorkInfo](https://developer.android.com/reference/androidx/work/WorkManager) — `setProgress`, flows d'observation, états, `cancelWorkById` et interruption de `CoroutineWorker`
- [UIDT (user-initiated data transfer)](https://developer.android.com/develop/background-work/background-tasks/uidt) + [retour d'expérience Google Maps](https://android-developers.googleblog.com/2024/09/google-maps-improved-download-reliability-user-initiated-data-transfer-api.html)
- [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work) — 2.10.0 compilé SDK 35 (compat Android 15)
- [Ajouter du code C/C++ (externalNativeBuild)](https://developer.android.com/studio/projects/add-native-code)
- [MediaMetadataRetriever (référence)](https://developer.android.com/reference/android/media/MediaMetadataRetriever) — recommandation `getFramesAtIndex` pour les frames consécutives

**ML Kit (confiance HIGH)**
- [Face Detection Android](https://developers.google.com/ml-kit/vision/face-detection/android) — `com.google.mlkit:face-detection` (bundled), options, tailles d'entrée recommandées

**whisper.cpp (confiance HIGH — dépôt officiel ggml-org/whisper.cpp)**
- [`include/whisper.h`](https://github.com/ggml-org/whisper.cpp/blob/master/include/whisper.h) — `token_timestamps`, `max_len`, `split_on_word`, t0/t1 en centièmes de seconde, `abort_callback`/callbacks d'interruption
- [`examples/whisper.android`](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android) — module Gradle `lib`, `externalNativeBuild` CMake, cibles par ABI, FetchContent ggml
- [`models/download-ggml-model.sh`](https://github.com/ggml-org/whisper.cpp/blob/master/models/download-ggml-model.sh) — source officielle des modèles (`huggingface.co/ggerganov/whisper.cpp`), **sans** vérification de checksum

**FFmpegKit / libass (confiance MEDIUM — communautés + Sonatype)**
- [Dépôt arthenica/ffmpeg-kit (archivé)](https://github.com/arthenica/ffmpeg-kit) + [wiki Android (API execute/cancel/ReturnCode)](https://github.com/arthenica/ffmpeg-kit/wiki/Android)
- [Fork de continuation maitrungduc1410 sur Maven Central](https://central.sonatype.com/artifact/io.github.maitrungduc1410/ffmpeg-kit-min) — remplacement drop-in aligné 16 KB
- [FFmpeg-Kit + 16 KB Page Size (ProAndroidDev)](https://proandroiddev.com/ffmpeg-kit-16-kb-page-size-in-android-d522adc5efa2) — constat d'incompatibilité des binaires historiques
- [libass font setup (QtAV wiki)](https://github.com/wang-bin/QtAV/wiki/libass-Font-Setup) + [hardsub fontsdir/force_style (Ask Ubuntu)](https://askubuntu.com/questions/1336927/) + [bug sélection de police libass corrigé en 0.17.2 (Video SE)](https://video.stackexchange.com/questions/36637/)

**HTTP / NewPipeExtractor (confiance HIGH pour HTTP, MEDIUM pour l'état du projet)**
- [MDN — HTTP range requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Range_requests) — 206/416, reprise, `If-Range`
- [NewPipe Extractor](https://github.com/teamnewpipe/newpipeextractor) + [documentation mainteneurs](https://teamnewpipe.github.io/documentation/06_releasing/) — « quand YouTube change, l'extractor casse instantanément »

---
*Recherche architecture pour : ShortifyLocal AI (traitement média/IA on-device Android)*
*Recherché : 2026-09-04*
