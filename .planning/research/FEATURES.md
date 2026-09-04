# Recherche Fonctionnalités — Paysage des produits de clipping IA

**Domaine :** Recyclage vidéo par IA (longue durée YouTube → Shorts verticaux) — mobile Android, on-device
**Date de recherche :** 2026-09-04
**Confiance :** MEDIUM (paysage concurrentiel croisé entre recherches web et pages officielles ; les pages produits évoluent vite)

> **Cadre de lecture** : le cahier des charges (`docs/01` à `docs/05`) est **normatif** — le périmètre v1 est figé. Ce document ne propose pas d'ajouts au v1 : il (1) positionne le jeu de fonctionnalités spécifié face aux concurrents réels, (2) classe correctement table stakes vs différenciateurs vs anti-features, (3) flag les candidats v2 sans les faire entrer dans le v1.

---

## Paysage des fonctionnalités

### Éléments de base attendus (Table Stakes)

Fonctionnalités que les utilisateurs considèrent acquises dans la catégorie « clipping IA ». Les manquer = produit perçu comme incomplet. **Toutes sont déjà dans le périmètre v1 du cahier des charges.**

| Fonctionnalité | Pourquoi attendue | Complexité | Notes |
|---------|--------------|------------|-------|
| Ingestion par lien YouTube (coller un lien) | Le workflow « paste a link » est le cœur de Klap, 2short.ai, Opus Clip, Vizard — c'est le geste d'entrée de la catégorie | MEDIUM | NewPipeExtractor ; fragilité structurelle (ruptures à chaque changement YouTube) ; **risque ToS/Play — voir encadré conformité** |
| Découpage automatique multi-clips (15–60 s) | Table stakes absolu : Opus Clip « ClipAnything », Klap « 10+ clips en 30 s », CapCut « Long video to shorts », Vizard, quso.ai, Wisecut le font tous | HIGH | Pipeline A→F complet ; heuristique locale (pics `ebur128` + motifs d'accroche multilingues) — l'approche signal audio + sémantique est proche de ce que décrivent CapCut (détection visage/voix/engagement) |
| Auto-reframe 9:16 avec suivi du locuteur | Opus Clip (ReframeAnything, object tracking), Vizard, Klap, 2short.ai (center-stage facial tracking) — un recadrage 16:9→9:16 sans suivi rend le clip inutilisable en vertical | HIGH | ML Kit Face Detection bundled (offline), 4 fps, lissage moyenne mobile 1 s + interpolation — l'écart de qualité face à Opus Clip (tracking d'objet, multi-visages) est le principal risque de « feel cheap » |
| Sous-titres dynamiques mot à mot (style karaoké) | Convention visuelle de la catégorie : Captions/Dubs, Opus Clip (captions dynamiques « 97 % »), CapCut (auto-captions) — les clips sans sous-titres ne performent pas en feed | HIGH | whisper.cpp `token_timestamps=true` + génération `.ass` en pur Kotlin + hardsub libass — la partie on-device est le seul élément non table stakes (voir différenciateurs) |
| Score de viralité + tri des clips | Convention établie : Opus Clip (score basé hook/pacing/topic, fonctionnalité la plus citée par les créateurs), Klap (score par clip), Vizard (classement des moments). Format attendu : score 0–100 + raison courte + tri décroissant | MEDIUM (heuristique) / LOW (BYOK) | La spec correspond **exactement** à la convention : `viralityScore` 0–100, `explanationReason`, `ORDER BY viralityScore DESC`, seuil 75 → label Excellent/Moyen |
| Normalisation du son (loudness) | Attente croissante sur le format court (livraison −14/−16 LUFS sur les plateformes) ; les outils cloud la font implicitement | LOW | `loudnorm=I=-16:TP=-1.5:LRA=11` en une passe FFmpeg — déjà spécifié (Fonction F) |
| Aperçu in-app + export vers la galerie | Aucun concurrent web n'a ce problème (tout est téléchargeable) ; sur mobile c'est le débouché obligatoire | MEDIUM | Media3 ExoPlayer en `ModalBottomSheet` 9:16 + export MediaStore (aucune permission stockage) |
| Gating freemium avec filigrane sur le gratuit | Norme du marché : Klap sans filigrane uniquement payant, Opus Clip 60 min/mois gratuites, quso.ai 75 crédits/mois — le filigrane « PREVIEW » levé au débit −3 tokens est la transposition locale de cette norme | LOW | `isUnlocked` Room + overlay filigrane sur le lecteur |
| Progression visible pendant le traitement | Table stakes UX renforcé : le traitement on-device est plus long qu'en cloud, l'absence de feedback = désinstallation | LOW-MEDIUM | Stepper `st_*` (7 états) + `WorkInfo.progress` — déjà spécifié (Partie 2 §5.3) |
| Historique des projets + recherche | Attendu sur tout outil de création (retrouver ses clips) ; Vizard/quso.ai ont des bibliothèques de projets | MEDIUM | Room 4 entités + recherche Unicode pré-minorisée + debounce 300 ms |
| Transcription multilingue (langues majeures) | Opus Clip 25–30+ langues, Vizard traduction 100+, Wisecut 13 — 7 langues couvrent l'essentiel mais c'est le minimum pour être crédible multilingue | MEDIUM | Whisper Tiny/Base/Small couvrant FR/EN/ES/IT/AR/JA/KO — la **traduction** de sous-titres n'est pas dans le v1 (voir v2) |

### Différenciateurs (Avantage concurrentiel)

Fonctionnalités du cahier des charges qui constituent un **véritable** avantage face au paysage réel — avec la preuve concurrentielle.

| Fonctionnalité | Proposition de valeur | Complexité | Notes |
|---------|-------------------|------------|-------|
| **100 % on-device (whisper.cpp + ML Kit + FFmpeg embarqués)** | LE différenciateur central. Aucun concurrent grand public (Opus Clip, Vizard, Klap, quso.ai, 2short, Wisecut, CapCut) n'exécute transcription/clipping sur l'appareil — tous sont cloud. Argument commercial : « aucune donnée ne quitte le téléphone ». Écosystème whisper.cpp Android prouvé (Handy, whisper-mobile, F-Droid Whisper) mais uniquement en petits utilitaires OSS de dictée — **la position « studio de clipping privé dans la poche » est inoccupée** | HIGH | Coût marginal nul par utilisateur → rend le modèle gratuit viable là où les concurrents doivent facturer des minutes GPU ; contrepartie : temps de rendu longs sur mobile (à communiquer honnêtement) |
| **Gratuit, sans compte, sans serveur** | Tous les concurrents exigent un compte + abonnement (Captions/Dubs : abonnement hebdo ; Klap payant ; Opus Clip freemium avec login). « Coller un lien et obtenir des Shorts sans inscription » est une promesse différenciante, surtout marchés émergents | MEDIUM | Découle du local-first ; l'économie de tokens remplace le paywall |
| **RTL arabe intégral + 7 langues (dont JA/KO) avec polices bundlées** | Différenciateur documenté : Opus Clip gère l'arabe en *traduction/captions* mais le **rendu RTL correct des sous-titres reste une demande ouverte** sur son board public (canny) — les sous-titres arabes/hébreux ne s'affichent pas correctement chez le leader. Aucun concurrent n'a d'UI produit RTL complète | MEDIUM | Le RTL est structurel : `Alignment.Start/End`, icônes directionnelles flippées, Cairo/Noto bundlés — à construire dès le premier composant, pas à rattraper |
| **BYOK Gemini (analyse sémantique opt-in avec la clé de l'utilisateur)** | Aucun concurrent ne fait du BYOK : le sémantique avancé est vendu en abonnement. Ici l'utilisateur paie l'upgrade avec sa propre clé API (coût zéro pour l'éditeur) — l'app reste 100 % fonctionnelle sans clé | LOW-MEDIUM | C.1 heuristique locale = fallback gratuit permanent ; C.2 = upgrade. Transparence à afficher : en mode BYOK, la transcription sort de l'appareil (déjà prévu, Partie 5 §6.2) |
| **Monétisation tokens + pub récompensée (au lieu de l'abonnement)** | Différenciation de modèle : personne dans la catégorie ne monétise ainsi (tout est abonnement). Avantage : barrière d'entrée nulle. **C'est aussi le point de risque maximal — voir encadré conformité** | MEDIUM | `updateTokens()` central + HMAC ; Pass 24h intelligent (une pub débloque 24 h d'usage gratuit — généreux vs marché) |
| **Export projet complet ZIP (MP4 + WAV + ASS/SRT)** | Offre « pro » que les apps mobile concurrentes n'ont pas (CapCut garde les projets dans son cloud ; les outils web ne livrent que le MP4). Cible les créateurs qui remontent les clips dans leur éditeur | LOW | `java.util.zip` + artefacts déjà produits par le pipeline |
| **Tags projets + recherche Unicode multilingue (AR/JA/KO)** | Petite différenciation organisationnelle : les concurrents classent par date seulement ; la recherche qui gère É/é, العربية, 日本語 correctement est rare | LOW-MEDIUM | `titleSearch` pré-minorisé Kotlin — point d'expertise déjà normatif (Partie 4 §4) |

### Anti-features (Demandées, mais problématiques — à ne PAS construire)

| Fonctionnalité | Pourquoi demandée | Pourquoi problématique | Alternative |
|---------|---------------|-----------------|-------------|
| **Éditeur timeline complet** (type CapCut) | « Je veux ajuster le clip avant d'exporter » — demande naturelle n°1 | Coût énorme, Enterprise-feature ; détruit le positionnement « automatique » ; CapCut (gratuit, ByteDance) écrase toute concurrence sur ce terrain | v1.x candidat : ajustement léger des bornes (nudge début/fin ±5 s) avant rendu |
| **Publication directe TikTok/YT/IG** | Table stakes chez les concurrents **web** (Opus Clip, Vizard, quso.ai publient en 1 clic) | Sur mobile : APIs tierces à homologuer + approbations développeur + **empilement de risque politique** (déjà exposé par le téléchargement YouTube) | Export galerie + `ACTION_SEND` ; l'utilisateur publie lui-même (2 gestes, risque zéro) |
| **B-roll IA / musique de fond auto** | Signature d'Opus Clip (AI B-roll) et Wisecut (smart music) | Nécessite banques de licences ou génération cloud — incompatible local-first et gratuité | Rester sur le contenu source, bien sous-titré et bien recadré |
| **Traduction / doublage des sous-titres** | Forte demande (Captions/Dubs en fait son marketing ; Opus Clip traduit) | Double la charge IA (modèles de traduction/dubbing) ; Whisper `translate` ne fait qu'anglais | **Candidat v2 prioritaire** : la transcription 7 langues existe déjà, la traduction s'ajoute sans refonte |
| **Serveur backend / comptes utilisateur** | Permettrait sync multi-appareils, quotas anti-abus, stats | Coût permanent, RGPD, contredit la promesse locale-first, surface d'attaque | Architecture 100 % locale (décision normative) |
| **Analytics tierce (Crashlytics, etc.)** | Standard du marché pour la qualité | Contredit « aucune donnée ne quitte l'appareil » ; incohérent avec l'argument commercial central | Aucune télémétrie en v1 ; play console vitals (agrégé, natif) comme seule source passive |
| **Achats intégrés (IAP)** | Monétisation complémentaire évidente | Commission Google 15–30 %, obligations billing, serveurs de reçus — contredit « aucun serveur » | AdMob récompensé uniquement (décision normative) |
| **Détection de root bloquante** | Renforcerait l'anti-triche tokens | Faux positifs coûteux sur utilisateurs légitimes | Réinitialisation HMAC silencieuse (décision normative, Partie 5 §7) |
| **iOS** | Marché plus monétisable | Hors périmètre stratégique v1 (décision normative) | Réexaminer post-PMF Android |

### Encadré conformité : AdMob récompensé × téléchargement YouTube (risque politique majeur)

C'est le point le plus important de cette recherche pour la survie du produit sur Play Store.

1. **YouTube ToS** (MEDIUM) : interdiction d'accéder au contenu hors lecteur autorisé et de télécharger/copier/sauvegarder sans bouton de téléchargement explicite (hors Premium). Le téléchargement via NewPipeExtractor est une violation contractuelle de la ToS.
2. **Google Play — Device and Network Abuse** (page officielle vérifiée) : interdit les apps qui « *access or use a service or API in a manner that violates its terms of service* ». C'est le fondement des vagues de retrait des apps de téléchargement YouTube ; les YouTube API Developer Policies interdisent explicitement d'activer le téléchargement. **Une app Play qui télécharge l'audio/vidéo YouTube on-device porte donc un risque réel de retrait.**
3. **AdMob** (MEDIUM) : les rewarded ads doivent être opt-in avec callback de récompense (la spec est conforme : bottom sheet refermable, récompense sur `onUserEarnedReward` uniquement) ; récompenses **non monétaires** autorisées (tokens = OK) ; Google recommande des intervalles entre pubs récompensées (sur-servir = suspension) ; la fraude au trafic entraîne suspension/clawback. Surtout : **la violation de policy au niveau app (retrait Play) se propage au compte AdMob** — le modèle de revenus entier dépend de la conformité de la fonctionnalité d'ingestion.
4. **Positionnement des concurrents** : Opus Clip, Klap, 2short.ai, Vizard, quso.ai font tous du « paste a YouTube link » **mais sont des services web** — hors de portée de la policy Play. C'est pourquoi le modèle économique « téléchargement YouTube + pub » n'existe pas chez eux : ils n'en ont pas besoin et n'encourent pas ce risque. La spec de ShortifyLocal AI est la seule à empiler les deux.

**Implication roadmap (sans élargir le v1)** : ce risque doit être porté tel quel dans les PITFALLS et la définition des phases ; le candidat v2 « ingestion par upload du fichier vidéo (au lieu du lien YouTube) » est le levier de dé-risquage le plus direct et devrait être gardé chaud dès la v1 architecture (une interface `VideoSourceRepository` déjà générique).

---

## Dépendances entre fonctionnalités

```
[Coller un lien YouTube + validation Regex]
    └──exige──> [Smart download audio (Fonction A) + conversion 16 kHz]
                       └──exige──> [Transcription Whisper mot à mot (Fonction B)]
                                          └──exige──> [Whisper Manager (modèles + SHA-256)]
                       └──alimente──> [Découpage 15-60 s + score (Fonction C)]
                                          ├──enhance──> [BYOK Gemini (C.2, fallback C.1)]
                                          └──exige──> [Score de viralité + tri UI (Détails)]
[Download vidéo partiel Range Requests] ──exige──> [Auto-reframe ML Kit (Fonction D)]
[Transcription] ──exige──> [Sous-titres karaoké .ass (Fonction E)]
[Reframe + .ass + loudnorm] ──exige──> [Rendu FFmpeg x264 (Fonction F)]
[Rendu] ──gated-by──> [Économie de tokens : −5 analyse, −3 téléchargement, isUnlocked]
[−3 tokens / blocage] ──exige──> [AdMob récompensé + Pass 24h + UMP consent]
[Export galerie] ──exige──> [isUnlocked] ──enhance──> [ZIP projet complet]
[Historique + recherche + tags] ──exige──> [Schéma Room 4 entités + titleSearch Unicode]
[RTL arabe + 7 langues] ──exige──> [i18n structurelle dès le 1er composant]
[Parrainage +20] ──exige──> [Bouton partage + deep link shortify://invite]
```

### Notes de dépendance

- **Sous-titres karaoké exigent la transcription mot à mot** : les balises `{\K}` du `.ass` sont dérivées des timestamps par mot de whisper.cpp (`token_timestamps=true`). Sans transcription word-level, pas de karaoké — c'est la brique la plus structurante du pipeline.
- **Le score de viralité sémantique exige la transcription** ; l'heuristique pics audio seule (C.1 partie 1) peut fonctionner sans, mais la spec croise les deux — la transcription est donc un prérequis du découpage.
- **L'export galerie est gated par l'économie de tokens** (`isUnlocked`) qui exige AdMob (recharge) + HMAC (intégrité) : la chaîne monétisation est dans le chemin critique du rendu, pas une surcouche.
- **Le RTL exige l'i18n structurelle d'emblée** : rattraper le RTL après coup est la cause n°1 des UI arabes cassées ; les composants du design system doivent naître avec `Start/End`.
- **BYOK Gemini est en conflit doux avec la promesse privacy** : l'analyse sort de l'appareil en mode clé — la mention discrète (Partie 5 §6.2) doit exister dès la v1 pour la cohérence marketing.
- **AdMob exige le consentement UMP en production** (EEA/UK) avant `MobileAds.initialize()` — ordre d'initialisation non négociable.

---

## Définition du MVP

### Lancer avec (v1) — le périmètre normatif du cahier des charges (figé)

- [ ] Ingestion lien YouTube (regex + métadonnées NewPipe + smart download audio) — cœur du workflow
- [ ] Pipeline A→F complet en WorkManager foreground (machine à états 7 états + progression)
- [ ] Transcription Whisper on-device mot à mot (Tiny/Base/Small + Whisper Manager + SHA-256)
- [ ] Découpage 15–60 s hybride : heuristique locale (pics + motifs multilingues) + BYOK Gemini structured outputs
- [ ] Auto-reframe 9:16 (ML Kit bundled, lissage 1 s) + sous-titres karaoké .ass (Montserrat, jaune/blanc, contour 3-4 px)
- [ ] Rendu final x264 + loudnorm + export MediaStore + ZIP projet optionnel
- [ ] Économie de tokens locale (+50/+70, +10 pub, +5 partage, +20 parrainage ; −5/−3 ; Pass 24h) signée HMAC
- [ ] AdMob récompensé (IDs test dev, UMP prod) + deep link parrainage
- [ ] Room 4 entités, historique, recherche Unicode, tags, GC 7 jours
- [ ] UI Compose « Soft-Clean » 4 écrans, clair/sombre, 7 langues + RTL arabe complet

### Ajouter après validation (v1.x candidats — ne PAS planifier en v1)

- [ ] **Ajustement léger des bornes de clip (nudge début/fin)** — déclencheur : retours utilisateurs « le clip coupe trop tôt » (faiblesse connue des auto-clippers, y compris CapCut)
- [ ] **Ingestion par fichier/upload local (dé-risquage Play policy)** — déclencheur : premier signalement de retrait ou refus de review Play ; l'architecture `VideoSourceRepository` doit rester générique dès la v1
- [ ] **Presets de style de sous-titres (2-3 styles)** — déclencheur : demande de personnalisation (les concurrents vendent du custom style)
- [ ] **Suppression des mots de remplissage** (euh, donc…) — déclencheur : feedback qualité transcription
- [ ] **Re-génération d'un clip individuel** sans ré-analyser tout le projet — déclencheur : coûts de rendu jugés trop longs

### Considérations futures (v2+)

- [ ] **Traduction de sous-titres** — la demande concurrentielle est forte (Opus Clip, Vizard 100+ langues, Wisecut 13) ; s'appuie sur la transcription existante
- [ ] **Ingestion multi-plateformes** (Vimeo, Twitch, Drive — norme chez Opus Clip/quso.ai)
- [ ] **Doublage IA** — différenciateur Captions/Dubs ; lourd, nécessiterait décision cloud vs on-device
- [ ] **iOS** — exclu stratégiquement en v1 ; réévaluer après PMF Android
- [ ] **Publication directe** — seulement si les APIs tierces sont homologuées et le risque Play résolu (upload-first)

---

## Matrice de priorisation

| Fonctionnalité | Valeur utilisateur | Coût d'implémentation | Priorité |
|---------|------------|---------------------|----------|
| Pipeline A→F (download→transcription→découpage→reframe→sous-titres→rendu) | HIGH | HIGH | P1 |
| Économie de tokens + AdMob récompensé + HMAC | HIGH (modèle économique entier) | MEDIUM | P1 |
| Sous-titres karaoké offline | HIGH | HIGH | P1 |
| Auto-reframe avec suivi visage | HIGH | HIGH | P1 |
| Score de viralité + tri + raison | HIGH | MEDIUM | P1 |
| 7 langues + RTL arabe intégral | HIGH (marchés différenciateurs) | MEDIUM | P1 |
| Historique + recherche Unicode + tags | MEDIUM | MEDIUM | P1 |
| ZIP projet complet | MEDIUM | LOW | P1 |
| Whisper Manager (modèles téléchargeables) | HIGH (prérequis pipeline) | MEDIUM | P1 |
| Ajustement bornes de clip | MEDIUM | MEDIUM | P2 (v1.x) |
| Upload fichier local (dé-risquage Play) | HIGH (interne : survie produit) | MEDIUM | P2 (v1.x) |
| Presets sous-titres | MEDIUM | LOW | P2 (v1.x) |
| Traduction de sous-titres | HIGH | HIGH | P3 (v2) |
| Publication directe plateformes | MEDIUM | HIGH | P3 (v2) |
| Doublage IA / B-roll | MEDIUM | HIGH | P3 (v2) |

**Clé :** P1 = obligatoire au lancement (périmètre normatif) · P2 = à ajouter après validation · P3 = considération future

---

## Analyse concurrentielle

| Fonctionnalité | Opus Clip (leader cloud) | Klap | CapCut (mobile gratuit) | Apps mobile (Captions/Dubs) | **ShortifyLocal AI v1 (spec)** |
|---------|--------------|--------------|--------------|--------------|--------------|
| Ingestion | Lien YouTube + 12 sources (Drive, Zoom, Twitch…) | Lien YouTube (cœur UX) | Fichier local + cloud ByteDance | Fichier local (tournage) | Lien YouTube (on-device) |
| Découpage IA | ClipAnything + prompts NL | 10+ clips / 30 s | Long-to-shorts (visage/voix/engagement) | Édition assistée | Heuristique locale + BYOK Gemini |
| Score de viralité | Oui (hook/pacing/topic) — le + cité | Oui (par clip) | Non (non exposé) | Non | Oui, 0–100 + raison + tri (conforme à la convention) |
| Auto-reframe 9:16 | ReframeAnything (object tracking) | Oui (sujets principaux) | Oui | Oui | ML Kit visage + lissage 1 s |
| Sous-titres | Dynamiques, ~97 %, custom | Custom styles | Auto-captions | Signature produit | Karaoké .ass offline (un seul style v1) |
| Exécution | 100 % cloud | 100 % cloud | Cloud hybride | Cloud/ hybride | **100 % on-device (unique)** |
| Compte requis | Oui | Oui | Compte optionnel | Oui | **Non (unique)** |
| Prix | Freemium 60 min/mois + abonnement | Abonnement | Gratuit + Pro | Abonnement hebdo | **Gratuit + pub récompensée (unique)** |
| Langues captions | 25–30+ (AR inclus, RTL captions défaillant — demande ouverte) | Multi | Multi | Multi | 7 dont **AR RTL complet, JA, KO** |
| Publishing direct | 1 clic multi-plateformes | Non (export) | Partiel (ByteDance) | Non | Non (anti-feature v1) |
| B-roll IA / musique | Oui / Oui | Non / Non | Oui (bibliothèque) | Oui | Non (anti-feature v1) |
| Distribution | Web (hors policy Play) | Web + API | Play Store | Play Store | **Play Store (risque policy à traiter)** |
| Données/quota | Minutes GPU facturées | Minutes facturées | Quotas cloud | Abonnement | Coût marginal zéro (CPU utilisateur) |

**Lecture stratégique** : ShortifyLocal AI ne peut pas gagner sur le terrain des concurrents (qualité de découpage sémantique, publishing, B-roll — ils ont des GPU et des équipes ML). Le terrain gagnable est exactement celui que la spec occupe : **privé (on-device), gratuit sans compte, multilingue RTL, sans friction** — avec pour seule faille structurelle le risque Play/ToS de l'ingestion YouTube, que les concurrents web n'ont pas car ils ne distribuent pas via Play.

---

## Sources

**Produits concurrents (pages officielles + reviews)**
- [opus.pro](https://www.opus.pro/) — fonctionnalités officielles vérifiées (ClipAnything, ReframeAnything, templates, publishing, langues, freemium) — MEDIUM
- [OpusClip API multi-language captions](https://www.opus.pro/blog/multi-language-captions-api), [help.opus.pro — langues vidéo](https://help.opus.pro/docs/article/video-languages-supported) — MEDIUM
- [Canny OpusClip — Hebrew and RTL usage](https://opusclip.canny.io/feature-requests/p/hebrew-and-rtl-usage), [Language Arabic](https://opusclip.canny.io/feature-requests/p/language-arabic) — MEDIUM (preuve du différenciateur RTL)
- [vizard.ai](https://vizard.ai/) — MEDIUM · [klap.app](https://klap.app/) + [API](https://klap.app/api) — MEDIUM
- [CapCut — AI long video to short](https://www.capcut.com/tools/ai-long-video-to-short-video), [guide Hollyland](https://store.hollyland.com/blogs/creator-hub/convert-long-videos-to-shorts-in-capcut) — MEDIUM
- [captions.ai](https://captions.ai/), [Dubs sur l'App Store](https://apps.apple.com/us/app/captions-ai-for-videos-dubs/id6446585789) — LOW (pricing non confirmé)
- [quso.ai](https://quso.ai/) (ex-vidyo.ai), [2short.ai](https://2short.ai/), [wisecut.ai](https://wisecut.ai/), [Eklipse vs Spikes Studio](https://eklipse.gg/compare/eklipse-vs-spikes-studio/) — MEDIUM

**Conformité (ToS / Play / AdMob)**
- [YouTube Terms of Service](https://www.youtube.com/static?template=terms) + [TLDR Legal](https://www.tldrlegal.com/license/youtube-terms-of-service) — interdiction de téléchargement hors moyen autorisé — MEDIUM
- [Play — Developer Content Policy](https://play.google.com/about/developer-content-policy/) et [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379) — clause « *access or use a service in a manner that violates its terms of service* » (page officielle vérifiée) — MEDIUM/HIGH
- [YouTube API Developer Policies](https://developers.google.com/youtube/terms/developer-policies-guide) — interdiction d'activer le téléchargement — MEDIUM
- [AdMob Policies](https://support.google.com/admob/answer/6128543), [Invalid traffic/suspensions](https://support.google.com/admob/answer/6213019), [Blog Google AdMob](https://blog.google/products/admob/understanding-account-suspensions-due-invalid-traffic/), [fréquence rewarded ads](https://groups.google.com/g/google-admob-ads-sdk/c/J8F3v7suxfQ) — MEDIUM

**Faisabilité on-device (positionnement différenciant)**
- [whisper-mobile (Android, whisper.cpp)](https://github.com/hrushik98/whisper-mobile), [Whisper sur F-Droid](https://f-droid.org/packages/org.woheller69.whisper/), [Handy](https://www.reddit.com/r/LocalLLaMA/comments/1ldvosh/handy_a_simple_opensource_offline_speechtotext/), [portage JNI Kotlin (LocalMind)](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc) — MEDIUM : preuve technique, absence de concurrent commercial on-device

---
*Recherche fonctionnalités pour : ShortifyLocal AI (clipping IA on-device)*
*Date de recherche : 2026-09-04*
