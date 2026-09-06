---
phase: "2"
slug: "fondations-natives-spike-critique"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-06"
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Rempli par plan-phase (plans 02-01 → 02-04) — le contrat de consommation des 4 plans.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + AndroidX Test (instrumentation : runner 1.7.0, ext:junit 1.3.0 — première suite androidTest du dépôt) + batterie bash hôte (pattern verify_p1.sh) |
| **Config file** | none — Wave 0 installe (déps `androidTestImplementation` + `testInstrumentationRunner` au plan 02-01) |
| **Quick run command** | `cd "D:/projets/android/locut" && ./gradlew :app:assembleDebug && bash scripts/verify_p2.sh` (hôte, sans appareil) |
| **Full suite command** | `ANDROID_SERIAL=<serial physique> ./gradlew :app:connectedDebugAndroidTest && ANDROID_SERIAL=<serial physique> bash scripts/verify_p2.sh` |
| **Estimated runtime** | ~90 s (hôte) / ~4-6 min (suite instrumentation, benchmark skippé) / ~1 h (session `bench=on` seule) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:assembleDebug` (compile) + `bash scripts/verify_p2.sh` dès que le script existe (02-03+)
- **After every plan wave:** Run `ANDROID_SERIAL=<serial physique> ./gradlew :app:connectedDebugAndroidTest` (waves 2-3, appareil requis)
- **Before `/gsd:verify-work`:** Full suite must be green + verify_p2.sh green + benchmark-result.json pullé + 02-BENCHMARK.md remplie
- **Max feedback latency:** ~90 s hôte / ~6 min device

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | PROJ-02 (R1) | T-02-02 / T-02-03 | pin submodule + flags 16 KB au link | build (script) | `./gradlew :whisper-native:assembleDebug` + garde submodule-absent (mv/restore) | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | PROJ-02 (R1) | — | contrat D-04/D-06 compilable, symboles JNI présents | build + binaire | `./gradlew :app:assembleDebug` + grep symbole `Java_com_shortifylocal_ai_whisper_1native_WhisperNative_nativeTranscribe` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | PROJ-02 (R4/D-01) | T-02-01 | SHA-256 anti-TOFU, zéro .bin versionné | bash (hôte) | `bash scripts/fetch_models_p2.sh --no-push` + `git ls-files '*.bin'` vide | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 2 | PROJ-02 (R4/D-08/D-09/D-02) | T-02-04 | garde TTS dégradé → abort, artefacts persistants | instrumentation | `ANDROID_SERIAL=… ./gradlew :app:connectedDebugAndroidTest --tests "*TtsAssetGenerationTest*"` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 2 | PROJ-02 (R2/E3) | T-02-05 / T-02-07 | 5 propriétés WAV assertées, cancel(sessionId) | instrumentation | `… --tests "*FfmpegForkTest*"` | ❌ W0 | ⬜ pending |
| 02-02-03 | 02 | 2 | PROJ-02 (R3/D-05/D-06) | T-02-06 | 0 mot = échec, Aborted ≠ InferenceFailed | instrumentation | `… --tests "*WordTranscriptionTest*" --tests "*AbortPropagationTest*"` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 2 | PROJ-02 (R5/R6 hôte) | T-02-08 / T-02-09 | 16 KB + SHA .so == AAR + zéro cloud + getprop | bash (hôte) | `bash scripts/verify_p2.sh` | ❌ W0 | ⬜ pending |
| 02-03-02 | 03 | 2 | PROJ-02 (R5) | T-02-09 | ensemble vide = échec (fail-closed prouvé) | bash (hôte, négatif) | démo P2_APK factice → exit non-zero + « ÉCHEC » | ❌ W0 | ⬜ pending |
| 02-04-01 | 04 | 3 | PROJ-02 (R7/D-07/D-03) | T-02-11 / T-02-13 | JSON atomique, off par défaut, zéro logcat | build + instrumentation (skip) | `./gradlew :app:compileDebugAndroidTestSources` + run sans `bench=on` vert rapide | ❌ W0 | ⬜ pending |
| 02-04-02 | 04 | 3 | PROJ-02 (R6 + clôture P1) | — | refus émulateur vérifié, suite verte physique | instrumentation + bash | suite complète + `bash scripts/verify_p2.sh` + refus AVD consigné | ❌ W0 | ⬜ pending |
| 02-04-03 | 04 | 3 | PROJ-02 (R7, porte Base) | T-02-12 / T-02-14 | fiche = projection mécanique du JSON | human-action + bash | session `bench=on` (opérateur) puis grep rubriques fiche + PROJECT.md | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Module `whisper-native/` + `include(":whisper-native")` + entrées catalogue (ndk, androidxTest*, plugin android-library) — plan 02-01 Task 1
- [ ] `app/src/androidTest/` créé (première suite) : runner configuré + 5 classes de test — plans 02-01/02-02
- [ ] `scripts/verify_p2.sh` + `scripts/fetch_models_p2.sh` (+ `models_manifest_p2.txt`, `pull_benchmark_p2.sh`) — plans 02-01/02-03/02-04
- [ ] Submodule `whisper-native/whisper.cpp` épinglé `371b5a7561823ab2bb32142d2751e35e7534727b` — plan 02-01 Task 1

*Toutes les vérifications <automated> des plans pointent vers des fichiers créés en Wave 1 (module + scripts) ou par la tâche elle-même — aucune référence MISSING.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Session benchmark réelle Tiny→Base→Small (~1 h) | R7 (D-07) | batterie < 30 % stricte + appareil branché secteur + ne pas toucher l'appareil pendant ~1 h — conditions opérateur non simulables | checkpoint 02-04 Task 3 : `pull_benchmark_p2.sh --stayon` puis `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.bench=on --tests "*WhisperBenchmarkTest*"` puis pull |
| Build clone frais (submodule init → 2 ABIs) | R1 | clone + réseau + build complet ~10 min, hors fenêtre executor | `git clone --recurse-submodules D:/projets/android/locut $TMP/p2fresh && cd $TMP/p2fresh && ./gradlew :whisper-native:assembleDebug` (02-04 Task 2 : auto si possible, sinon human-check) |
| Captures jambe physique P1 (device-shell / after-toggle) | clôture P1 (STATE.md) | opération adb + copie d'écran sur le téléphone | `ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh` puis copier vers .planning/phases/01-…/screens/device-*.png |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < ~6 min (hôte : ~90 s)
- [ ] `nyquist_compliant: true` set in frontmatter (à poser par validate-phase §6)

**Approval:** pending
