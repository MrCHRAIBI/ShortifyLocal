# CAHIER DES CHARGES — ShortifyLocal AI
## Partie 1 : Description, Fonctionnalités & Architecture Technique
### Version Kotlin Android Natif — Clean Architecture + MVVM — Septembre 2026

---

## 0. Règles de lecture pour l'Agent de Codage

1. Ce document est **normatif et auto-suffisant**. Toute extrapolation est interdite : si un comportement n'est pas décrit, choisir l'option la plus simple et la plus locale.
2. **Aucun code iOS** ne doit être produit : le projet est **Android-only** (décision stratégique ; la version d'origine Flutter visait Android + iOS, cette version abandonne iOS).
3. Philosophie **local-first** : tous les traitements lourds (audio, vidéo, IA de transcription, suivi de visage, analyse) s'exécutent **on-device**, sans serveur cloud.
4. Modèle **hybride BYOK** : l'application fonctionne 100% gratuitement sans clé API ; l'utilisateur peut fournir sa propre clé Gemini pour débloquer l'analyse sémantique avancée.
5. Licences autorisées : **MIT / Apache 2.0 / BSD / LGPL** (LGPL explicitement accepté pour NewPipeExtractor et ffmpeg-kit).
6. Approche **"AI-Agent Friendly"** : préférer des bibliothèques standard, stables et bien documentées ; aucune intégration native artisanale hors celles spécifiées (JNI whisper.cpp).
7. Renvois normatifs : UI/UX → Partie 2 ; Monétisation/Tokens → Partie 3 ; Base de données → Partie 4 ; Sécurité → Partie 5.

---

## 1. Vision du Projet

ShortifyLocal AI est un **studio de montage automatisé par IA dans la poche de l'utilisateur** : l'application transforme une vidéo YouTube longue en plusieurs clips verticaux (Shorts/Reels/TikTok) de 15 à 60 secondes, avec sous-titres karaoké dynamiques, recadrage automatique autour du visage et normalisation audio — le tout généré localement sur le smartphone.

---

## 2. Contraintes Critiques & Toolchain

### 2.1 Stack imposé
| Élément | Choix |
|---|---|
| Langage | **Kotlin** (compilateur K2) |
| UI | **Jetpack Compose** |
| Architecture | **Clean Architecture + MVVM** |
| Injection de dépendances | **Hilt** |
| Navigation | Jetpack Navigation Compose |
| Asynchrone | Coroutines Kotlin + Flow |
| Tâches longues | **WorkManager** (foreground `dataProcessing`) |

### 2.2 Versions épinglées (état stable : septembre 2026)
| Outil | Version |
|---|---|
| Kotlin | **2.3.0** |
| AGP | **9.1.0** |
| Gradle (wrapper) | **9.4** |
| JDK toolchain | **17** |
| compileSdk / targetSdk | **36** (Android 16) |
| minSdk | **33** (Android 13) |
| Compose BOM | **2026.06.00** (ou dernier BOM 2026) |
| Hilt | 2.56+ |
| Room | 2.8+ (KSP) |
| Navigation Compose | 2.9+ |
| WorkManager | 2.10+ |
| security-crypto | 1.1.0-alpha06+ |
| play-services-ads | 24.x |
| ML Kit Face Detection | `com.google.mlkit:face-detection` (**modèle bundled**, offline) |
| NewPipeExtractor | Dernière release stable via JitPack (`com.github.TeamNewPipe:NewPipeExtractor`) |
| ffmpeg-kit-android | `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (libass + libx264 + loudnorm inclus) |

- **Catalogue unique** : toutes les dépendances dans `gradle/libs.versions.toml`.
- **Clause de fraîcheur** : à l'échafaudage, vérifier kotlinlang.org / developer.android.com / gradle.org ; si une version stable plus récente existe, l'adopter et le noter en commentaire.
- **Contrainte Android 16** : les bibliothèques natives (whisper.cpp, FFmpeg `.so`) doivent être **alignées 16 KB page size** (ELF alignment).

### 2.3 Permissions & Manifeste
- `INTERNET`, `ACCESS_NETWORK_STATE`
- `POST_NOTIFICATIONS` (runtime ; notifications de progression du pipeline)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_PROCESSING`
- **Aucune permission de stockage** (export galerie via **MediaStore**, minSdk 33)
- `android:supportsRtl="true"`
- Intent-filter deep link : `shortify://invite` (parrainage, voir Partie 3)

---

## 3. Architecture Logicielle — Clean Architecture + MVVM

### 3.1 Couches et responsabilités
| Couche | Contenu | Règle stricte |
|---|---|---|
| `presentation` | Composables, ViewModels, UiState, design system, navigation, thème | **Zéro logique métier** : déclaratif uniquement |
| `domain` | Modèles purs, **interfaces** de repositories, use cases | **Kotlin pur** : zéro import Android, zéro dépendance externe |
| `data` | Implémentations des interfaces, Room, stockage sécurisé, fichiers, NewPipe, Gemini, wrappers natifs | Seule couche autorisée à toucher aux sources de données |
| `worker` | Workers WorkManager (pipeline A→F) | Appellent uniquement des use cases du domain |
| `di` | Modules Hilt (liaison interfaces → implémentations, singletons) | — |
| `util` | Regex YouTube, helpers locale, extensions | — |

### 3.2 Arborescence normative
```
app/src/main/java/com/shortifylocal/ai/
├── presentation/
│   ├── ui/home/            # HomeScreen, HomeViewModel, HomeUiState
│   ├── ui/detail/          # DetailScreen, DetailViewModel, ShortPlayerSheet
│   ├── ui/history/
│   ├── ui/settings/
│   ├── components/         # Design system (voir Partie 2)
│   ├── navigation/         # NavGraph : 3 onglets + écran Détails
│   └── theme/              # Color.kt, Type.kt, Shape.kt (clair/sombre)
├── domain/
│   ├── model/              # VideoProject, ProjectTag, GeneratedShort, WordTimestamp, ClipSegment, TokenBalance
│   ├── repository/         # INTERFACES : YouTubeRepository, VideoRepository, TokenRepository,
│   │                       # GeminiRepository, TranscriptionRepository, RenderRepository, SecureStorageRepository
│   └── usecase/            # AnalyzeVideoUseCase, DownloadShortUseCase, UpdateTokensUseCase,
│                           # ShareRewardUseCase, SearchProjectsUseCase, TagUseCases...
├── data/
│   ├── repository/         # Implémentations des interfaces domain
│   ├── local/room/         # AppDatabase, DAOs, Entities, VideoTagCrossRef, Converters (Partie 4)
│   ├── local/secure/       # SecureStorageImpl (Partie 5)
│   ├── local/files/        # CacheManager (GC 7 jours), AssSubtitleGenerator, ZipExporter
│   ├── remote/youtube/     # NewPipeExtractorWrapper (métadonnées, flux audio, range requests vidéo)
│   ├── remote/gemini/      # GeminiClient (structured outputs)
│   └── native/
│       ├── ffmpeg/         # FFmpegWrapper (conversion, volumedetect, crop, subtitles, loudnorm)
│       ├── whisper/        # Bindings JNI/CMake whisper.cpp (token_timestamps)
│       └── mlkit/          # FaceTrackerWrapper (bounding boxes + lissage)
├── worker/                 # PipelineWorker, TranscriptionWorker, RenderWorker
├── di/                     # Modules Hilt
└── util/                   # YouTubeRegex, LocaleHelper, Extensions
```

### 3.3 Règles MVVM strictes
1. Chaque écran = 1 `@HiltViewModel` exposant `StateFlow<XUiState>` (data class immutable / sealed class).
2. Événements one-shot (SnackBar, navigation, dialogues) via `SharedFlow<UiEvent>`.
3. Flux unidirectionnel : `Composable → ViewModel → UseCase (domain) → Repository (data)`.
4. Annulation propagée : l'annulation d'un job Kotlin doit stopper les commandes FFmpeg en cours.

### 3.4 Pipeline de traitement (A→F) en arrière-plan
- Orchestration dans **`PipelineWorker`** (WorkManager + Hilt, `setForeground` type `dataProcessing` ; jamais sur le main thread).
- Progression exposée via `WorkInfo.progress` (Data : état, étape courante, pourcentage) + bus `StateFlow` ; les ViewModels collectent.
- Machine à états normative : `DownloadingAudio → Transcribing → Segmenting → Reframing → Subtitling → Rendering → Done | Error`.

---

## 4. Spécification Détaillée des Fonctions Techniques (A → F)

### 🚀 Fonction A — Interception & "Smart Download" de l'Audio YouTube
**Rôle** : valider l'URL saisie, extraire uniquement le flux audio (économie de bande passante/stockage), le télécharger, puis le convertir au format exigé par Whisper.

**Algorithme** :
1. **Validation Regex** (standard + raccourci + shorts) :
   `^(https?://)?(www\.|m\.)?(youtube\.com/(watch\?v=|shorts/|embed/)|youtu\.be/)[A-Za-z0-9_-]{11}`
2. **Métadonnées** via NewPipeExtractor : ID vidéo, titre, auteur, durée, URL miniature haute résolution (téléchargée et stockée localement).
3. **Smart Audio Download** : flux audio seul au plus faible débit suffisant pour la voix (AAC/M4A ~128 kbps) ; téléchargement OkHttp. (~10-20 Mo pour 15 min.)
4. **Conversion 16 kHz Mono** (FFmpeg, thread dédié) :
   `-i input_audio.m4a -ar 16000 -ac 1 -c:a pcm_s16le output_audio_16k.wav`

**Déclenchement** : clic sur le bouton de validation de l'Écran 1 (règles de débit de tokens → Partie 3).

### 🎙️ Fonction B — Transcription Locale Mot-à-Mot (Whisper On-Device)
**Rôle** : produire une transcription complète où **chaque mot** porte son horodatage début/fin en millisecondes.

**Algorithme** :
1. **Vérification du modèle** actif (Tiny ~39 Mo / Base ~57 Mo / Small ~184 Mo ; **défaut proposé : Base**). Si aucun modèle présent → redirection forcée vers Paramètres / Whisper Manager.
2. Exécution dans `TranscriptionWorker` (jamais sur le thread UI).
3. **whisper.cpp** compilé via **CMake/NDK**, bindings **JNI** Kotlin, paramètre `token_timestamps = true`.
4. **Sortie JSON interne** :
```json
[
  {"word": "Bonjour", "start": 120, "end": 450},
  {"word": "à", "start": 460, "end": 580},
  {"word": "tous", "start": 590, "end": 900}
]
```

### 🧠 Fonction C — Découpage en Segments 15-60 s + Score de Viralité (Hybride)
**CAS C.1 — SANS clé API (heuristique locale)** :
1. Passe FFmpeg rapide `volumedetect` / `ebur128` → timestamps des **pics d'amplitude** (rires, cris, hausses de ton).
2. Filtre sémantique Kotlin sur la transcription : motifs d'accroche multilingues configurables ("Pourquoi", "Comment", "Voici la méthode", mots d'action forts + équivalents EN/ES/IT/AR/JA/KO).
3. **Croisement** : un segment est généré de `t−2 s` (avant le pic) à `t+25 s` si un pic sonore fort coïncide avec une phrase cohérente ; bornes strictes **15 s ≤ segment ≤ 60 s**.
4. **Score** = pondération locale (amplitude du pic × présence de mots d'action).

**CAS C.2 — AVEC clé Gemini (BYOK)** :
1. Prompt système contenant la transcription complète avec timestamps par phrase.
2. Exigence **Structured Outputs** (schéma JSON strict) :
```json
{
  "clips": [
    {
      "title": "L'importance du Deep Work",
      "start_time": 124.5,
      "end_time": 178.2,
      "viral_score": 92,
      "reason": "Sujet très engageant, démarre par une question rhétorique forte.",
      "type": "Key Points"
    }
  ]
}
```

### 🎥 Fonction D — Suivi de Visage & Auto-Reframe (16:9 → 9:16)
**Algorithme** :
1. FFmpeg extrait **1 frame toutes les 250 ms** (4 fps) sur le segment choisi.
2. **ML Kit Face Detection** (modèle bundled, offline) → bounding box du **plus grand visage** → coordonnée X du centre.
3. **Lissage** : moyenne mobile sur fenêtre glissante de **1 s** (4 échantillons) + interpolation linéaire entre keyframes (anti-mouvements brusques).
4. **Génération du crop** (cible 1080×1920) :
   - `h = hauteur source` ; `w = min(largeur source, arrondi(h × 9/16))`
   - `x = clamp(arrondi(centerX − w/2), 0, largeur source − w)` ; `y = 0`

### 🎨 Fonction E — Sous-titres Karaoké Dynamiques (.ass)
**Algorithme** :
1. Génération Kotlin pur d'un fichier **Advanced SubStation Alpha** (`.ass`) ; balises de timing `{\K[durée]}` par mot (conversion ms → centièmes de seconde).
2. **En-tête de style normatif** :
   - `Fontname` : Montserrat-ExtraBold (police bundlée) ou Impact
   - `Fontsize` : adaptée à la résolution verticale
   - `PrimaryColor` : `&H00FFFFFF&` (blanc)
   - `SecondaryColor` (couleur active karaoké) : `&H0000FFFF&` (jaune)
   - `Outline` : contour noir épais **3-4 px**
3. **Hardsubbing** au rendu via le filtre `-vf "subtitles=temp_subs.ass"` (libass inclus dans ffmpeg-kit-full-gpl).

### 🛠️ Fonction F — Rendu Final, Normalisation Audio & Export
**Algorithme** :
1. **Smart Video Download partiel** : URL du flux vidéo via NewPipeExtractor + **Range Requests HTTP** (OkHttp) limités à l'intervalle du Short (ex: 124 s → 178 s).
2. **Commande FFmpeg unique combinée** :
```
-ss {start} -to {end} -i video_partiel.mp4
-vf "crop={w}:{h}:{x}:{y},scale=1080:1920,subtitles=temp_subs.ass"
-af "loudnorm=I=-16:TP=-1.5:LRA=11"
-c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k short_final.mp4
```
3. Écriture dans le cache applicatif ; copie vers la galerie via **MediaStore** uniquement si l'achat du Short est validé (règles de tokens → Partie 3).
4. Si l'option "Export projet complet" est active (Paramètres → Partie 2) : génération d'un **ZIP** (MP4 + WAV + ASS/SRT) via `java.util.zip`.

---

## 5. Design & Internationalisation — Aperçu (détail normatif en Partie 2)

- **Design System "Soft-Clean / Soft UI"** : cartes blanches flottantes sur fond gris clair, **UNE seule ombre douce verticale** par surface. **INTERDICTION formelle du neumorphisme** (pas d'ombres doubles clair+sombre, pas d'éléments de la même couleur que le fond).
- Thèmes clair/sombre dynamiques ; accent noir + **orange terracotta** pour les états actifs.
- **7 langues** : FR, EN, ES, IT, AR, JA, KO ; **RTL complet pour l'arabe** ; polices par écriture (Inter / Cairo / Noto Sans JP / Noto Sans KR) bundlées offline.

---

## 6. Renvois vers les autres parties

| Sujet | Partie |
|---|---|
| Écrans, composants, tokens de design, i18n complète | **Partie 2 — UI/UX** |
| Économie de tokens, AdMob, Pass 24h, parrainage | **Partie 3 — Monétisation** |
| Schéma Room, requêtes, garbage collector de cache | **Partie 4 — Base de données** |
| Keystore, EncryptedSharedPreferences, HMAC anti-triche | **Partie 5 — Sécurité** |

---

## 7. Checklist Partie 1 pour l'Agent

- [ ] Versions vérifiées contre les registres officiels (§2.2) ; clause de fraîcheur appliquée
- [ ] Aucun code iOS résiduel ; aucune permission de stockage demandée
- [ ] Couches `domain` sans import Android ; logique métier absente des Composables
- [ ] Pipeline A→F exclusivement dans WorkManager (foreground `dataProcessing`)
- [ ] Bibliothèques natives alignées 16 KB page size
- [ ] Règles de tokens non codées en dur ici : renvois stricts aux Parties 3 et 5