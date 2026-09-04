# ShortifyLocal AI

## What This Is

Studio de montage automatisé par IA dans la poche de l'utilisateur : une application **Android native** (Kotlin + Jetpack Compose) qui transforme une vidéo YouTube longue en plusieurs clips verticaux (Shorts/Reels/TikTok) de 15 à 60 secondes, avec sous-titres karaoké dynamiques, recadrage automatique autour du visage et normalisation audio. Tout est généré **localement sur le smartphone** (whisper.cpp, ML Kit, FFmpeg), sans serveur cloud. Cible : créateurs de contenu multilingues (FR, EN, ES, IT, AR, JA, KO).

## Core Value

Transformer un lien YouTube en Shorts verticaux sous-titrés, recadrés et prêts à publier — entièrement en local sur le téléphone, sans compte, sans serveur.

## Business Context

- **Customer** : créateurs de contenu (YouTube/TikTok/Reels) qui recyclent leurs vidéos longues en formats courts, multirégionaux (7 langues).
- **Revenue model** : gratuit avec publicités récompensées AdMob (économie de tokens locale : +10/pub, +5/partage, +20/parrainage ; −5/analyse, −3/téléchargement ; Pass 24h). Aucun achat intégré, aucun serveur de paiement.
- **Success metric** : nombre de Shorts exportés par utilisateur actif (métrique à instrumenter côté local uniquement — aucune analytics tierce en v1).
- **Strategy notes** : philosophie local-first / confidentialité (« aucune donnée ne quitte l'appareil en mode sans clé ») comme argument commercial central. Source de vérité produit : cahier des charges normatif en 5 parties dans `docs/`.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Pipeline A→F complet en arrière-plan (WorkManager foreground) : téléchargement audio YouTube intelligent → transcription Whisper on-device mot à mot → découpage viral 15–60 s (heuristique locale + option Gemini BYOK) → suivi de visage & auto-reframe 16:9→9:16 → sous-titres karaoké .ass → rendu final x264 avec loudnorm et export galerie
- [ ] Interface Compose 4 écrans (Accueil, Détails, Historique, Paramètres) avec design system « Soft-Clean » (interdiction formelle du neumorphisme), thème clair/sombre persisté
- [ ] Économie de tokens 100 % locale : fonction centrale `updateTokens()`, transactions +50/+70 initial, +10 pub, +5 partage/24h, +20 parrainage deep link, −5 analyse, −3 téléchargement, Pass 24h
- [ ] Intégration AdMob récompensé (IDs de test en dev, UMP consent avant `MobileAds.initialize()` en production)
- [ ] Base Room : 4 entités (VideoProject, ProjectTag, VideoTagCrossRef, GeneratedShort), recherche multilingue Unicode, GC cache 7 jours, migrations additives obligatoires
- [ ] Sécurité : EncryptedSharedPreferences + Keystore, HMAC-SHA256 anti-triche du solde, BYOK Gemini chiffré, durcissement manifest (allowBackup=false, HTTPS only, R8 + stripping logs)
- [ ] Internationalisation complète : 7 langues (FR défaut, EN, ES, IT, AR, JA, KO), RTL arabe intégral, polices bundlées par écriture (Inter/Cairo/Noto Sans JP/KR)
- [ ] Whisper Manager in-app (modèles Tiny/Base/Small téléchargeables avec vérification SHA-256, défaut Base)
- [ ] Paramètres : clé Gemini BYOK (masquée, testable, supprimable), mode sombre, langue, export projet complet en ZIP (MP4+WAV+ASS/SRT)

### Out of Scope

- **iOS** — décision stratégique : la version d'origine Flutter visait Android + iOS, cette version est Android-only
- **Serveur backend / comptes utilisateur** — philosophie local-first : tout vit sur l'appareil, aucune base centrale
- **Achats intégrés (IAP)** — monétisation uniquement par pub récompensée et tokens locaux
- **Analytics tierce** — cohérent avec local-first et la minimisation des données
- **Détection de root bloquante** — faux positifs trop coûteux ; la réinitialisation HMAC silencieuse reste la contre-mesure
- **Version Flutter** — le cahier des charges Flutter d'origine est abandonné comme base de code ; seul le document sert de référence produit

## Context

- **Source de vérité normative** : cahier des charges en 5 parties dans `docs/` (01-Description, 02-UI-UX, 03-Monetisation, 04-Room, 05-Securite). Document « normatif et auto-suffisant » : toute extrapolation est interdite ; si un comportement n'est pas décrit, choisir l'option la plus simple et la plus locale. Les renvois entre parties sont normatifs.
- **Historique** : projet initialement conçu en Flutter (Android+iOS), réécrit en spécification Kotlin natif Android-only. Room remplace Isar (inexistant en natif) avec mapping conceptuel documenté en Partie 4.
- **Chaîne de traitement clé** : machine à états `DownloadingAudio → Transcribing → Segmenting → Reframing → Subtitling → Rendering → Done | Error`, orchestrée par `PipelineWorker` (WorkManager + Hilt, foreground `dataProcessing`), progression via `WorkInfo.progress` + bus StateFlow.
- **Dépendances natives** : whisper.cpp compilé CMake/NDK avec bindings JNI (`token_timestamps=true`) ; ffmpeg-kit-full-gpl 6.0-2 (libass + libx264 + loudnorm) ; ML Kit Face Detection modèle bundled. **Alignement 16 KB page size obligatoire** (Android 16).
- **Licences** : MIT / Apache 2.0 / BSD / LGPL + GPL-3.0 accepté uniquement pour NewPipeExtractor et ffmpeg-kit-full-gpl (décision propriétaire du 2026-09-05)
- **État du répertoire** : aucun code applicatif ; le workspace ne contient que l'outillage GSD et les documents de spécification.

## Constraints

- **Tech stack (imposé)** : Kotlin 2.3.0 (K2), Jetpack Compose (BOM 2026.06.00), Clean Architecture + MVVM strict (domain pur Kotlin sans import Android), Hilt 2.56+, Room 2.8+ (KSP), Navigation Compose 2.9+, WorkManager 2.10+, security-crypto 1.1.0-alpha06+, catalogue unique `gradle/libs.versions.toml`
- **Compatibilité** : minSdk 33 (Android 13), compileSdk/targetSdk 36 (Android 16), JDK toolchain 17, Gradle 9.4, AGP 9.1.0 — clause de fraîcheur : vérifier les versions à l'échafaudage et adopter la stable plus récente si existante
- **Performance/Plateforme** : aucun traitement lourd sur le thread UI ; bibliothèques natives alignées 16 KB ; annulation propagée (job Kotlin → commande FFmpeg)
- **Sécurité** : solde/compteurs jamais en clair (ni Room, ni DataStore, ni logs, ni payloads WorkManager) ; HMAC vérifié au démarrage et avant chaque débit ; comparaison en temps constant ; `usesCleartextTraffic=false`, `allowBackup=false`
- **Données** : `fallbackToDestructiveMigration` interdit ; `exportSchema=true` obligatoire ; URL YouTube unique (jamais ré-analyser/écraser) ; Shorts déverrouillés jamais supprimés automatiquement
- **Permissions** : INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+DATA_PROCESSING) uniquement — aucune permission de stockage (export via MediaStore)
- **Confidentialité/RGPD** : consentement UMP avant initialisation AdMob pour EEA/UK en production ; la clé Gemini ne sort vers l'API officielle que si l'utilisateur l'a fournie
- **Langue produit** : 7 langues, aucune chaîne en dur, polices bundlées offline, RTL complet

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Android-only, abandon d'iOS | Décision stratégique du cahier des charges (v1) | — Pending |
| Kotlin natif au lieu de Flutter | Réécriture de la spécification d'origine ; performance et accès natif (JNI whisper, ffmpeg-kit) | — Pending |
| Room remplace Isar | Isar inexistant en Android natif ; mapping conceptuel documenté (Partie 4) | — Pending |
| Transcription 100 % on-device (whisper.cpp JNI) | Philosophie local-first ; confidentialité ; coûts zéro | — Pending |
| Découpage viral hybride : heuristique locale + Gemini BYOK optionnel | L'app fonctionne gratuitement sans clé ; l'analyse sémantique est un upgrade BYOK | — Pending |
| Économie de tokens locale signée HMAC-SHA256 | Aucun serveur : anti-triche contre manipulation naïve, limites assumées (Partie 5 §7) | — Pending |
| Cible de distribution : Play Store ; IDs AdMob de test en dev, UMP en production | Le cahier des charges impose les IDs de test en développement et le consentement UMP en prod | — Pending |
| Documents de planification GSD en français | Cohérence avec le cahier des charges rédigé en français | — Pending |
| Acceptation GPL-3.0 pour NewPipeExtractor (v0.26.5) et ffmpeg-kit-full-gpl — amendement de la liste blanche des licences | Aucune alternative fonctionnelle crédible hors GPL couvrant le pipeline A→F (extraction YouTube + x264 + libass) ; licences vérifiées directement sur les dépôts officiels le 2026-09-04 (NewPipeExtractor = GPL-3.0, et non LGPL) | — Committed |
| Substitution obligatoire de `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (retiré de Maven Central le 2025-04-01, HTTP 404) par le fork ffmpegkit-maintained (maitrungduc1410) : `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` | API drop-in identique `com.arthenica.ffmpegkit` ; libass + x264 + loudnorm inclus ; alignement 16 KB imposé par la CI du fork ; publication arm64-v8a à confirmer en Phase 1 ; coordonnée exacte + checksum à épingler dans `libs.versions.toml` en Phase 1 | — Committed |
| Distribution hybride : Google Play avec listing repositionné « studio de montage IA » (aucune mention de téléchargement YouTube) + canal parallèle APK signé en distribution directe (GitHub Releases / site, checksums SHA-256) + upload de vidéo locale en v1.x comme dé-risquage (V2PROD-01) | L'application est légale (copie privée / fair use) ; le risque est uniquement distributionnel (policy Play « Device and Network Abuse » + ToS YouTube) ; plan B indispensable dès le jour 1 | — Committed |
| Épinglage des versions mis à jour (clause de fraîcheur) : Kotlin 2.4.10, AGP 9.4.0, Gradle 9.7.1, JDK 17, Compose BOM 2026.08.00, Hilt 2.60.1, Room 2.8.4 + KSP2 2.3.11, Navigation 2.10.0, WorkManager 2.11.2, security-crypto 1.1.0 stable, play-services-ads 25.4.0, UMP 4.0.0, ML Kit face-detection 16.1.7, Media3 1.11.0, OkHttp 5.5.0, Coil 3.6.2, DataStore 1.2.1, NewPipeExtractor v0.26.5, fork ffmpeg-kit 8.1.7, whisper.cpp b4938 | Registres officiels (Google Maven, Maven Central, services.gradle.org, kotlinlang.org, GitHub) vérifiés le 2026-09-04 ; épinglages d'origine du cahier des charges dépassés | — Committed |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-04 after initialization*
