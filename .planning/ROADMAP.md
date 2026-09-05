# Roadmap: ShortifyLocal AI

## Overview

Ce roadmap transforme un lien YouTube en Shorts verticaux prêts à publier, entièrement en local, en suivant l'ordre de construction convergent de la recherche : d'abord verrouiller le socle technique et les décisions bloquantes (supply-chain ffmpeg-kit, GPL-3.0, positionnement Play), puis prouver la chaîne native critique (whisper.cpp JNI + fork ffmpeg-kit, alignement 16 KB) avant d'investir quoi que ce soit d'autre. Viennent ensuite le cœur métier pur Kotlin et la persistance (Room, coffre-fort HMAC, économie de tokens, modèles Whisper), puis les deux goulots du pipeline : A→C (la transcription mot-à-mot est la brique structurante dont dépendent karaoké ET score) et D→F (le premier Short rendu, avec le déverrouillage tokens dans le chemin critique du rendu/export). L'interface n'est branchée sur le réel qu'après un Short rendu de bout en bout, et la phase finale consolide monétisation (AdMob/UMP), 7 langues avec RTL intégral et durcissement release. Chaque phase livre une capacité vérifiable : un APK qui tourne, une transcription native, des données signées, une analyse complète, un Short exporté, une app navigable, une app publiable.

## Phases

**Phase Numbering:**

- Phases entières (1, 2, 3) : travail planifié du milestone
- Phases décimales (2.1, 2.2) : insertions urgentes (marquées INSERTED)

Les phases décimales s'exécutent entre leurs phases entières encadrantes, en ordre numérique.

- [ ] **Phase 1: Échafaudage & décisions bloquantes** - Socle build verrouillé (catalogue épinglé, fork ffmpeg-kit) et 3 décisions transverses actées
- [ ] **Phase 2: Fondations natives (spike critique)** - whisper.cpp JNI et fork ffmpeg-kit prouvés sur appareil réel, alignement 16 KB vérifié mécaniquement
- [ ] **Phase 3: Contrats domain & socle de données** - Domain pur Kotlin, Room SSOT, coffre-fort HMAC, économie de tokens locale, Whisper Manager
- [ ] **Phase 4: Pipeline A→C — de l'URL aux segments** - Analyse complète : métadonnées, audio seul, transcription mot à mot, découpage scoré local + BYOK, worker foreground annulable
- [ ] **Phase 5: Pipeline D→F — premier Short rendu** - Auto-reframe 9:16, sous-titres karaoké hardsubbés, rendu x264 loudnorm, export galerie/ZIP avec déverrouillage tokens
- [ ] **Phase 6: Écrans & interface live** - Les 4 écrans Compose branchés sur le pipeline réel, design system Soft-Clean, structurellement RTL-ready
- [ ] **Phase 7: Monétisation, i18n & durcissement** - AdMob récompensé + UMP, économie de tokens complète, 7 langues + RTL intégral, build release durci

## Phase Details

### Phase 1: Échafaudage & décisions bloquantes

**Goal**: Le projet Android natif compile et s'exécute depuis un socle technique verrouillé — dépendances épinglées dans le catalogue unique, artefact ffmpeg-kit mort substitué — et les trois décisions transverses (supply-chain, licence, distribution Play) sont actées avant toute ligne de pipeline.
**Mode:** mvp
**Depends on**: Aucune (première phase)
**Requirements**: PROJ-01
**Success Criteria** (what must be TRUE):

  1. `assembleDebug` produit un APK installable depuis le catalogue unique `gradle/libs.versions.toml` avec versions vérifiées contre les registres officiels (clause de fraîcheur appliquée et notée) — le build ne référence plus l'artefact mort `com.arthenica:ffmpeg-kit-full-gpl` mais le fork maintenu épinglé (coordonnée exacte + version + checksum consignés).
  2. L'app s'installe et se lance sur un appareil/émulateur Android 13+ en affichant la coquille Compose avec sa navigation à 3 destinations vide.
  3. La structure Clean Architecture (couches domain/data/ui) est en place, avec WorkManager + Hilt initialisés et coquille de thème clair/sombre persistée DataStore — la tranche Room v1 (schéma commité) est reprogrammée en Phase 3 (décision propriétaire B du 2026-09-05 : Room 2.8.4 rejette un `@Database` sans entités).
  4. Les décisions bloquantes sont actées et consignées dans PROJECT.md (Key Decisions) : licence GPL-3.0 acceptée pour NewPipeExtractor et ffmpeg-kit-full-gpl, positionnement Play « studio de montage IA » avec plan B distribution directe (APK signé), stratégie x86_64/appareil ARM64 pour le développement.

**Plans:** 3/4 plans executed

Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Socle build : wrapper 9.7.1, catalogue E2 unique, module app minimal, checksum fork vérifié

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Plomberie : arborescence Clean Architecture, persistance thème DataStore (D-05) — slice Room reporté en Phase 3 (décision propriétaire B)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-03-PLAN.md — Coquille : tokens Soft-Clean Material 3, navigation 3 onglets, bascule thème câblée

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 01-04-PLAN.md — Vérification : batterie mécanique scriptée, double cible émulateur, human-checks appareil physique + visuel

**Cross-cutting constraints:**

- Un clone frais (JDK 17 + Android SDK, sans Gradle global) buildé avec le wrapper produit app-debug.apk installable ; local.properties reste gitigné
- Toutes les versions de gradle/libs.versions.toml correspondent 1:1 à l'errata E2 du 2026-09-05

### Phase 2: Fondations natives (spike critique)

**Goal**: La chaîne native est prouvée sur appareil réel — le chemin critique absolu du projet : transcription whisper.cpp mot à mot et commandes FFmpeg du fork fonctionnent, avec l'alignement 16 KB vérifié mécaniquement et un premier benchmark qui fige les choix de modèle.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: PROJ-02
**Success Criteria** (what must be TRUE):

  1. Une commande FFmpeg triviale du fork (ex. conversion en WAV 16 kHz mono) s'exécute avec succès sur un appareil Android 13+.
  2. Le module `whisper-native/` (submodule épinglé, CMake/NDK, JNI) transcrit un WAV embarqué et restitue chaque mot avec horodatages début/fin en millisecondes (`token_timestamps=true`) sur un appareil réel.
  3. Toutes les bibliothèques `.so` embarquées passent la vérification 16 KB (`llvm-readelf` / `zipalign -P 16`) en CI, et l'app s'installe et se lance sur un émulateur/appareil configuré 16 KB.
  4. Un premier benchmark whisper on-device est consigné (facteur temps réel sur vidéo 15–20 min, appareil 4–6 Go, batterie < 30 %) et sert de base au choix du modèle par défaut et à l'UX de progression.

**Plans**: TBD

### Phase 3: Contrats domain & socle de données

**Goal**: Le cœur métier pur Kotlin et la persistance sont en place : Room SSOT complète, coffre-fort chiffré avec HMAC anti-triche, économie de tokens locale fonctionnelle, gestionnaire de modèles Whisper — sans lesquels ni le pipeline ni la monétisation ne peuvent démarrer.
**Mode:** mvp
**Depends on**: Phase 1 (parallélisable avec la Phase 2)
**Requirements**: PROJ-03, DATA-01, DATA-02, DATA-03, DATA-04, DATA-05, SEC-01, SEC-02, SEC-05, WHSP-01, TOK-01, TOK-04
**Success Criteria** (what must be TRUE):

  1. L'utilisateur télécharge le modèle Whisper de son choix (Tiny/Base/Small) depuis le Whisper Manager avec progression, coche verte une fois présent, et suppression manuelle ; un échec de vérification SHA-256 supprime le fichier et affiche une erreur localisée.
  2. Ré-analyser une URL déjà connue réouvre le projet existant sans jamais l'écraser ; les 4 entités Room (projets, tags, association, Shorts) persistent avec index uniques, schéma exporté commité et migrations additives uniquement.
  3. Toute mutation du solde passe par `updateTokens()` : le premier lancement crédite +50 (+70 si le deep link d'invitation est intercepté au premier lancement uniquement), le solde vit uniquement dans le stockage chiffré avec signature HMAC vérifiée au démarrage et avant chaque débit — toute falsification déclenche remise à zéro + dialogue `secure_compromised`, et l'UI se synchronise uniquement via `balanceFlow`.
  4. Au lancement et à la fermeture d'un projet, les fichiers sources de plus de 7 jours sans accès sont purgés (statut `NON_DISPONIBLE_LOCALEMENT` proposé au re-téléchargement) tandis que les MP4 des Shorts déverrouillés sont conservés et `frames_tmp/` purgé.
  5. La recherche du domaine est insensible à la casse Unicode — titres en français, arabe, japonais et coréen retrouvés quelle que soit la casse saisie.

**Plans**: TBD

### Phase 4: Pipeline A→C — de l'URL aux segments

**Goal**: De l'URL collée aux segments viraux scorés : téléchargement audio intelligent, transcription Whisper on-device mot à mot (la brique structurante), découpage 15–60 s heuristique locale avec upgrade BYOK Gemini, le tout orchestré par le worker foreground à machine à états persistée, annulable nativement, avec le débit d'analyse soudé au lancement.
**Mode:** mvp
**Depends on**: Phase 2 et Phase 3
**Requirements**: PIPE-01, PIPE-02, PIPE-03, PIPE-04, PIPE-05, PIPE-10, PIPE-11, WHSP-02, TOK-02, SEC-03
**Success Criteria** (what must be TRUE):

  1. L'utilisateur colle un lien YouTube (watch/shorts/embed/youtu.be) : l'URL est validée, les métadonnées (titre, auteur, durée, miniature HD stockée localement) sont récupérées, et seul le flux audio est téléchargé puis converti en WAV 16 kHz mono.
  2. La transcription s'exécute sur l'appareil hors thread UI avec progression réelle (chunks + checkpoints) et restitue chaque mot horodaté ; sans modèle Whisper présent, le lancement d'une analyse redirige vers le Whisper Manager.
  3. Sans clé, des segments 15–60 s scorés sont produits par l'heuristique locale (pics d'amplitude × motifs d'accroche multilingues) ; avec une clé Gemini fournie, le découpage exploite les Structured Outputs validés par schéma — la clé restant masquée, testable, supprimable et envoyée uniquement à l'endpoint HTTPS officiel.
  4. Lancer une analyse débite 5 tokens (0 si Pass 24h actif) ; solde insuffisant → lancement bloqué avec solution de recharge proposée (bottom sheet, inerte tant que les pubs ne sont pas intégrées) — sans jamais de double débit après mort du process.
  5. Annuler une analyse stoppe effectivement téléchargement, transcription et commande FFmpeg en cours (plus aucun CPU consommé après annulation), et la machine à états du pipeline foreground expose sa progression de façon observable.

**Plans**: TBD

### Phase 5: Pipeline D→F — premier Short rendu

**Goal**: Le premier Short complet sort du téléphone : auto-reframe 9:16 autour du visage, sous-titres karaoké hardsubbés (y compris shaping arabe), rendu x264 normalisé loudnorm, export galerie et ZIP — avec le déverrouillage par tokens câblé dans le chemin critique du rendu/export, pas en surcouche.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: PIPE-06, PIPE-07, PIPE-08, PIPE-09, DATA-06, TOK-03
**Success Criteria** (what must be TRUE):

  1. Un rendu produit un MP4 vertical 1080×1920 dont le cadre suit le plus grand visage (sondage 1 frame/250 ms, lissage moyenne mobile 1 s, repli centre si aucun visage) — vérifié sur une vidéo de test multi-plans.
  2. Les sous-titres karaoké .ass sont hardsubbés dans l'image : chaque mot surligné jaune au rythme de la voix (`{\K}`), contour noir, police Montserrat-ExtraBold rendue via fontsdir — y compris sur des tests dorés FR/AR (shaping complexe)/JA/KO.
  3. Le rendu combiné FFmpeg applique crop/scale, loudnorm (I=-16, TP=-1.5, LRA=11) et x264 CRF 20 / AAC 128k ; le téléchargement vidéo partiel (HTTP Range) limite la donnée à l'intervalle du Short avec repli automatique en cas d'URL expirée.
  4. Télécharger un Short débite 3 tokens (0 si Pass 24h ou déjà déverrouillé), passe le rendu en haute qualité et exporte vers la galerie via MediaStore avec retrait du filigrane ; solde insuffisant → déverrouillage refusé et aperçu restant filigrané.
  5. L'option « Export projet complet » produit un ZIP contenant MP4 + WAV + sous-titres ASS/SRT.

**Plans**: TBD

### Phase 6: Écrans & interface live

**Goal**: Les 4 écrans Compose sont branchés sur l'état réel du pipeline (plus de fakes) avec le design system « Soft-Clean » conforme et une structure RTL-ready dès le premier composant — l'utilisateur voit l'app entière vivre sur de vraies données.
**Mode:** mvp
**Depends on**: Phase 3 (écrans démarrables sur fakes) et Phase 5 (wiring live sur le pipeline complet)
**Requirements**: UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07
**Success Criteria** (what must be TRUE):

  1. L'utilisateur navigue entre Accueil, Historique et Paramètres via la pilule flottante à 3 onglets (masquée sur Détails) ; l'Accueil affiche la salutation selon l'heure, la TokenPill synchronisée au solde avec bouton +, le champ lien YouTube validé, le gestionnaire de tags multi-sélection avec ajout via AlertDialog et les 2 dernières réalisations en grille.
  2. L'écran Détails montre la bannière radius 32, les badges durée/nombre de Shorts, le stepper branché sur la vraie progression du pipeline (`WorkInfo.progress` + bus StateFlow) avec retry sur erreur, et la liste des Shorts triée par score décroissant (≥ 75 vert « Excellent », < 75 orange « Moyen »).
  3. Le lecteur 9:16 (Media3 ExoPlayer) lit un Short et affiche le filigrane « APERÇU » oblique tant qu'il n'est pas déverrouillé.
  4. L'Historique filtre en temps réel (debounce 300 ms) sur des titres en toutes langues et affiche la grille 2 colonnes (miniature, titre, date locale, mini-badges de tags colorés) ; les Paramètres exposent les SoftCards catégorisées (Interface : mode sombre + langue ; Intelligence Artificielle : clé Gemini + Whisper Manager ; Options Professionnelles : export ZIP).
  5. Le design system Soft-Clean est conforme partout : une seule ombre douce verticale par surface (5 % clair / 30 % sombre), rayons 32/24/16/28, aucun neumorphisme, composants avec @Preview clair/sombre/RTL, alignments Start/End et icônes directionnelles structurellement corrects.

**Plans**: TBD
**UI hint**: yes

### Phase 7: Monétisation, i18n & durcissement

**Goal**: L'app est complète et prête à publier : pub récompensée avec consentement UMP conforme, économie de tokens complète (partage, Pass 24h), 7 langues avec RTL arabe intégral et polices par écriture, build release durci — les chaînes monétisation et durcissement vérifiées ensemble sur un pipeline stable.
**Mode:** mvp
**Depends on**: Phase 6 (UI en place) et Phase 5 (gating déverrouillage existant)
**Requirements**: ADS-01, ADS-02, ADS-03, ADS-04, TOK-05, TOK-06, SEC-04, I18N-01, I18N-02, I18N-03
**Success Criteria** (what must be TRUE):

  1. Regarder une pub récompensée jusqu'au bout crédite +10 tokens ET active le Pass 24h — le libellé « Pass 24h actif » apparaît dans la TokenPill et les coûts passent à 0 tant que `now ≤ pass24h_until` ; fermer le bottom sheet avant la fin ne crédite jamais rien.
  2. La pub est préchargée au lancement et rechargée automatiquement après affichage ; indisponible au-delà de 10 s → indicateur fermé + snackbar `ad_unavailable` ; en production le consentement UMP précède `MobileAds.initialize()` (EEA/UK), en développement seuls les IDs de test Android sont utilisés (contrôle CI en place).
  3. Partager l'app crédite +5 tokens une seule fois par fenêtre de 24 h, avec blocage silencieux ensuite.
  4. L'interface bascule intégralement entre FR/EN/ES/IT/AR/JA/KO : aucune chaîne en dur, RTL arabe complet (icônes directionnelles flippées, alignments Start/End, bascule instantanée persistée), polices Inter/Cairo/Noto Sans JP/KR rendues hors ligne selon la locale.
  5. Le build release est durci et vérifié : `allowBackup=false`, `usesCleartextTraffic=false`, R8 avec stripping des logs, deep link d'invitation en liste blanche — et solde/hash/clé jamais exposés en clair dans Room, DataStore, logs, exports ZIP ou payloads WorkManager.

**Plans**: TBD

## Progress

**Execution Order:**
Phases exécutées en ordre numérique : 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Échafaudage & décisions bloquantes | 3/4 | In Progress|  |
| 2. Fondations natives (spike critique) | 0/TBD | Not started | - |
| 3. Contrats domain & socle de données | 0/TBD | Not started | - |
| 4. Pipeline A→C — de l'URL aux segments | 0/TBD | Not started | - |
| 5. Pipeline D→F — premier Short rendu | 0/TBD | Not started | - |
| 6. Écrans & interface live | 0/TBD | Not started | - |
| 7. Monétisation, i18n & durcissement | 0/TBD | Not started | - |
