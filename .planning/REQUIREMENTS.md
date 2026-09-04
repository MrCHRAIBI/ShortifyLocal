# Requirements: ShortifyLocal AI

**Defined:** 2026-09-04
**Core Value:** Transformer un lien YouTube en Shorts verticaux sous-titrés, recadrés et prêts à publier — entièrement en local sur le téléphone, sans compte, sans serveur.

## v1 Requirements

Périmètre v1 = intégralité du cahier des charges normatif (`docs/` 01–05). Chaque exigence renvoie à la partie normative correspondante.

### Fondations (PROJ)

- [ ] **PROJ-01**: Le projet se compile en APK debug sur la toolchain épinglée (Kotlin récent vérifié, AGP, Gradle, JDK 17, minSdk 33, targetSdk 36) avec catalogue unique `gradle/libs.versions.toml` — clause de fraîcheur appliquée et notée (Partie 1 §2)
- [ ] **PROJ-02**: Les bibliothèques natives (whisper.cpp, ffmpeg-kit et son substitut) sont alignées 16 KB page size et l'app s'installe et se lance sur Android 13+ (Partie 1 §2.2)
- [ ] **PROJ-03**: L'architecture respecte Clean Architecture + MVVM strict : couche `domain` sans import Android, Composables sans logique métier, flux unidirectionnel ViewModel → UseCase → Repository (Partie 1 §3)

### Pipeline A→F (PIPE)

- [ ] **PIPE-01**: L'utilisateur colle un lien YouTube (watch/shorts/embed/youtu.be) ; l'URL est validée par regex et les métadonnées (titre, auteur, durée, miniature HD stockée localement) sont récupérées via NewPipeExtractor (Partie 1, Fonction A)
- [ ] **PIPE-02**: Seul le flux audio (~128 kbps AAC/M4A) est téléchargé puis converti en WAV 16 kHz mono via FFmpeg (Partie 1, Fonction A)
- [ ] **PIPE-03**: La transcription Whisper on-device produit chaque mot avec horodatage début/fin en millisecondes (`token_timestamps=true`), exécutée hors thread UI (Partie 1, Fonction B)
- [ ] **PIPE-04**: Sans clé Gemini, les segments 15–60 s sont détectés localement : croisement pics d'amplitude FFmpeg (volumedetect/ebur128) × motifs d'accroche multilingues, score local pondéré (Partie 1, Fonction C.1)
- [ ] **PIPE-05**: Avec clé Gemini (BYOK), le découpage utilise Structured Outputs (titre, start_time, end_time, viral_score, reason, type) avec validation du schéma (Partie 1, Fonction C.2)
- [ ] **PIPE-06**: L'auto-reframe 16:9→9:16 suit le plus grand visage (ML Kit bundled, 1 frame/250 ms, lissage moyenne mobile 1 s + interpolation) et recadre en 1080×1920 (Partie 1, Fonction D)
- [ ] **PIPE-07**: Les sous-titres karaoké .ass (Montserrat-ExtraBold bundlée, blanc/jaune `{\K}` par mot, contour noir 3–4 px) sont générés en Kotlin pur et hardsubbés via libass (Partie 1, Fonction E)
- [ ] **PIPE-08**: Le rendu final combine en une commande FFmpeg : crop+scale 1080×1920+subtitles+loudnorm (I=-16, TP=-1.5, LRA=11), x264 CRF 20 / AAC 128k (Partie 1, Fonction F)
- [ ] **PIPE-09**: Le téléchargement vidéo est partiel (HTTP Range Requests via OkHttp) limité à l'intervalle temporel du Short (Partie 1, Fonction F)
- [ ] **PIPE-10**: L'annulation d'une analyse (job Kotlin) stoppe le téléchargement, la transcription whisper et la commande FFmpeg en cours (Partie 1 §3.3)
- [ ] **PIPE-11**: Le pipeline tourne exclusivement en WorkManager foreground `dataProcessing` avec machine à états `DownloadingAudio → Transcribing → Segmenting → Reframing → Subtitling → Rendering → Done | Error` et progression exposée (Partie 1 §3.4)

### Écrans & UX (UI)

- [ ] **UI-01**: Écran Accueil : salutation selon l'heure, TokenPill (solde + bouton +), champ lien YouTube avec validation, gestionnaire de tags multi-sélectionnables avec ajout via AlertDialog, 2 dernières réalisations en grille (Partie 2 §4)
- [ ] **UI-02**: Écran Détails : bannière miniature radius 32, boutons retour/partage, badges durée et nombre de Shorts, stepper de progression du pipeline avec retry sur erreur, liste des Shorts triée par score décroissant (≥75 vert « Excellent », <75 orange « Moyen ») (Partie 2 §5)
- [ ] **UI-03**: Le lecteur ShortPlayerSheet (Media3 ExoPlayer, 9:16) affiche un filigrane « APERÇU » oblique tant que le Short n'est pas déverrouillé (Partie 2 §5)
- [ ] **UI-04**: Écran Historique : recherche avec debounce 300 ms, grille 2 colonnes (miniature, titre, date locale, mini-badges de tags colorés) (Partie 2 §6)
- [ ] **UI-05**: Écran Paramètres en SoftCards catégorisées : Interface (mode sombre, langue), Intelligence Artificielle (clé Gemini, Whisper Manager), Options Professionnelles (export ZIP) (Partie 2 §7)
- [ ] **UI-06**: Navigation : pilule flottante à 3 onglets (Accueil, Historique, Paramètres) masquée sur l'écran Détails ; actions one-shot via SharedFlow (Partie 2 §3)
- [ ] **UI-07**: Design system « Soft-Clean » conforme : une seule ombre douce verticale par surface (5 % clair / 30 % sombre), rayons 32/24/16/28, interdiction formelle du neumorphisme, composants §2 avec @Preview clair/sombre/RTL (Partie 2 §1–2)

### Économie de tokens (TOK)

- [ ] **TOK-01**: Premier lancement : solde initialisé à +50, ou +70 si deep link `shortify://invite?ref=gift20` intercepté au premier lancement uniquement (Partie 3 §3, §5)
- [ ] **TOK-02**: Lancer une analyse coûte 5 tokens (coût 0 si Pass 24h actif) ; solde insuffisant → analyse bloquée + bottom sheet pub (Partie 3 §3)
- [ ] **TOK-03**: Télécharger un Short coûte 3 tokens (coût 0 si Pass 24h ou `isUnlocked`) ; débit → `isUnlocked=true` + retrait filigrane + rendu haute qualité + export galerie via MediaStore (Partie 3 §3)
- [ ] **TOK-04**: Toute mutation du solde passe par `updateTokens(amount)` (false sans débit si solde insuffisant) avec re-signature HMAC et émission dans `balanceFlow` ; l'UI se synchronise uniquement via ce flux (Partie 3 §2)
- [ ] **TOK-05**: Le partage de l'app crédite +5 une seule fois par fenêtre de 24 h ; sinon blocage silencieux (Partie 3 §3)
- [ ] **TOK-06**: Le libellé « Pass 24h actif » s'affiche dans le TokenPill tant que `now ≤ pass24h_until`, vérifié à chaque lecture (Partie 3 §4)

### Publicités (ADS)

- [ ] **ADS-01**: La récompense publicitaire (`onUserEarnedReward`) crédite +10 tokens ET active le Pass 24h (`now + 24h`), sur le main thread (Partie 3 §6.2)
- [ ] **ADS-02**: Préchargement de la pub au lancement + rechargement automatique après affichage ; timeout de 10 s → fermeture indicateur + snackbar `ad_unavailable` (Partie 3 §6.2)
- [ ] **ADS-03**: Le bottom sheet pub reste fermable à tout moment avant `onUserEarnedReward` ; fermeture anticipée = pas de récompense (Partie 3 §6.3)
- [ ] **ADS-04**: En production, le consentement UMP est demandé avant `MobileAds.initialize()` pour les utilisateurs EEA/UK ; en développement, IDs de test Android uniquement (Partie 5 §6, Partie 3 §6.1)

### Données & cache (DATA)

- [ ] **DATA-01**: Les 4 entités Room (VideoProject, ProjectTag, VideoTagCrossRef, GeneratedShort) avec FK CASCADE, index uniques `youtubeUrl` et `label`, POJOs @Transaction (Partie 4 §1–2)
- [ ] **DATA-02**: Ré-analyser une URL existante retourne le projet existant sans jamais l'écraser (`findByUrl`) (Partie 4 §3)
- [ ] **DATA-03**: La recherche est insensible à la casse Unicode : `titleSearch` pré-minorisé en Kotlin, requête `LIKE` sans `COLLATE NOCASE` (Partie 4 §4)
- [ ] **DATA-04**: GC cache au lancement et à la fermeture d'un projet : fichiers sources > 7 jours sans accès supprimés + `sourceStatus=NON_DISPONIBLE_LOCALEMENT` + bouton re-téléchargement ; MP4 des Shorts déverrouillés jamais supprimés ; `frames_tmp/` purgé après chaque rendu (Partie 4 §6)
- [ ] **DATA-05**: Room v1 avec `exportSchema=true`, migrations additives obligatoires, `fallbackToDestructiveMigration` interdit ; exceptions mappées `DB_DISK_FULL`/`DB_WRITE_FAIL` → snackbar localisé (Partie 4 §5, §7)
- [ ] **DATA-06**: L'option « Export projet complet » génère un ZIP (MP4 + WAV + ASS/SRT) via `java.util.zip` (Partie 2 §7, Partie 1 Fonction F)

### Sécurité (SEC)

- [ ] **SEC-01**: Solde, hash HMAC, sel, clé Gemini et compteurs uniquement dans EncryptedSharedPreferences `shortify_secure_prefs` (Keystore AES256_GCM) — jamais en clair, dans Room, DataStore, logs, exports ZIP ni payloads WorkManager (Partie 5 §1, §5)
- [ ] **SEC-02**: HMAC-SHA256 (sel SecureRandom 32 octets) : signature à chaque écriture, vérification au démarrage et avant chaque débit, comparaison en temps constant ; mismatch → reset 0 + re-signature + dialogue `secure_compromised` (Partie 5 §2)
- [ ] **SEC-03**: La clé Gemini est masquée par défaut, testable, supprimable physiquement, et envoyée uniquement à l'endpoint HTTPS officiel Gemini (Partie 5 §3)
- [ ] **SEC-04**: Durcissement : `allowBackup=false`, `usesCleartextTraffic=false`, R8 avec stripping des logs en release, parsing du deep link `ref` en liste blanche (Partie 5 §4–5)
- [ ] **SEC-05**: Les modèles Whisper sont téléchargés en HTTPS depuis la source officielle avec vérification SHA-256 ; échec → suppression du fichier + erreur localisée (Partie 5 §4)

### Whisper Manager (WHSP)

- [ ] **WHSP-01**: Le Whisper Manager liste les modèles Tiny (~39 Mo)/Base (~57 Mo)/Small (~184 Mo) avec téléchargement progressif, coche verte si présent, suppression manuelle (Partie 2 §7)
- [ ] **WHSP-02**: Sans modèle Whisper présent, le lancement d'une analyse redirige vers le Whisper Manager (Partie 1 Fonction B)

### Internationalisation (I18N)

- [ ] **I18N-01**: 7 langues complètes (FR défaut, EN, ES, IT, AR, JA, KO) via ressources Android — aucune chaîne en dur dans le code (Partie 2 §8)
- [ ] **I18N-02**: RTL arabe intégral : `supportsRtl`, icônes directionnelles flippées / non directionnelles intactes, alignments Start/End uniquement, bascule RTL instantanée à la sélection de la langue, persistée (Partie 2 §1.6, §7)
- [ ] **I18N-03**: Polices par écriture bundlées offline (Inter, Cairo, Noto Sans JP, Noto Sans KR) avec sélection automatique selon la locale (Partie 2 §1.4)

## v2 Requirements

Différé aux versions futures. Suivi mais hors roadmap actuelle.

### Sécurité

- **V2SEC-01**: Code d'accès applicatif (`secure_app_passcode`) — explicitement réservé v2 dans le cahier des charges (Partie 5 §1.2)
- **V2SEC-02**: Détection de root (non bloquante) — déclarée optionnelle en v1, reportée (Partie 5 §7)

### Produit

- **V2PROD-01**: Ingestion par upload d'un fichier vidéo local — identifiée par la recherche comme premier candidat de dé-risquage policy Play (hors cahier des charges v1)

## Out of Scope

Exclusions explictes, documentées pour prévenir le scope creep.

| Feature | Reason |
|---------|--------|
| iOS | Décision stratégique : la version d'origine Flutter visait Android + iOS, cette version est Android-only (Partie 1 §0) |
| Serveur backend / comptes utilisateur | Philosophie local-first : tout vit sur l'appareil, aucune base centrale (Partie 3 §0) |
| Achats intégrés (IAP) | Monétisation uniquement par pub récompensée et tokens locaux (Partie 3 §0) |
| Analytics tierce | Cohérent avec local-first et minimisation des données (Partie 5 §6) |
| Éditeur timeline / publication directe / B-roll & musique IA / traduction-dubbing | Anti-features de la catégorie identifiées par la recherche — hors périmètre studio mobile |
| Base de code Flutter d'origine | Abandonnée comme référence de code ; seul le cahier des charges fait foi |
| `fallbackToDestructiveMigration` | Interdit formellement — perte de données utilisateur inacceptable (Partie 4 §5) |
| Neumorphisme | Interdiction formelle du design system (Partie 2 §0) |

## Traceability

Quelles phases couvrent quelles exigences. Rempli lors de la création du roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROJ-01 | Phase 1 | Pending |
| PROJ-02 | Phase 2 | Pending |
| PROJ-03 | Phase 3 | Pending |
| PIPE-01 | Phase 4 | Pending |
| PIPE-02 | Phase 4 | Pending |
| PIPE-03 | Phase 4 | Pending |
| PIPE-04 | Phase 4 | Pending |
| PIPE-05 | Phase 4 | Pending |
| PIPE-06 | Phase 5 | Pending |
| PIPE-07 | Phase 5 | Pending |
| PIPE-08 | Phase 5 | Pending |
| PIPE-09 | Phase 5 | Pending |
| PIPE-10 | Phase 4 | Pending |
| PIPE-11 | Phase 4 | Pending |
| UI-01 | Phase 6 | Pending |
| UI-02 | Phase 6 | Pending |
| UI-03 | Phase 6 | Pending |
| UI-04 | Phase 6 | Pending |
| UI-05 | Phase 6 | Pending |
| UI-06 | Phase 6 | Pending |
| UI-07 | Phase 6 | Pending |
| TOK-01 | Phase 3 | Pending |
| TOK-02 | Phase 4 | Pending |
| TOK-03 | Phase 5 | Pending |
| TOK-04 | Phase 3 | Pending |
| TOK-05 | Phase 7 | Pending |
| TOK-06 | Phase 7 | Pending |
| ADS-01 | Phase 7 | Pending |
| ADS-02 | Phase 7 | Pending |
| ADS-03 | Phase 7 | Pending |
| ADS-04 | Phase 7 | Pending |
| DATA-01 | Phase 3 | Pending |
| DATA-02 | Phase 3 | Pending |
| DATA-03 | Phase 3 | Pending |
| DATA-04 | Phase 3 | Pending |
| DATA-05 | Phase 3 | Pending |
| DATA-06 | Phase 5 | Pending |
| SEC-01 | Phase 3 | Pending |
| SEC-02 | Phase 3 | Pending |
| SEC-03 | Phase 4 | Pending |
| SEC-04 | Phase 7 | Pending |
| SEC-05 | Phase 3 | Pending |
| WHSP-01 | Phase 3 | Pending |
| WHSP-02 | Phase 4 | Pending |
| I18N-01 | Phase 7 | Pending |
| I18N-02 | Phase 7 | Pending |
| I18N-03 | Phase 7 | Pending |

**Coverage:**
- v1 requirements: 47 total
- Mapped to phases: 47
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-04*
*Last updated: 2026-09-04 after roadmap creation (traceability remplie)*
