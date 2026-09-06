# Phase 2: Fondations natives (spike critique) — Specification

**Created:** 2026-09-06
**Ambiguity score:** 0.08 (gate: ≤ 0.20)
**Requirements:** 7 locked

## Goal

La chaîne native est prouvée exclusivement sur appareil physique ARM64 : un module `whisper-native/` (whisper.cpp b4938, CMake/NDK, JNI) transcrit mot-à-mot un WAV validé mécaniquement contre un manifest TTS, une commande FFmpeg du fork convertit en WAV 16 kHz mono avec propriétés assertées, l'alignement 16 KB est vérifié par script local (`verify_p2.sh`), et un benchmark Tiny/Base/Small consigné fige le défaut Base sous porte de viabilité (RTF ≤ 0,50, zéro OOM, zéro thermal throttle).

## Background

Seul le module `:app` existe : il compile, s'installe et se lance (émulateur x86_64 prouvé en Phase 1 ; jambe appareil physique ARM64 en UAT ouverte — prérequis de clôture de cette phase). Le fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` est dépendance de `:app` mais **aucun de ses `.so` n'a jamais été chargé ni aucune commande exécutée** (P1 install-only). whisper.cpp est épinglé `b4938` dans `gradle/libs.versions.toml` **en entrée commentaire** : la source n'est ni clonée ni intégrée, aucune config CMake/NDK n'existe nulle part, aucun binding JNI, aucun asset audio dans le dépôt, aucun script de vérification Phase 2. Le critère 3 du roadmap mentionne « en CI » : **aucune CI cloud n'existe et aucune ne sera créée** — le pattern Phase 1 (batterie bash locale : `verify_p1.sh`, `verify_emulator_p1.sh`) est étendu (`verify_p2.sh`). Les flags linker 16 KB sont déjà prescrits par l'errata : `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`. Le format de sortie normatif (Fonction B) : JSON `{word, start(ms), end(ms)}` avec `token_timestamps=true`.

## Requirements

1. **Module whisper-native** : module Gradle `whisper-native/` (Android library) compilant whisper.cpp épinglé commit exact `b4938` (submodule git) via CMake/NDK avec bindings JNI Kotlin, produisant des `.so` pour arm64-v8a + x86_64 avec les flags linker 16 KB de l'errata.
   - Current: aucun module natif, aucune config CMake/NDK, aucun binding JNI ; whisper.cpp `b4938` en entrée commentaire du catalogue seulement
   - Target: module `whisper-native/` intégré via `externalNativeBuild` CMake ; submodule épinglé à `b4938` ; `ndkVersion` câblé depuis une entrée unique de `gradle/libs.versions.toml` (≥ r28, dernière stable compatible b4938, clause de fraîcheur E2 — version exacte + date de vérification consignées dans la fiche phase et PROJECT.md) ; ABIs arm64-v8a + x86_64 (x86_64 compile-only pour tests unitaires, jamais exécuté)
   - Acceptance: sur un clone frais, `git submodule update --init` puis build OK (2 ABIs produites) ; submodule absent → échec CMake explicite (message actionnable, pas d'échec cryptique) ; l'entrée NDK existe dans le catalogue avec sa date de vérification

2. **Preuve FFmpeg du fork sur appareil** : un test d'instrumentation exécute une commande FFmpeg triviale du fork — conversion en WAV 16 kHz mono pcm_s16le — puis un pass `volumedetect`, chacun avec rc=0, et asserte les propriétés du WAV produit via FFprobeKit.
   - Current: le fork 8.1.7 est dépendance de `:app` ; zéro `.so` chargé, zéro commande exécutée
   - Target: instrumenté : conversion rc=0, `volumedetect` rc=0 ; FFprobeKit asserte `sample_rate=16000`, `channels=1`, `codec=pcm_s16le`, durée = durée source ± 0,1 s, taille non nulle — le contrat d'entrée de whisper est vérifié en Phase 2 pour isoler les fautes FFmpeg des fautes whisper (non reporté à Phase 4)
   - Acceptance: `connectedDebugAndroidTest` vert sur ARM64 physique ; les 5 propriétés WAV assertées mécaniquement (rc=0 seul ne suffit pas)

3. **Transcription mot-à-mot validée contre manifest TTS** : whisper.cpp transcrit le WAV fonctionnel avec `token_timestamps=true` et restitue chaque mot `{word, start, end}` en millisecondes ; l'alignement est asserté mécaniquement contre le manifest de synthèse.
   - Current: aucun code whisper, aucune transcription, aucune ground truth
   - Target: bindings JNI retournant le JSON normatif ; contrat d'appariement : normalisation (minuscules, ponctuation retirée) puis appariement manifest→whisper par rang ; assertions : ≥ 1 mot, |start_whisper − start_manifest| ≤ 200 ms par mot apparié, starts monotones non décroissants, end ≥ start
   - Acceptance: test instrumenté vert avec le contrat ci-dessus ; transcription vide (0 mot) → échec explicite du test (jamais de PASS vacuo)

4. **Asset de test généré par script (zéro binaire versionné)** : un script versionné génère on-device (1) le WAV fonctionnel ~1 min via TTS système avec silence inter-mots de 200 ms et manifest JSON des timestamps exacts par construction, (2) le WAV benchmark 15–20 min par boucles FFmpeg du WAV fonctionnel (non versionné).
   - Current: aucun asset audio, aucun script de génération dans le dépôt
   - Target: WAV fonctionnel : `android.speech.tts` (aucune dépendance ajoutée), segmentation non ambiguë par silence 200 ms (élimine le cas end N = start N+1), manifest exact ; WAV benchmark : plus petit n de boucles tel que total ≥ 15 min, assert total ≤ 20 min (bornes [15, 20] incluses) sinon abort ; TTS muet/absent → abort explicite (exit non-zero, message actionnable) du test fonctionnel mot-à-mot — jamais de PASS vacuo, jamais de preuve dégradée ; le repli CC0 + SHA-256 épinglé (poussé via adb) est admissible UNIQUEMENT pour les runs benchmark (RTF/température, sans ground truth) ; jamais de WAV vide ni de manifest vide
   - Acceptance: le script produit les deux assets + manifest ; SHA-256 du WAV et du manifest consignés dans 02-BENCHMARK.md ; absence de TTS → abort explicite vérifié

5. **Vérification 16 KB mécanique locale** : `scripts/verify_p2.sh` (pattern Phase 1, zéro CI cloud) vérifie l'alignement 16 KB de chaque `.so` embarqué et l'intégrité des binaires du fork.
   - Current: aucun script Phase 2, aucune vérification d'alignement n'existe
   - Target: pour chaque `lib*.so` extrait de l'APK : `llvm-readelf -l` → alignement LOAD ≥ 16384 (2**14 ; 32768 accepté) ; `zipalign -v -c -P 16 4` sur l'APK ; échec (exit non-zero) si alignement insuffisant, si aucun `.so` trouvé (compte attendu : libs whisper JNI + libs fork présentes), ou si un `.so` du fork extrait de l'APK diverge en SHA-256 du même `.so` dans l'AAR résolu Maven Central (pin Phase 1) — interdiction de patch post-build
   - Acceptance: `verify_p2.sh` vert sur l'APK ARM64 physique ; le script échoue sur alignement < 16384, ensemble vide ou `.so` divergent ; `grep` workflows cloud dans le dépôt = 0

6. **Exécution exclusive sur appareil physique ARM64** : le spike s'exécute uniquement sur le téléphone ARM64 Android 13+ via `connectedDebugAndroidTest` (adb USB), garanti par triple assert mécanique avant toute exécution.
   - Current: Phase 1 a prouvé l'install émulateur x86_64 (`install Success`, `.so` jamais chargés) ; la jambe physique ARM64 est en UAT ouverte ; rien n'a jamais exécuté de code natif
   - Target: recette : `ANDROID_SERIAL=<serial physique ARM64>` imposé (Gradle/adb invoqués uniquement via ce serial) ; asserts avant lancement : `ro.kernel.qemu ≠ 1` ET `ro.hardware ∉ {goldfish, ranchu}` ET `ro.product.cpu.abi = arm64-v8a` ; toute violation → échec explicite AVANT lancement des tests (pas de skip silencieux, aucune discipline manuelle admise) ; émulateur x86_64 = cible de dev rapide uniquement (tests unitaires, `.so` jamais chargés) ; la jambe physique UAT de Phase 1 est un prérequis de clôture
   - Acceptance: `connectedDebugAndroidTest` vert via le serial physique ; le triple assert refuse l'exécution sur émulateur (échec explicite vérifié)

7. **Benchmark on-device et choix du modèle figé** : Tiny, Base et Small sont mesurés sur le même WAV benchmark ; le défaut est **verrouillé Base** (verrou précision — Tiny imprécis, Small gourmand) sous porte de viabilité ; les résultats sont consignés dans `02-BENCHMARK.md` + une ligne PROJECT.md Key Decisions.
   - Current: aucun chiffre de performance on-device, aucun choix de modèle argumenté
   - Target: ordre fixe Tiny → Base → Small (plus gourmand en dernier) ; cooldown mécanique entre runs : run N+1 déclenché uniquement si zone thermique ≤ baseline (mesurée avant run 1) + 2 °C, attente max 10 min sinon session abortée et documentée (ni hang infini, ni run biaisé) ; par modèle : RTF, température, pic RAM, verdict ; **RTF = durée processing wall-clock / durée audio** (≤ 0,50 = 2× plus vite que le réel), consigné à 2 décimales, formule + arrondi écrits dans la spec et la fiche, temps de chargement du modèle exclu du RTF et consigné séparément (run warm) ; OOM d'un modèle → consigné verdict « OOM » sans RTF, non bloquant (seule la porte Base bloque) ; **porte Base : RTF ≤ 0,50 inclusif (0,50 = porte passée) ET zéro OOM ET zéro thermal throttle** (statut thermique on-device, méthode nommée dans la fiche) ; batterie < 30 % stricte au départ de session (30 % pile = run invalide non consigné), non re-vérifiée par run (décharge monotone) ; run atomique : toute interruption (écran éteint, kill, abort) → run rejeté et refait en entier (aucune agrégation partielle), appareil maintenu éveillé (stay-awake) pendant la session ; Tiny/Small documentation-only (Whisper Manager Phase 3), jamais auto-élus
   - Acceptance: `02-BENCHMARK.md` contient : appareil (modèle, RAM 4–6 Go, SoC), version Android, batterie départ (< 30 %), commit whisper.cpp b4938, nb threads, SHA-256 WAV + manifest, baseline et seuil thermiques (+2 °C, max 10 min), RTF/température/pic RAM/verdict par modèle, défaut final + justification ; PROJECT.md Key Decisions reçoit la ligne du défaut figé ; porte échouée → décision propriétaire consignée en ERRATA (jamais de repli auto)

## Boundaries

**In scope:**
- Module `whisper-native/` : submodule whisper.cpp épinglé b4938, CMake/NDK, bindings JNI, NDK épinglé ≥ r28 (clause fraîcheur E2), flags linker 16 KB, ABIs arm64-v8a + x86_64
- Tests d'instrumentation spike : FFmpeg conversion + `volumedetect` + assertions FFprobeKit, transcription mot-à-mot + assertions manifest TTS
- Scripts versionnés : génération assets TTS (+manifest), `verify_p2.sh` (16 KB + intégrité SHA-256 des `.so` du fork)
- Benchmark on-device Tiny/Base/Small → `02-BENCHMARK.md` + ligne PROJECT.md Key Decisions (défaut Base figé)
- Recette d'exécution ARM64 physique : `ANDROID_SERIAL` + triple assert anti-émulateur
- Épinglage NDK dans `gradle/libs.versions.toml` (entrée unique, `ndkVersion`)

**Out of scope:**
- Pipeline utilisateur (téléchargement YouTube, NewPipeExtractor, worker foreground, annulation) — Phase 4
- UI, y compris tout écran/bouton debug — le spike vit exclusivement en instrumentation ; les écrans sont Phase 6
- Room, coffre-fort HMAC, Whisper Manager (téléchargement de modèles, SEC-05) — Phase 3
- Gemini BYOK, découpage en segments scorés — Phase 4
- AdMob récompensé + UMP — Phase 7
- CI cloud (GitHub Actions ou équivalent) — batterie locale pattern Phase 1, cohérent local-first
- Benchmark sur émulateur x86_64 — non représentatif (verrou round 1 : température/RAM/batterie propres à l'appareil réel)
- Repli automatique vitesse-seule sur Tiny / auto-élection de modèle — contredit le verrou précision (défaut Base)
- `com.arthenica:ffmpeg-kit-full-gpl` (artefact mort) — prohibition Phase 1 (grep = 0), non re-mintée ici

## Constraints

- Toolchain épinglée : NDK ≥ r28 — dernière stable compatible whisper.cpp b4938, clause de fraîcheur E2 (version exacte + date de vérification consignées) ; flags linker 16 KB prescrits par l'errata
- whisper.cpp submodule épinglé au commit exact `b4938` — jamais de version flottante
- Fork ffmpeg-kit 8.1.7 inchangé : aucun patch/modification des `.so` prébuilds (le check porte sur les binaires tels qu'embarqués)
- Local-first intégral : TTS système, adb, scripts bash — aucun service cloud, aucun téléchargement réseau de modèles (SEC-05 = Phase 3)
- Le test fonctionnel mot-à-mot (assertions manifest) est bloquant pour la clôture ; le repli CC0 n'est admissible que pour les runs benchmark sans ground truth
- La jambe physique UAT de Phase 1 (STATE.md, préoccupation ouverte) est un prérequis de clôture de la Phase 2

## Acceptance Criteria

- [ ] Sur un clone frais : `git submodule update --init` puis build du module `whisper-native` OK (arm64-v8a + x86_64) ; submodule absent → échec CMake explicite
- [ ] NDK épinglé dans `gradle/libs.versions.toml` (≥ r28, fraîcheur E2 vérifiée, version + date consignées) et câblé via `ndkVersion` du module
- [ ] `connectedDebugAndroidTest` vert sur ARM64 physique uniquement : `ANDROID_SERIAL` imposé + triple assert (`ro.kernel.qemu ≠ 1`, `ro.hardware ∉ {goldfish, ranchu}`, `ro.product.cpu.abi = arm64-v8a`) ; violation → échec explicite avant lancement
- [ ] FFmpeg fork : conversion WAV rc=0 + `volumedetect` rc=0 + FFprobeKit : 16000 Hz / 1 canal / pcm_s16le / durée source ± 0,1 s / taille > 0
- [ ] Transcription : ≥ 1 mot, |start_whisper − start_manifest| ≤ 200 ms par mot apparié (normalisation minuscules/ponctuation, appariement par rang), starts monotones non décroissants, end ≥ start ; 0 mot → échec du test
- [ ] Asset : script versionné génère WAV fonctionnel ~1 min (silence inter-mots 200 ms) + manifest exact ; WAV benchmark ∈ [15, 20] min (n minimal asserté) ; TTS muet → abort explicite (exit non-zero) ; repli CC0 réservé aux runs benchmark
- [ ] `verify_p2.sh` : alignement LOAD ≥ 16384 pour chaque `.so` (32768 accepté), `zipalign -P 16` OK, ensemble vide = échec, SHA-256 des `.so` du fork dans l'APK = ceux de l'AAR résolu ; exit non-zero sinon ; zéro workflow CI cloud dans le dépôt
- [ ] `02-BENCHMARK.md` complet : appareil/RAM/SoC/Android, batterie départ < 30 %, commit b4938, nb threads, SHA-256 WAV + manifest, baseline et seuil thermiques, ordre Tiny→Base→Small avec cooldown baseline + 2 °C (max 10 min), RTF = processing/audio à 2 décimales (chargement exclu, consigné à part), température + pic RAM + verdict par modèle, OOM consigné non bloquant, run atomique stay-awake
- [ ] Porte Base (RTF ≤ 0,50 inclusif ET zéro OOM ET zéro throttle) : passée → défaut Base figé en PROJECT.md Key Decisions ; échouée → décision propriétaire consignée en ERRATA (jamais de repli auto)
- [ ] La jambe physique UAT de Phase 1 est soldée avant clôture de la Phase 2
- [ ] grep : zéro code de téléchargement réseau de modèles dans `whisper-native/` et l'androidTest ; zéro composable/bouton spike dans `app/`

## Edge Coverage

**Coverage:** 14/16 applicable edges resolved · 0 unresolved · 2 dismissed

| Category | Requirement | Status | Resolution / Reason |
|----------|-------------|--------|---------------------|
| unclassified | R1 | ✅ covered | AC « clone frais » : `git submodule update --init` → build OK ; submodule absent → échec CMake explicite (R1) |
| unclassified | R2 | ✅ covered | Propriétés WAV assertées via FFprobeKit (16000 Hz / mono / pcm_s16le / durée ± 0,1 s / taille > 0) — AC R2 |
| adjacency | R3 | ✅ covered | Contrat ±200 ms + appariement par rang après normalisation + silence TTS inter-mots 200 ms (élimine end N = start N+1) — AC R3 |
| empty | R3 | ✅ covered | 0 mot → échec explicite du test, jamais de PASS vacuo (R3) |
| ordering | R3 | ✅ covered | Starts monotones non décroissants (R3) |
| unclassified | R4 | ✅ covered | Bornes [15, 20] min incluses (n minimal asserté), TTS muet → abort explicite, repli CC0 réservé benchmark (R4) |
| adjacency | R5 | ✅ covered | align ≥ 16384 (2**14), 32768 accepté (R5) |
| empty | R5 | ✅ covered | Ensemble vide = échec — compte attendu libs whisper + fork (R5) |
| ordering | R5 | ⛔ dismissed | Check ensembliste sans sémantique d'ordre — chaque `.so` est vérifié indépendamment |
| unclassified | R6 | ✅ covered | Triple assert anti-émulateur + `ANDROID_SERIAL` imposé, échec explicite avant lancement (R6) |
| boundary | R7 | ✅ covered | RTF ≤ 0,50 inclusif (0,50 = porte passée) ; batterie < 30 % stricte (30 % pile = run invalide non consigné) |
| adjacency | R7 | ⛔ dismissed | Un seul appareil, un seul WAV, trois modèles distincts — aucune sémantique d'ex æquo à départager |
| empty | R7 | ✅ covered | OOM consigné verdict « OOM » sans RTF, non bloquant ; seule la porte Base bloque |
| ordering | R7 | ✅ covered | Ordre fixe Tiny → Base → Small + cooldown thermique baseline + 2 °C (max 10 min, sinon session abortée et documentée) |
| precision | R7 | ✅ covered | RTF = durée processing wall-clock / durée audio, 2 décimales, formule + arrondi écrits, temps de chargement exclu et consigné séparément (run warm) |
| concurrency | R7 | ✅ covered | Run atomique : interruption → rejeté et refait en entier ; stay-awake pendant la session |

## Prohibitions (must-NOT)

**Coverage:** 6/7 applicable prohibitions resolved · 0 unresolved · 1 dismissed (cross-ref)

| Prohibition (must-NOT statement) | Requirement | Status | Verification / Reason |
|----------------------------------|-------------|--------|------------------------|
| MUST NOT ré-élire automatiquement un modèle sur critère vitesse-seule (repli Tiny) si la porte Base échoue — décision propriétaire en ERRATA uniquement | R7 | resolved | judgment (décision humaine consignée) |
| MUST NOT ajouter de code de téléchargement réseau de modèles Whisper (SEC-05 = Phase 3) ; modèles générés (TTS) ou poussés via adb | R1, R7 | resolved | test — grep zéro client HTTP/DownloadManager dans `whisper-native/` + androidTest |
| MUST NOT exposer le spike dans l'UI de production (aucun écran/bouton debug, navigation inchangée à 3 destinations) | R6 | resolved | test — grep composables/boutons spike dans `app/` = 0 |
| MUST NOT utiliser un service TTS cloud/réseau pour l'asset — `android.speech.tts` système uniquement, repli CC0 local | R4 | resolved | judgment (aucun import SDK TTS tiers) |
| MUST NOT créer de workflow CI cloud (GitHub Actions ou équivalent) — `verify_p2.sh` local uniquement | R5 | resolved | test — zéro fichier workflow cloud dans le dépôt |
| Exécution du spike/benchmark sur émulateur interdite | R6 | dismissed | Cross-ref sans contenu normatif : source de vérité unique = AC R6 (triple assert anti-émulateur + refus d'exécution avant lancement) — ligne présente pour lisibilité, pas de seconde norme divergente |
| MUST NOT patcher/modifier les `.so` prébuilds du fork (patch ELF post-build) pour passer le check 16 KB — le check porte sur les binaires tels qu'embarqués | R5 | resolved | test + judgment — `verify_p2.sh` compare le SHA-256 de chaque `.so` du fork extrait de l'APK au SHA-256 du même `.so` dans l'AAR résolu (pin Phase 1, Maven Central) ; divergence = échec |

*Note descripteurs wired-check (CHK-04, soft) : les vérifications `test` ci-dessus sont des greps/comparaisons bash logés dans `scripts/verify_p2.sh` et les greps AC — le schéma formel `check_kind` (node-test \| lint-rule) ne s'applique pas à une batterie bash ; descripteurs formels laissés vides, câblage à la charge de plan-phase dans `must_haves.prohibitions` (fail-closed assumé).*

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                              |
|--------------------|-------|------|--------|----------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | 4 critères roadmap + règle de décision benchmark verrouillée |
| Boundary Clarity   | 0.95  | 0.70 | ✓      | Out-of-scope 9 items explicites ; « CI » = script local ; ARM64 physique exclusif |
| Constraint Clarity | 0.92  | 0.65 | ✓      | NDK ≥ r28 fraîcheur E2 ; flags 16 KB errata ; contrat ±200 ms ; formule RTF |
| Acceptance Criteria| 0.90  | 0.70 | ✓      | 11 critères pass/fail ; porte Base quantifiée ; 16 KB falsifiable |
| **Ambiguity**      | 0.08  | ≤0.20| ✓      | Gate passé au round 3 (0.14) puis affiné à 0.08     |

## Interview Log

| Round | Perspective     | Question summary                                        | Decision locked                                                                 |
|-------|-----------------|---------------------------------------------------------|---------------------------------------------------------------------------------|
| 1     | Researcher      | Modalité d'exercice du spike, cible matérielle, asset    | Instrumentation only (`connectedDebugAndroidTest`) ; ARM64 physique exclusif, émulateur exclu du spike ; asset généré : TTS système + manifest, repli CC0 benchmark-only |
| 2     | Simplifier      | Noyau du benchmark, lieu de consignation, pin NDK        | 3 modèles mesurés, défaut Base verrouillé (jamais auto-réélu), porte RTF ≤ 0,5 + zéro OOM + zéro throttle ; `02-BENCHMARK.md` + ligne PROJECT.md ; NDK ≥ r28, fraîcheur E2 |
| 3     | Boundary Keeper | Signification « en CI », livrables, out-of-scope        | `verify_p2.sh` local (aucune CI cloud) ; livrables de clôture énumérés ; out-of-scope : pipeline, UI, Room, NewPipe, Gemini, AdMob, CI cloud, émulateur, repli Tiny |
| 4     | Edge probe (4 passes) | 16 arêtes moteur + arêtes manquées du classifieur  | 14 explicit, 2 dismissed : contrat ±200 ms/monotonie, ≥ 1 mot, align ≥ 16384, ensemble vide = échec, triple assert, ≤ 0,50 inclusif / < 30 % stricte, cooldown baseline + 2 °C max 10 min, OOM non bloquant, RTF processing/audio 2 déc., run atomique stay-awake, clone frais, ffprobe, bornes [15,20] + abort TTS |
| 5     | Prohibition probe | 7 must-NOT candidats (filtrage routine éjecté)        | 6 keep (P1/P4 judgment ; P2/P3/P5/P7 test) ; 1 dismissed cross-ref AC R6 ; breadcrumb canon : artefact mort com.arthenica déjà possédé par la batterie Phase 1 |

---

*Phase: 02-fondations-natives-spike-critique*
*Spec created: 2026-09-06*
*Next step: /gsd:discuss-phase 2 — implementation decisions (structure CMake, API JNI, intégration androidTest, recette benchmark)*
