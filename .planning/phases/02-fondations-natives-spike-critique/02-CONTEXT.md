# Phase 2: Fondations natives (spike critique) - Context

**Gathered:** 2026-09-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Prouver la chaîne native sur appareil physique ARM64 exclusivement : module `whisper-native/` (whisper.cpp b4938 en submodule, CMake/NDK, bindings bruts) transcrivant mot-à-mot via un wrapper Kotlin JNI logé dans `data/native/whisper/` de `:app` ; preuve FFmpeg du fork (WAV 16 kHz mono + volumedetect + assertions FFprobeKit) ; alignement 16 KB vérifié par `verify_p2.sh` local (zéro CI cloud) ; benchmark on-device Tiny/Base/Small dont le JSON mécanique fige le défaut Base sous porte de viabilité (RTF ≤ 0,50, zéro OOM, zéro thermal throttle). Le spike vit en tests d'instrumentation uniquement — aucune UI, aucun pipeline, aucun Room.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**7 requirements are locked.** See `02-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `02-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):** module `whisper-native/` (submodule b4938, CMake/NDK, bindings JNI, NDK ≥ r28 clause fraîcheur E2, flags linker 16 KB, ABIs arm64-v8a + x86_64) ; wrapper Kotlin JNI dans data/native/whisper/ (couche data de :app) ; tests d'instrumentation spike (FFmpeg + volumedetect + FFprobeKit, transcription mot-à-mot + assertions manifest TTS) ; scripts versionnés génération assets TTS (+manifest) et `verify_p2.sh` (16 KB + intégrité SHA-256 des `.so` du fork) ; benchmark on-device Tiny/Base/Small → `02-BENCHMARK.md` + ligne PROJECT.md Key Decisions ; recette exécution ARM64 physique (ANDROID_SERIAL + triple assert) ; épinglage NDK dans `gradle/libs.versions.toml`.

**Out of scope (from SPEC.md):** pipeline utilisateur (téléchargement YouTube, NewPipeExtractor, worker foreground, annulation utilisateur worker/UI) — Phase 4 ; UI y compris écran/bouton debug — Phase 6 ; Room, coffre-fort, Whisper Manager (SEC-05) — Phase 3 ; Gemini BYOK et découpage — Phase 4 ; AdMob/UMP — Phase 7 ; CI cloud ; benchmark sur émulateur ; repli automatique Tiny ; artefact mort `com.arthenica` (prohibition P1).

</spec_lock>

<decisions>
## Implementation Decisions

### Modèles ggml (provenance, pin, stockage, variante)
- **D-01:** Les binaires Tiny/Base/Small sont obtenus par un **script hôte versionné `scripts/fetch_models_p2.sh`** (dev-time, hors app — respecte le MUST NOT réseau) : téléchargement depuis la source officielle ggerganov/whisper.cpp (HuggingFace) ; licence Whisper (MIT modifié) ∈ liste blanche E1, **vérifiée à l'exécution** ; SHA-256 des 3 fichiers épinglés au repo dans un **manifest versionné** (valeurs copiées depuis les sommes officielles publiées, source + date consignées — **pas de TOFU**) et vérifiés au téléchargement (échec si divergence) ; push via adb ; zéro `.bin` versionné au repo ; SHA-256 + URL + date également consignés dans `02-BENCHMARK.md` pour audit.
- **D-02:** Sur l'appareil, modèles, WAV et sorties de run vivent dans le **stockage externe app-scoped (`getExternalFilesDir`), persistant** — inspection adb des artefacts sans teardown, pas de nettoyage automatique.
- **D-03:** Le benchmark P2 mesure les **bins officiels standard** ggerganov/whisper.cpp (fp16, SHA-256 épinglés — référence conservative, même si plus lourds que l'anti-sèche « Base ~57 Mo »). La **quantization est une décision Phase 3** (Whisper Manager) ; si la variante embarquée diffère, **re-benchmark obligatoire**. Aucune source quantizée hors repo officiel en P2 (empêche tout miroir non officiel). — **Reversibility:** costly — les mesures consignées dans 02-BENCHMARK.md référencent ces bins exacts ; changer de variante invalide le benchmark et impose une session complète de re-mesure sur l'appareil physique

### API du wrapper JNI (data/native/whisper/)
- **D-04:** Forme : **suspend one-shot** — `suspend fun transcribe(wav, model, abort: () -> Boolean): List<WordTimestamp>` exécutée sur `Dispatchers.Default` (jamais le main thread) ; data class `WordTimestamp(word, startMs, endMs)` mappée côté Kotlin = **contrat typé P2 et P4** ; la sérialisation JSON Fonction B est différée (détail d'implémentation, pas le contrat du wrapper). — **Reversibility:** costly — la signature est le contrat repris par l'instrumentation P2 et le mapping worker de la Phase 4 ; la changer casse les deux call sites
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Exigences verrouillées & contexte projet
- `.planning/phases/02-fondations-natives-spike-critique/02-SPEC.md` — **Locked requirements — MUST read before planning** : 7 exigences, boundaries, 11 critères d'acceptation, Edge Coverage 14/16, Prohibitions 6/7
- `.planning/PROJECT.md` — Key Decisions (GPL-3.0, fork 8.1.7, distribution hybride, épinglage versions, double cible D-03/D-04) ; recevra la ligne « modèle défaut figé » à la clôture
- `.planning/REQUIREMENTS.md` — PROJ-02 (16 KB), PIPE-03 (mot-à-mot, `token_timestamps=true`), WHSP-01 (tailles modèles 39/57/184 Mo)
- `.planning/phases/01-chafaudage-d-cisions-bloquantes/01-CONTEXT.md` — décisions P1 (D-01 environnement, D-02 toolchain auto-provisioning, D-03/D-04 double cible adb)

### Cahier des charges normatif (prime : `docs/ERRATA-2026-09-05.md`)
- `docs/ERRATA-2026-09-05.md` — **errata normatif** : E2 versions (fork 8.1.7, whisper.cpp b4938, flags linker 16 KB), **E3 annulation propagée** (abort callback entre chunks, `FFmpegSession.cancel()` sur `invokeOnCancellation`), E1 licences (Whisper MIT modifié ∈ liste blanche)
- `docs/01-Description.md` — Fonction B (JSON `{word, start, end}` en ms), §2.2 contrainte 16 KB, §3.1–3.2 Clean Architecture (le wrapper vit en couche `data/`)

### Patterns de vérification existants
- `scripts/verify_p1.sh` + `scripts/verify_emulator_p1.sh` — pattern batterie mécanique locale à étendre en `verify_p2.sh`
- `gradle/libs.versions.toml` — entrées `whisperCpp = "b4938"` et `ffmpegKit = 8.1.7` (l'entrée NDK y sera ajoutée)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `scripts/verify_p1.sh`, `verify_emulator_p1.sh` : pattern batterie bash (exit non-zero, asserts adb `getprop`) — à étendre pour `verify_p2.sh` (16 KB, triple assert, pull JSON benchmark)
- Module `:app` opérationnel (Hilt, WorkManager, Compose) : le wrapper `data/native/whisper/` s'y greffe sans nouveau wiring DI en P2 (exercé par instrumentation seule)
- Catalogue `gradle/libs.versions.toml` : `whisperCpp = "b4938"` déjà épinglé (entrée commentaire à promouvoir en entrée NDK + submodule)

### Established Patterns
- Catalogue unique, zéro version inline (exception documentée `settings.gradle.kts`) — l'entrée `ndkVersion` suit la même discipline
- Vérification double cible P1 : émulateur install-only, physique exécution — le SPEC P2 durcit en physique exclusif
- Aucune suite androidTest n'existe encore : P2 crée la première (`connectedDebugAndroidTest`)

### Integration Points
- `settings.gradle.kts` : `include(":whisper-native")` à ajouter (l'arborescence P1 a laissé le point d'extension ouvert)
- `app/build.gradle.kts` : dépendance `implementation(project(":whisper-native"))` ; le fork 8.1.7 reste la dépendance FFmpeg existante de `:app`
- `data/native/whisper/` : nouveau sous-package de la couche data existante (`app/src/main/java/com/shortifylocal/ai/data/`)

</code_context>

<specifics>
## Specific Ideas

- Modèles **multilingues** obligatoires (`.bin` standards 39/57/184 Mo de WHSP-01) — jamais les variantes `.en` : l'app vise AR/JA/KO en v1.
- Pin anti-TOFU : les SHA-256 du manifest sont copiés depuis les sommes officielles publiées (source + date consignées), pas auto-certifiés au premier téléchargement.
- Le JSON benchmark est la source mécanique unique de la fiche — la fiche est *remplie depuis* le JSON, jamais saisie à la main.
- La session benchmark ~1 h est isolée du spike fonctionnel (arg `bench=on`) pour garder le test de preuve rapide et exécutable seul.
- La recette adb (ANDROID_SERIAL, triple assert, stay-awake, batterie < 30 %) est déjà normative au SPEC — non re-mintée en décision ici.

</specifics>

<deferred>
## Deferred Ideas

- **Quantization des modèles ggml** (variante embarquée plus légère que fp16) → décision Phase 3 (Whisper Manager), avec **re-benchmark obligatoire** si variante différente de celle mesurée en P2.
- **Streaming de progression (Flow de tokens)** → la progression chunks/checkpoints est un sujet PIPE-03/Phase 4 ; le contrat suspend one-shot de P2 est volontairement minimal.

</deferred>

---

*Phase: 02-fondations-natives-spike-critique*
*Context gathered: 2026-09-06*
