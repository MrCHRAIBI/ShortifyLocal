# Project Research Summary

**Project:** ShortifyLocal AI
**Domain:** Application Android native on-device AI (traitement média + IA 100 % local : YouTube long → Shorts verticaux)
**Researched:** 2026-09-04
**Confidence:** HIGH (stack, plateforme Android, supply-chain) / MEDIUM (paysage concurrentiel, perfs whisper on-device, pronostics d'application des règles Play)

## Executive Summary

ShortifyLocal AI est un « studio de clipping IA » mobile : la catégorie (Opus Clip, Klap, Vizard, CapCut, quso.ai) est dominée par des services **cloud** avec compte et abonnement. Les experts du domaine convergent sur un jeu de table stakes précis — lien YouTube → multi-clips 15-60 s, auto-reframe 9:16 avec suivi du locuteur, sous-titres dynamiques mot à mot, score de viralité, normalisation loudness, aperçu + export — et le cahier des charges couvre **exactement** ce socle. La position stratégique gagnable est celle que la spec occupe déjà et qu'aucun concurrent n'occupe : **100 % on-device** (whisper.cpp + ML Kit + FFmpeg embarqués), **gratuit sans compte**, **multilingue avec RTL arabe intégral**, upgrade sémantique **BYOK Gemini**. La seule faille structurelle est le risque Play Store/ToS lié au téléchargement YouTube — risque que les concurrents web n'ont pas car ils ne distribuent pas via Play.

L'approche recommandée est celle du cahier des charges, **validée par la recherche** : Clean Architecture + MVVM strict (domain pur Kotlin), pipeline WorkManager foreground `dataProcessing` à machine à états persistée dans Room, wrappers natifs `suspend` avec annulation propagée (FFmpegKit.cancel / abort_callback whisper). La stack confirme Kotlin natif récent (Kotlin 2.4.10, AGP 9.4.0, Compose BOM 2026.08.00, Room 2.8.4 + KSP2, minSdk 33 / targetSdk 36) avec **une substitution bloquante** : `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` est **mort** (projet retiré janvier 2025, binaires supprimés de Maven Central le 2025-04-01, 404 vérifié) → remplacer par le fork communautaire **`ffmpegkit-maintained`** (maintenu par maitrungduc1410), drop-in API `com.arthenica.ffmpegkit`, coordonnée Maven exacte + version à épingler à l'échafaudage.

Trois risques structurent tout le roadmap. **(1) Supply-chain** : la substitution ffmpeg-kit est une décision de Phase 1, bloquante avant toute ligne de code. **(2) Licences** : NewPipeExtractor et ffmpeg-kit-full-gpl sont **GPL-3.0, pas LGPL** comme affirmé au cahier des charges — lier les deux rend l'app distribuable sous GPL-3.0 ; décision de distribution explicite requise **avant le développement** (option recommandée : accepter GPL-3.0 pour ces deux composants, seule combinaison couvrant tout le pipeline). **(3) Politique Play** : une app Play qui télécharge du contenu YouTube porte un risque réel de retrait (policy « Device and Network Abuse » + ToS YouTube) — décision produit au niveau milestone : viser Play avec listing repositionné « studio de montage IA » + plan B distribution directe (APK signé/site), et garder chaud l'**upload de fichier vidéo local** comme candidat v1.x de dé-risquage. Les risques techniques (alignement 16 KB, RAM/thermique whisper, mort du process, chaîne hardsub libass) ont tous des parades documentées : spike natif en premier, vérification ELF en CI, checkpointing Room, tests dorés de rendu par langue.

## Key Findings

### Recommended Stack

**En une ligne** : Kotlin natif (Kotlin **2.4.10**, AGP **9.4.0**, Gradle **9.7.1**, JDK 17, Compose BOM **2026.08.00**, Hilt **2.60.1**, Room **2.8.4** + KSP **2.3.11**, Navigation **2.10.0**, WorkManager **2.11.2**, minSdk 33 / compileSdk-targetSdk 36) ; natif : **whisper.cpp b4938 compilé depuis la source** (CMake/NDK r28+, JNI, submodule épinglé) + **remplacement obligatoire de `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (mort) par le fork `ffmpegkit-maintained` de maitrungduc1410** (variante `full-gpl`, ligne 8.1.7 recommandée, API Java identique, alignement 16 KB imposé par sa CI) ; NewPipeExtractor **v0.26.5** (JitPack, tag exact) ; ML Kit face-detection **16.1.7** bundled ; OkHttp **5.5.0** ; security-crypto **1.1.0** stable (API dépréciée — isolée derrière `SecureStorageRepository`) ; Media3 **1.11.0** ; Coil 3.6.2 ; DataStore 1.2.1.

> **Réconciliation d'une divergence entre chercheurs** : le fork de continuation d'ffmpeg-kit a été cité sous deux formes de coordonnées (`dev.ffmpegkit-maintained:...` dans STACK/PITFALLS, `io.github.maitrungduc1410:...` dans ARCHITECTURE). Il s'agit du **même projet** : le fork est **`ffmpegkit-maintained`** (dépôt GitHub `ffmpegkit-maintained/ffmpeg-kit`, maintenu par **maitrungduc1410**), qui publie sur Maven Central les lignes 6.0.x / 7.1.x / 8.1.x. **Position retenue** : traiter « ffmpegkit-maintained (par maitrungduc1410) » comme la substitution validée, et **épingler la coordonnée Maven exacte (group ID) + la version dans `gradle/libs.versions.toml` à l'échafaudage** (recommandation : `full-gpl:8.1.7`), en vertu de la clause de fraîcheur du cahier des charges. Ne jamais pointer JitPack ni `-SNAPSHOT`.

**Core technologies:**
- **Kotlin 2.4.10 + Compose (BOM 2026.08.00)** — langage unique et UI déclarative Material 3 ; clause de fraîcheur appliquée au-delà des épinglages du spec (plan B conservateur : 2.3.21).
- **Room 2.8.4 + KSP2 2.3.11** — SSOT projets/shorts/états ; kapt/KSP1 proscrits (cassés avec Kotlin 2.4 / AGP 9) ; plugin Gradle Room (`schemaDirectory`) dès v1.
- **WorkManager 2.11.2 + Hilt 2.60.1** — `PipelineWorker` foreground type `dataProcessing` (choix validé : pas de plafond 6 h/24 h contrairement à `dataSync`/`mediaProcessing` Android 15).
- **whisper.cpp b4938 (source, CMake/NDK, JNI)** — transcription mot-à-mot (`token_timestamps=true`, `max_len=1`, `split_on_word=true`) ; t0/t1 en centièmes de seconde = unité native du tag karaoké `.ass` `{\K}`.
- **ffmpegkit-maintained `full-gpl` (fork drop-in d'ffmpeg-kit)** — conversion 16 kHz, volumedetect/ebur128, crop, hardsub libass, loudnorm, x264 ; contient les 3 exigences des Fonctions E/F.
- **NewPipeExtractor v0.26.5 (JitPack)** — métadonnées + flux audio/vidéo YouTube ; **fragile par conception** (casse à chaque changement YouTube) → isolation totale derrière `YouTubeRepository`.
- **ML Kit face-detection 16.1.7 (bundled, offline)** — auto-reframe 9:16 ; version récente obligatoire (compatibilité 16 KB).
- **security-crypto 1.1.0 + HMAC-SHA256** — conforme au spec mais en mode maintenance : encapsulation stricte + plan de migration Tink/Keystore documenté.

### Expected Features

Le cahier des charges est **normatif et figé** : la recherche ne propose aucun ajout au v1. Elle confirme que le périmètre spécifié couvre l'intégralité des table stakes de la catégorie et que les différenciateurs sont réels et documentés.

**Must have (table stakes — déjà dans le v1, conformité vérifiée) :**
- Ingestion par lien YouTube (coller un lien) — geste d'entrée de toute la catégorie
- Découpage automatique multi-clips 15-60 s + score de viralité 0-100 avec raison et tri — la spec correspond exactement à la convention Opus Clip/Klap
- Auto-reframe 9:16 avec suivi du locuteur — un recadrage sans suivi rend le clip inutilisable en vertical
- Sous-titres dynamiques mot à mot (karaoké) — convention visuelle de la catégorie
- Normalisation loudness (loudnorm −16 LUFS), aperçu in-app + export galerie, progression visible (stepper 7 états), historique + recherche, transcription multilingue (7 langues = minimum crédible)

**Should have (différenciateurs — l'avantage concurrentiel) :**
- **100 % on-device / confidentialité** — LE différenciateur central : aucun concurrent grand public n'exécute transcription/clipping sur l'appareil ; coût marginal nul → rend le gratuit viable
- **Gratuit, sans compte, sans serveur** — tous les concurrents exigent compte + abonnement
- **RTL arabe intégral + 7 langues (dont JA/KO)** — le rendu RTL correct des sous-titres est une demande ouverte chez le leader (Opus Clip) ; différenciateur documenté
- **BYOK Gemini** — aucun concurrent ne fait du BYOK ; l'app reste 100 % fonctionnelle sans clé (fallback heuristique C.1)
- Monétisation tokens + pub récompensée (au lieu de l'abonnement) ; export ZIP projet complet ; recherche Unicode multilingue

**Defer (v1.x candidats et v2+ — ne PAS planifier en v1) :**
- v1.x : ajustement léger des bornes de clip (nudge ±5 s), **ingestion par fichier local (dé-risquage Play — top candidat)**, presets de sous-titres, suppression des mots de remplissage, re-génération d'un clip individuel
- v2+ : traduction de sous-titres, multi-plateformes (Vimeo/Twitch/Drive), doublage IA, publication directe, iOS
- **Anti-features (à ne jamais construire)** : éditeur timeline complet, backend/comptes, analytics tierce, IAP, détection de root bloquante

### Architecture Approach

L'architecture du cahier des charges (Clean Architecture + MVVM + UDF + SSOT Room) est **validée par les recommandations officielles Android** (couche domain optionnelle recommandée précisément pour la logique métier complexe — heuristique de découpage, économie de tokens, machine à états). Le risque technique est concentré côté natif et pipeline : un `PipelineWorker` unique long-running (foreground `dataProcessing`) délègue chaque étape A→F à un use case domain, persiste l'étape dans Room à chaque transition (reprise idempotente après mort du process), et expose la progression par double canal (`WorkInfo.progress` + bus `StateFlow`). Les wrappers natifs sont les seuls à toucher JNI/FFmpegKit/ML Kit, tous `suspend` avec annulation propagée au niveau natif.

**Major components:**
1. **`PipelineWorker` + `RenderWorker`** — machine à états `DownloadingAudio → … → Done|Error`, notification foreground, checkpointing Room, progression fine
2. **Domain pur Kotlin** — interfaces repository (`YouTubeRepository`, `TranscriptionRepository`, `RenderRepository`, `TokenRepository`, `SecureStorageRepository`…) + use cases testables sans Android
3. **Wrappers natifs** — `FFmpegWrapper` (suspend + `FFmpegKit.cancel`), `WhisperWrapper` (JNI + abort_callback, dispatcher CPU dédié `limitedParallelism(1)`, contexte libéré en `finally`), `FaceTrackerWrapper` (ML Kit)
4. **Module `whisper-native/`** — module Gradle séparé (pattern canonique `examples/whisper.android`), whisper.cpp en submodule épinglé, CMake, flags 16 KB explicites
5. **`ModelManager` (Whisper Manager)** — téléchargement streamé Hugging Face + SHA-256 (`DigestInputStream`) + reprise Range + renommage atomique ; le script officiel ne vérifie aucun checksum
6. **Data layer** — Room (SSOT), `CacheManager` (GC 7 j, purge `frames_tmp/`), `NewPipeExtractorWrapper` (isolé, tag épinglé), `GeminiClient` (structured outputs, clé redactée des logs), `SecureStorageRepository` (blob atomique solde+HMAC)

### Critical Pitfalls

**Watch Out For — les 5 pièges qui tuent le projet s'ils sont ignorés :**

1. **ffmpeg-kit mort (`com.arthenica:*` ne résout plus)** — retiré le 2025-01-06, binaires supprimés de Maven Central le 2025-04-01 (404 vérifié) ; les binaires historiques sont en plus alignés 4 KB. → Parade : fork **ffmpegkit-maintained** (drop-in), coordonnée + checksum consignés dans `libs.versions.toml` ; **bloquant en Phase 1**.
2. **Alignement 16 KB page size** — une `.so` alignée 4 KB plante au `dlopen` uniquement sur les appareils 16 KB (invisible en dev classique) et expose au retrait Play. NDK r28+ (défaut 16 KB), flags linker explicites sur whisper.cpp (`-Wl,-z,max-page-size=16384` — défaut historique à 4096 constaté), vérification mécanique en CI (`llvm-readelf`/`zipalign -P 16`) + smoke test émulateur 16 KB. > **Réconciliation d'une divergence** : l'échéance Play a été citée tantôt au 2025-11-01 (PITFALLS), tantôt au 2027-02-01 (STACK) — Google a repoussé l'échéance plusieurs fois. **Position d'ingénierie sûre : traiter l'alignement 16 KB comme obligatoire DÈS MAINTENANT** (targetSdk 36, septembre 2026), indépendamment des interprétations de calendrier ; le contrôle CI ne coûte rien et supprime le risque.
3. **NewPipeExtractor fragile** — casse à chaque changement interne YouTube (403, « Watch on the latest version », incidents récurrents 2025-2026). → Isolation 100 % derrière `YouTubeRepository`, tag exact épinglé (jamais `-SNAPSHOT`/HEAD), mapping erreurs → état `Error` propre, veille releases + procédure de hotfix 48-72 h, canal beta.
4. **Risque policy Play (téléchargement YouTube)** — Play interdit les apps qui violent les ToS d'un service ; YouTube API Developer Policies interdisent d'activer le téléchargement ; retraits documentés en masse. → Décision produit au niveau **milestone** (pas pendant le dev) : listing positionné « studio de montage IA » (jamais « YouTube downloader »), plan B APK signé/site dès l'échafaudage, jamais l'API Data v3, et l'upload fichier local gardé chaud comme sortie de secours v1.x.
5. **whisper.cpp on-device sous-estimé** — RAM runtime : tiny ~200-273 Mo, base ~350-390 Mo, small ~850-900 Mo (les tailles du cahier des charges sont des tailles de fichier, pas la conso mémoire) ; throttling thermique qui dégrade la vitesse en cours de route ; `small` sur 4 Go = kills LMK. → Modèles quantisés (Q5_0/Q8_0), transcription par chunks de 30 s avec checkpoints + `setProgress`, sonde thermique, benchmark réel 15-20 min sur Pixel 4-6 Go **avant de figer l'UX**.

**Pièges de second rang à intégrer dans les phases concernées** : mort du process → reprise non idempotente + double débit de tokens (checkpointing + débit lié à l'ID projet) ; security-crypto dépréciée + `AEADBadTagException` (catch + blob atomique solde|HMAC) ; ordre UMP → `canRequestAds()` → `MobileAds.initialize()` et récompense créditée **uniquement** dans `onUserEarnedReward` ; chaîne FFmpeg Android (fontsdir obligatoire — libass n'a ni fontconfig ni polices système, `shaping=complex` pour l'arabe, loudnorm 2 passes + `-ar 48000` (upsample 192 kHz), dimensions paires, `-ss` en décodage) ; URLs googlevideo signées/IP-lock/TTL court (Range = optimisation avec repli téléchargement complet, résoudre les URLs au moment du rendu) ; annulation native non propagée (CPU qui brûle après « annulation ») ; Room (`IGNORE` silencieux sur index unique, migrations + schémas commités, échappement LIKE) ; ML Kit (visages ≥ 100 px, frames sur disque jamais en RAM) ; deep link `shortify://invite` (liste blanche `gift20`, `onNewIntent`, mutex `is_first_launch`).

## Implications for Roadmap

### Décisions transversales à trancher (bloquent ou façonnent le roadmap)

Les 4 chercheurs convergent sur trois décisions qui doivent être actées **avant ou pendant la Phase 1** — elles ne sont pas des détails d'implémentation :

1. **Remplacement ffmpeg-kit (bloquant Phase 1)** — validé par STACK + ARCHITECTURE + PITFALLS : le fork `ffmpegkit-maintained` (maitrungduc1410) est la seule substitution drop-in maintenue, variante `full-gpl` obligatoire (libx264 + libass + loudnorm exigés par les Fonctions E/F). À verrouiller (coordonnée exacte + version + checksum) au moment de l'échafaudage ; plan B documenté (vendor `.aar` ou self-build FFmpeg).
2. **Réalité GPL-3.0 (décision de distribution avant tout développement)** — NewPipeExtractor et ffmpeg-kit-full-gpl sont **GPL-3.0**, pas LGPL : le cahier des charges est factuellement faux sur ce point. Lier les deux rend l'app **compatible GPL-3.0** (travail dérivé). Non bloquant pour une app gratuite sans serveur, mais décision explicite du propriétaire requise : (a) **recommandé** — amender la liste blanche pour accepter GPL-3.0 pour ces deux composants (seule combinaison couvrant l'intégralité du pipeline A→F) ; (b) sinon rester strict → **le pipeline A n'a pas de solution conforme** (aucun extracteur YouTube crédible hors GPL, pas de x264 hors GPL) → escalade avant tout code.
3. **Risque policy Play (décision produit au niveau milestone)** — l'app empile téléchargement YouTube × distribution Play × monétisation AdMob (la violation policy au niveau app se propage au compte AdMob). Position recommandée : cibler Play avec un listing repositionné sur le montage IA, préparer la distribution directe (APK signé) dès l'échafaudage, et garder l'**upload de fichier vidéo local** comme top candidat v1.x de dé-risquage (l'interface `VideoSourceRepository` doit rester générique dès la v1).

### Suggested Phase Structure

Ordre de construction convergent entre ARCHITECTURE (8 étapes), PITFALLS (mapping pitfall→phase) et FEATURES (graphe de dépendances). Le principe : **prouver le natif avant d'investir l'UI**, et poser les contrats tôt pour paralléliser.

**Phase 1 : Échafaudage + décisions bloquantes**
- **Rationale:** le build doit résoudre ses dépendances avant toute ligne de pipeline ; les 3 décisions transverses ci-dessus se tranchent ici.
- **Delivers:** catalogue `libs.versions.toml` complet (fork ffmpeg-kit épinglé, tag NewPipeExtractor exact, KSP2), coquille Compose + Navigation + Hilt + Room (4 entités), DataStore thème, ET les trois décisions actées (fork verrouillé, GPL-3.0 accepté/amendé, positionnement Play + plan APK documentés).
- **Addresses:** table stakes (socle technique) ; **Avoids:** Pitfall 1 (ffmpeg-kit mort), Pitfall 12 partiel (plugin schema Room dès v1), JitPack non verrouillé.

**Phase 2 : Fondations natives (spike critique — chemin critique absolu)**
- **Rationale:** un échec ici (build CMake, alignement, ABI, x86_64) invaliderait tout le reste ; une fois la transcription fonctionnant sur un vrai téléphone, ~80 % du risque technique est levé.
- **Delivers:** intégration du fork ffmpeg-kit + commande triviale (`-version`, conversion 16 kHz mono) ; module `whisper-native/` (submodule b4938, CMake, JNI) transcrivant un WAV embarqué avec `token_timestamps=true` ; vérification 16 KB des deux libs en CI ; **premier benchmark whisper sur appareil cible**.
- **Uses:** ffmpegkit-maintained full-gpl, whisper.cpp b4938, NDK r28+, flags 16 KB explicites.
- **Avoids:** Pitfall 2 (16 KB), Pitfall 5 (benchmark avant de figer l'UX).

**Phase 3 : Contrats domain**
- **Rationale:** posée tôt, parallélisable avec la Phase 2, elle permet à l'UI (fakes) et à la monétisation d'avancer.
- **Delivers:** interfaces + modèles (`WordTimestamp`, `ClipSegment`, `TokenBalance`…) + implémentations fake.
- **Implements:** domain pur Kotlin (zéro import Android).

**Phase 4 : Couche données (non-native)**
- **Rationale:** l'étape 4 d'ARCHITECTURE — à la fin, on peut déjà télécharger un modèle et un audio.
- **Delivers:** Room complète (index unique + test de conflit, schémas commités), `CacheManager` (GC 7 j), `NewPipeExtractorWrapper`, `SecureStorageRepository` (HMAC, blob atomique, catch `AEADBadTagException`), `ModelManager` + écran Whisper Manager.
- **Addresses:** Whisper Manager (prérequis pipeline), historique/tags ; **Avoids:** Pitfalls 7, 12 ; gère l'isolation NewPipeExtractor (Pitfall 3).

**Phase 5 : Pipeline A→C (download → transcription → découpage)**
- **Rationale:** la **transcription word-level est la brique structurante** : les sous-titres karaoké (balises `{\K}` dérivées des timestamps par mot) ET le score sémantique en dépendent tous les deux — sans elle, rien en aval.
- **Delivers:** `PipelineWorker` (foreground `dataProcessing`, machine à états persistée, checkpointing, annulation native) ; smart download audio + conversion 16 kHz ; transcription chunkée avec progression réelle ; heuristique C.1 (pics ebur128 + motifs multilingues) + hook BYOK Gemini C.2 ; segments 15-60 s scorés.
- **Addresses:** Fonctions A, B, C ; **Avoids:** Pitfalls 5, 6, 10 (repli Range dès l'écriture), 11 (annulation câblée dans les wrappers dès leur création).

**Phase 6 : Pipeline D→F — premier Short rendu**
- **Rationale:** l'économie de tokens est **dans le chemin critique du rendu/export** (l'export est gated par `isUnlocked`, qui exige AdMob/HMAC) : le gating doit être implémenté ici, pas en surcouhe en Phase 8.
- **Delivers:** auto-reframe ML Kit (frames FFmpeg sur disque, ≥ 100 px, lissage 1 s, fallback centre) ; `AssSubtitleGenerator` karaoké (`fontsdir` + `shaping=complex` + force_style) ; téléchargement vidéo partiel avec repli ; rendu combiné FFmpeg (crop pair, hardsub, loudnorm 2 passes + `-ar 48000`, x264) ; export MediaStore + ZIP ; preview filigranée + déverrouillage −3 tokens.
- **Addresses:** Fonctions D, E, F, export, gating ; **Avoids:** Pitfalls 9, 13, anti-pattern frames en RAM ; tests dorés par langue (FR/AR/JA/KO + son faible/fort) dès le début de la phase.

**Phase 7 : UI live wiring**
- **Rationale:** l'UI peut démarrer en parallèle dès la Phase 3 avec des fakes ; cette phase branche les 4 écrans sur le vrai état du pipeline.
- **Delivers:** stepper Détails sur `WorkInfo.progress` + bus, lecteur Media3 9:16, Historique (recherche Unicode + tags), Paramètres (BYOK, langues, thème) ; POST_NOTIFICATIONS demandée avec contexte.
- **Addresses:** design system « Soft-Clean », UDF complet.

**Phase 8 : Monétisation, i18n, durcissement, pré-release**
- **Rationale:** les chaînes monétisation et durcissement se vérifient ensembles sur un pipeline déjà stable.
- **Delivers:** AdManager + UMP (ordre canonique consent → `canRequestAds` → initialize ; récompense sur `onUserEarnedReward` ; grep CI sur l'ID de test), économie de tokens complète (Pass 24h, parrainage deep link sécurisé), 7 langues + polices par écriture, R8 + stripping logs, checklist « looks done but isn't » (reprise au kill, annulation, consent EEA simulé, GC 7 j, migrations v1→vN).
- **Avoids:** Pitfalls 8, 14 ; faux positifs HMAC ; ad_unavailable sans alternative.

### Phase Ordering Rationale

- **Pourquoi cet ordre** : les dépendances découvertes imposent natif → pipeline → UI live. Le risque est massivement natif (Pitfalls 1, 2, 5) — le spike de Phase 2 est le chemin critique absolu. La transcription word-level (Phase 5) est la brique dont dépendent karaoké ET score : elle ne peut pas être repoussée. Le gating tokens (Phase 6) est dans le chemin critique du rendu/export, pas une surcouhe. **RTL/i18n n'est PAS une Phase 8** : `Alignment.Start/End`, icônes flippées et polices doivent être structurels dès le premier composant (rattraper le RTL est la cause n°1 des UI arabes cassées) — la Phase 8 ne fait que compléter traductions, polices et tests.
- **Pourquoi ce groupement** : A→C puis D→F sépare les deux goulots (whisper/thermique puis FFmpeg/libass) et livre un « premier Short rendu » à la fin de Phase 6 — jalon de dérisquage produit majeur. Contrats domain en Phase 3 = parallélisation UI (fakes) et monétisation.
- **Comment ça évite les pièges** : chaque phase du mapping PITFALLS y est ancrée (1, 2 → Phases 1-2 ; 3, 5, 6, 10, 11, 13 → Phases 4-6 ; 7 → Phase 4 ; 9 → Phase 6 ; 8, 14 → Phase 8 ; 4 → décision milestone + pré-release).

### Research Flags

Phases nécessitant une recherche approfondie pendant le planning (`/gsd:plan-phase --research-phase`) :
- **Phase 2 (Fondations natives)** : coordonnée Maven exacte du fork + disponibilité **x86_64** (le fork annonce arm64-v8a uniquement — MEDIUM, à confirmer ; prévoir device ARM64 physique), CMakeLists whisper.cpp courant (issue #3440 : défaut 4096 constaté), version Android Studio appairée à AGP 9.4.
- **Phase 5 (Pipeline A→C)** : benchmarks whisper on-device réels (real-time factor début/fin sur 15-20 min, appareil 4-6 Go), stratégie de chunking + checkpoints, modèles quantisés.
- **Phase 6 (Pipeline D→F)** : chaîne libass/fontsdir/shaping sur Android (sources MEDIUM — communautés), harness de tests dorés de rendu (pixel-diff), TTL réel des URLs googlevideo et stratégie de repli Range.

Phases à patterns standards (recherche inutile) :
- **Phase 1 (Échafaudage)** : versions déjà vérifiées contre les registres officiels ; patterns Hilt/Room/Compose documentés.
- **Phase 4 (Data layer)** : Room/KSP/OkHttp/DataStore — patterns bien documentés, pièges déjà catalogués (Pitfall 12).
- **Phase 8 (Monétisation/durcissement)** : ordre UMP/AdMob et pièges de sécurité déjà documentés en détail dans PITFALLS — appliquer, ne pas re-rechercher.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Chaque version vérifiée contre son registre officiel le 2026-09-04 (Google Maven, Maven Central, Gradle, kotlinlang, GitHub releases) ; résidus MEDIUM : publication arm64-only du fork, appariement Android Studio/AGP 9.4 |
| Features | MEDIUM | Paysage concurrentiel croisé (pages produits officielles + reviews), évolutif ; pages conformité Play/YouTube/AdMob officielles ; positionnement « on-device inoccupé » solide mais fondé sur l'absence de concurrent, non sur une étude de marché |
| Architecture | HIGH | Patterns validés par la documentation officielle Android (UDF, SSOT, WorkManager, FGS `dataProcessing`, ML Kit, API `whisper.h`) ; MEDIUM sur le fork ffmpeg-kit (jamais testé en build réel) et les patterns communautaires libass |
| Pitfalls | HIGH | Supply-chain et plateforme multi-sources vérifiées (retraite ffmpeg-kit, dépréciation security-crypto, types FGS, policy Play officielle) ; MEDIUM sur les perfs whisper on-device et les pronostics d'application des règles Play |

**Overall confidence:** HIGH sur la faisabilité technique, MEDIUM sur le risque produit/policy — la technique est bien comprise et les pièges sont documentés ; l'incertitude principale est stratégique (survie sur Play, qualité perçue du découpage face aux concurrents cloud), pas technique.

### Gaps to Address

- **Disponibilité x86_64 du fork ffmpeg-kit** : vérifier à l'échafaudage (Phase 2) ; sinon émulateur ARM sur hôte ARM ou device physique arm64 pour tout le dev Windows. Impacte le setup dev, pas le produit.
- **Benchmark whisper sur appareils cibles** : à exécuter dans le spike Phase 2 (Pixel milieu de gamme 4-6 Go, vidéo 15-20 min, batterie < 30 %) avant de figer l'UX de progression et le choix de modèle par défaut.
- **TTL réel des URLs googlevideo** : ≈ 6 h documenté (paramètre `expire`), verrouillage IP à confirmer empiriquement ; traiter le Range comme optimisation avec repli et résoudre les URLs au moment du rendu — aucune dépendance de roadmap, mais à valider en Phase 5/6.
- **Positionnement du listing Play** : décision milestone (Phase 1) ; la formulation exacte du listing « review-proof » et la procédure d'appel restent à préparer en pré-release ; surveiller les évolutions de la policy (Google durcit par vagues).
- **Coordonnée Maven exacte du fork** : `dev.ffmpegkit-maintained` vs `io.github.maitrungduc1410` — même projet, mais la coordonnée définitive + version + checksum se verrouillent dans `libs.versions.toml` à l'échafaudage (clause de fraîcheur).

## Sources

### Primary (HIGH confidence)
- Registres officiels : `dl.google.com/android/maven2`, `repo1.maven.org` (metadata ffmpeg-kit arthenica 404 + fork), `services.gradle.org`, `kotlinlang.org/docs/releases.html`, `api.github.com` (releases NewPipeExtractor v0.26.5, whisper.cpp b4938, JitPack build ok)
- `developer.android.com` — guide d'architecture, 16 KB page sizes, FGS types + timeouts Android 15, WorkManager, externalNativeBuild, MediaMetadataRetriever, migrations Room
- `include/whisper.h` + `examples/whisper.android` (ggml-org/whisper.cpp) — token_timestamps, centièmes de seconde, abort_callback, module Gradle canonique
- ML Kit Face Detection (developers.google.com) — bundled, 100×100 px min, options
- Conformité officielle : Play Developer Content Policy + Device and Network Abuse, YouTube API Developer Policies, YouTube ToS, AdMob Policies + UMP SDK docs

### Secondary (MEDIUM confidence)
- Fork ffmpegkit-maintained (README + Maven Central) — retraite Arthenica, variantes, licences, 16 KB, arm64-only à confirmer
- Issues whisper.cpp (#959, #272, #3440, discussion #3567) — perfs Android, mémoire, alignement 4096 historique
- Issues NewPipeExtractor/NewPipe (#1185, #12930) — incidents d'extraction 2025-2026
- Pages produits concurrents : opus.pro, klap.app, vizard.ai, capcut.com, quso.ai, 2short.ai, wisecut.ai (+ Canny OpusClip pour la preuve RTL)
- Communautés FFmpeg/libass (filters docs, QtAV wiki, Ask Ubuntu/Video SE) — fontsdir, shaping, loudnorm

### Tertiary (LOW confidence)
- Captions/Dubs pricing (App Store) — non confirmé
- Articles ProAndroidDev/Medium/dev.to — dépréciation security-crypto, fix ffmpeg-kit, guides 16 KB (statut confirmé par les sources primaires)

---
*Research completed: 2026-09-04*
*Ready for roadmap: yes*
