# Phase 2: Fondations natives (spike critique) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-06
**Phase:** 02-fondations-natives-spike-critique
**Areas discussed:** Modèles ggml (provenance & pin, stockage, variante), API du wrapper JNI, Orchestration du benchmark, Contenu parlé du WAV TTS

---

## Modèles ggml : provenance & pin

| Option | Description | Selected |
|--------|-------------|----------|
| Script hors-app + SHA-256 (Recommandé) | Script bash versionné qui télécharge depuis la source officielle puis push adb ; SHA-256 épinglés | ✓ (enrichi) |
| Manuel une fois | Téléchargement + push manuels, SHA-256 consignés à la main | |
| Source libre, pin seul | Provenance non normative, seule l'empreinte compte | |

**User's choice:** Script hôte versionné `scripts/fetch_models_p2.sh` (dev-time, hors app — MUST NOT P2 respecté) : source officielle ggerganov/whisper.cpp (HuggingFace), licence MIT modifié ∈ liste blanche E1 vérifiée à l'exécution, SHA-256 épinglés au repo en manifest versionné (valeurs copiées des sommes officielles, source + date consignées — pas de TOFU), vérifiés au téléchargement, push adb, zéro `.bin` versionné, SHA-256 + URL + date aussi dans 02-BENCHMARK.md.

**Notes:** Suivi du check « encore une question » → stockage : **External filesDir, persistant** (inspection adb des artefacts sans teardown) plutôt que filesDir + teardown.

## Modèles ggml : variante binaire (extension après gate « Explorer plus »)

| Option | Description | Selected |
|--------|-------------|----------|
| Bins officiels standard P2, quantization Phase 3 | Benchmark sur fp16 officiels SHA-256 épinglés (référence conservative) ; quantization = Phase 3 avec re-benchmark obligatoire ; aucune source quantizée hors repo officiel | ✓ |
| Anti-sèche taille (~57 Mo) prioritaire | Chercher une variante correspondant à l'estimation WHSP-01 | |

**User's choice:** Bins officiels standard ggerganov/whisper.cpp mesurés en P2 ; la quantization (variante embarquée) est une décision Phase 3 ; variante différente → re-benchmark obligatoire ; aucune source quantizée hors repo officiel en P2.

**Notes:** Le delta « ~57 Mo » (anti-sèche) vs fp16 (plus lourd) est assumé : la référence conservative prime sur l'estimation de taille.

## API du wrapper JNI

| Option | Description | Selected |
|--------|-------------|----------|
| Suspend one-shot (Recommandé) | `suspend fun transcribe(...): List<WordTimestamp>` sur Dispatchers.Default, abort coopératif, data class typée | ✓ (enrichi) |
| Flow streaming | Émission progressive des tokens | |
| JSON brut différé | String retournée, mapping au consommateur | |

**User's choice:** Suspend one-shot + l'annulation coroutine (`ensureActive()` entre chunks) combinée en OU avec le paramètre `abort` — les deux alimentent l'abort callback natif E3 ; exécution native jamais sur main thread ; sérialisation JSON Fonction B différée ; `WordTimestamp(word, startMs, endMs)` = contrat typé P2/P4.

| Option | Description | Selected |
|--------|-------------|----------|
| Result + sealed errors (Recommandé) | `Result<List<WordTimestamp>, WhisperError>` sealed exhaustif | ✓ (enrichi) |
| Exceptions standard | Contrat moins explicite | |
| Nominal seul P2 | Erreurs conçues en Phase 4 | |

**User's choice:** Sealed exhaustif sans exception contractuelle ; OOM au chargement = branche distinguable (variante dédiée ou cause typée de LoadFailed) pour le verdict benchmark « OOM » sans ambiguïté ; branche Aborted exercée en P2 par un test qui déclenche l'abort callback (preuve mécanique E3 niveau wrapper).

## Orchestration du benchmark

| Option | Description | Selected |
|--------|-------------|----------|
| @Test dédié + JSON pullé (Recommandé) | WhisperBenchmarkTest désactivable, mesures en JSON du external filesDir pullé adb | ✓ (enrichi) |
| Tout-en-un avec le spike | Un seul @Test spike + benchmark | |
| Runner hors instrumentation | Activity/binaire debug — hors périmètre SPEC | |

**User's choice:** @Test dédié dans la même suite, **off par défaut**, activé par arg instrumentation `bench=on` ; JSON écrit **uniquement sur run complété** (run interrompu → aucun JSON partiel, rejeté et refait) ; JSON = source mécanique unique de 02-BENCHMARK.md (mesures par modèle + métadonnées session) ; pull via script hôte/adb ; zéro parsing logcat pour les métriques.

## Contenu parlé du WAV TTS

| Option | Description | Selected |
|--------|-------------|----------|
| FR riche ~1 min (Recommandé) | ~90-110 mots distincts, ponctuation présente, chiffres en lettres | ✓ (enrichi) |
| Multilingue | FR/EN/AR/JA/KO — dépendance aux voix TTS rares | |
| Minimal répété | 20-30 mots répétés — ground truth affaiblie | |

**User's choice:** FR riche ~1 min + contraintes durcies : aucun nom propre, aucun chiffre (tous en lettres), aucune abréviation/trait d'union/apostrophe, ponctuation limitée à points et virgules ; texte versionné dans le script de génération, manifest dérivé par construction.

**Notes:** Extension au gate final → **garde TTS dégradé** : pré-assert mécanique du WAV avant le test fonctionnel — |durée WAV − durée manifest| ≤ 0,5 s ET volume moyen au-dessus d'un plancher de silence, sinon abort explicite au même rang que TTS muet. La recette adb a été explicitement écartée comme zone (déjà verrouillée au SPEC — non re-mintée).

## Claude's Discretion

- Structure CMake du submodule, flags de compilation whisper.cpp, organisation des sources du module
- Méthodes exactes de mesure température/pic RAM et détection du throttle (méthode nommée dans la fiche)
- Nommage exact des classes/fichiers sous contrainte du contrat wrapper
- Format JSON benchmark et script hôte de pull (contenu minimal verrouillé)

## Deferred Ideas

- Quantization des modèles ggml → Phase 3 (Whisper Manager) avec re-benchmark obligatoire si variante différente
- Streaming de progression (Flow de tokens) → PIPE-03/Phase 4
