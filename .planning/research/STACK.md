# Recherche Stack

**Domaine :** Application Android native on-device AI (découpage vidéo YouTube → Shorts verticaux, 100 % local)
**Recherché :** 2026-09-04
**Confiance :** HIGH (chaque version vérifiée contre son registre officiel le 2026-09-04 ; exceptions MEDIUM signalées ligne par ligne)

> **Méthodologie & hiérarchie des sources** : toutes les versions ci-dessous proviennent des registres de première main — `dl.google.com/android/maven2` (Google Maven), `repo1.maven.org` (Maven Central), `services.gradle.org`, `kotlinlang.org`, `api.github.com` (releases officielles) et `developer.android.com`. La seam `classify-confidence` a retourné un LOW générique (sans contexte package) ; la confiance par dépendance est donc attribuée selon l'autorité de la source citée, documentée dans chaque ligne. Aucune version ne repose sur des données d'entraînement.

---

## ⚠️ Alertes critiques (à traiter AVANT l'échafaudage)

### Alerte 1 — `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` est MORT (coordinates introuvables)
- Arthenica a annoncé la **retraite de ffmpeg-kit le 2025-01-06** ; les binaires précompilés ont été **supprimés de Maven Central, CocoaPods et npm le 2025-04-01**.
- Vérification directe du 2026-09-04 : `https://repo1.maven.org/maven2/com/arthenica/ffmpeg-kit-full-gpl/maven-metadata.xml` → **HTTP 404**. Un build épinglé sur ces coordinates **échoue aujourd'hui**.
- **Substitution fonctionnelle validée** (drop-in, même API `com.arthenica.ffmpegkit.*`) : le fork communautaire **`dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl`**, publie sur Maven Central les versions `6.0.3`, `7.1.6`, `8.1.7` (metadata à jour 2026-07-12). La variante `full-gpl` contient bien **libass + libx264** (+ `loudnorm`, filtre FFmpeg natif présent dans toutes les variantes) — les 3 exigences de la Fonction E/F du cahier des charges. La variante `8.1.7` **impose l'alignement 16 KB** (`-Wl,-z,max-page-size=16384`, la CI du fork échoue si un `.so` n'est pas aligné).
- **Clause de fraîcheur appliquée** : recommander `8.1.7` (ligne LTS FFmpeg n8.1.2) ; `6.0.3` = substitution la plus proche de l'épinglage « 6.0-2 » d'origine.
- Confiance : HIGH pour l'obtenabilité (metadata Maven Central lue directement) ; **MEDIUM** pour deux détails du README du fork à re-vérifier à l'échafaudage : (a) publication **arm64-v8a uniquement** (impacte les émulateurs x86_64 — prévoir émulateur ARM ou device physique), (b) minSdk 24 / targetSdk 35 du fork (sans impact : notre app est minSdk 33).

### Alerte 2 — Conflit de licences : 2 dépendances épinglées sont GPL-3.0, pas LGPL
Le cahier des charges (Partie 1 §0.5) liste « MIT / Apache 2.0 / BSD / LGPL » et affirme LGPL pour NewPipeExtractor et ffmpeg-kit. **Vérification directe le 2026-09-04 :**
- **NewPipeExtractor est sous GPL-3.0** (champ `license` de `api.github.com/repos/TeamNewPipe/NewPipeExtractor` : « GNU General Public License v3.0 »). L'affirmation « LGPL » du cahier des charges est **factuellement fausse**.
- **ffmpeg-kit-full-gpl est sous GPL-3.0** (libx264/x265 sont GPL ; le README du fork le confirme : « if your app links it, your whole app must be GPL-compatible »). C'était déjà vrai chez Arthenica — le problème préexiste à la retraite.
- **Conséquence juridique** : lier ces deux bibliothèques rend l'application distribuée **compatible GPL-3.0** (travail dérivé). Ce n'est pas bloquant pour une app gratuite sans composant serveur, mais c'est une **décision de distribution explicite** que le roadmap doit faire trancher au propriétaire : (a) amender la liste blanche du cahier des charges pour accepter GPL-3.0 pour ces deux composants (option recommandée — aucun équivalent fonctionnel crédible hors GPL pour l'extraction YouTube + encodage x264), ou (b) rester strict sur la liste blanche et perdre x264 (recadrage/encodage via `full` LGPL sans x264 → pas de libx264 exigé par la Fonction F) et l'extracteur YouTube (tous les extracteurs crédibles sont GPL). **Confiance : HIGH sur les licences ; l'interprétation juridique applicative est MEDIUM (pas un avis légal).**

### Alerte 3 — `security-crypto` : 1.1.0 stable existe, mais toute l'API est dépréciée
- Google Maven : `androidx.security:security-crypto` a atteint la version **stable 1.1.0 le 2025-07-30** (après alpha07/beta01). L'épinglage « 1.1.0-alpha06+ » du cahier des charges est donc satisfait **et dépassable** → adopter `1.1.0` stable.
- **Mais** : l'API entière (dont `EncryptedSharedPreferences`) est **officiellement dépréciée depuis 1.1.0-alpha07** ; Google oriente vers DataStore + Tink ou Keystore direct. La bibliothèque est en mode maintenance : aucun roadmap, suppression possible un jour.
- **Atténuation conforme au cahier des charges** : l'accès passe exclusivement par l'interface `SecureStorageRepository` (Partie 5) → l'implémentation est interchangeable sans toucher au domaine. Recommandation : implémenter avec `security-crypto:1.1.0` (conforme au spec), isoler, et garder la doc d'une migration Tink/Keystore prête. Confiance : HIGH.

---

## Stack recommandé

### Technologies core

| Technologie | Version | Rôle | Pourquoi / statut vérifié |
|---|---|---|---|
| Kotlin (K2) | **2.4.10** (spec : 2.3.0) | Langage unique | 2.3.0 existe (2025-12-16) mais n'est pas la dernière stable : **2.4.10 (2026-07-14)** est le dernier bug-fix de la release linguistique 2.4.0 (2026-06-03) — clause de fraîcheur appliquée. 2.4.20 prévu sept. 2026. Plan B conservateur : `2.3.21` si un plugin tiers traîne sur 2.4. Source : kotlinlang.org/docs/releases.html. Confiance HIGH |
| JDK toolchain | **17** (confirmé) | Compilation + daemon Gradle | AGP 9.4.0 exige **JDK ≥ 17** ; Gradle 9.x tourne sur 17. L'épinglage JDK 17 du spec reste valide en sept. 2026. Confiance HIGH |
| AGP | **9.4.0** (spec : 9.1.0) | Build Android | Dernière stable du Google Maven (9.5.0 = alphas uniquement). 9.1.0/9.1.1 existent mais clause de fraîcheur → 9.4.0. Requiert **Gradle ≥ 9.6.0**, build-tools 36, NDK par défaut 28.2, API max 37. Source : group-index `com.android.tools.build` + developer.android.com. Confiance HIGH |
| Gradle (wrapper) | **9.7.1** (spec : 9.4) | Orchestration build | Dernière stable (2026-08-19, checksum officiel vérifié). Compatible AGP 9.4.0 (≥ 9.6.0 exigé). Source : services.gradle.org/versions/current. Confiance HIGH |
| compileSdk / targetSdk | **36** (Android 16) | Niveau d'API cible | Confirmé supporté par AGP 9.4.0 (API max 37) ; requis par Compose BOM 2026.08 ; satisfait l'exigence Play de ciblage API 36 (échéance 31 août 2026). Confiance HIGH |
| minSdk | **33** (Android 13) | Plancher de compat | Compatible toutes les dépendances vérifiées (ads v24+ exige API 23 ; fork ffmpeg-kit minSdk 24 ; ML Kit minSdk 21). Nécessaire pour `AppCompatDelegate.setApplicationLocales` natif (Partie 2). Confiance HIGH |
| Compose BOM | **2026.08.00** (spec : 2026.06.00) | UI déclarative Material 3 | Le BOM épinglé existe (`2026.06.00`, `2026.06.01`) mais le dernier stable est **2026.08.00** — clause de fraîcheur. Source : maven-metadata `androidx.compose:compose-bom`. Confiance HIGH |
| Hilt | **2.60.1** (spec : 2.56+) | DI + injection des Workers | Dernière stable Maven Central (`com.google.dagger:hilt-android`). Satisfait largement « 2.56+ ». Source : repo1.maven.org. Confiance HIGH |
| Room + KSP | **2.8.4** / KSP **2.3.11** (spec : Room 2.8+) | Persistance, `exportSchema=true`, Flow | Dernière stable Room (Google Maven). KSP est passé au versionnage standalone : `2.3.11` (2026-08-03) ; **2.3.10 corrige explicitement la compat Kotlin 2.4.0** — requis avec Kotlin 2.4.10. KSP1/kapt à proscrire. Confiance HIGH |
| Navigation Compose | **2.10.0** (spec : 2.9+) | NavGraph 3 onglets + `detail/{videoId}` | Dernière stable (metadata 2026-08-26). Confiance HIGH |
| WorkManager | **2.11.2** (spec : 2.10+) | `PipelineWorker` foreground `dataProcessing` | Dernière stable ; 2.12.0 uniquement en -rc01/-alpha → rester sur 2.11.2. Confiance HIGH |

### Bibliothèques support

| Bibliothèque | Version | Rôle | Quand / statut vérifié |
|---|---|---|---|
| `androidx.security:security-crypto` | **1.1.0** (stable) | EncryptedSharedPreferences + MasterKey (Partie 5) | Remplace l'épinglage alpha06. API dépréciée — voir Alerte 3. Confiance HIGH |
| `com.google.android.gms:play-services-ads` | **25.4.0** (spec : 24.x) | AdMob rewarded | 24.x existe encore mais la v25.0.0 (fév. 2026) est sortie depuis : clause de fraîcheur → 25.4.0. Breaking changes v24→v25 visant surtout la médiation (callbacks dépréciés retirés) — lire la migration Google. Note : le SDK GMA « legacy » est en maintenance, Google pousse un SDK « next-gen » — surveiller, sans impact v1. Confiance HIGH |
| `com.google.android.ump:user-messaging-platform` | **4.0.0** | Consentement UMP avant `MobileAds.initialize()` (EEA/UK) | Requis par Partie 5 §6 ; dernière stable Google Maven. Confiance HIGH |
| `com.google.mlkit:face-detection` | **16.1.7** | Suivi de visage **modèle bundled, offline** (Fonction D) | Dernière version (metadata stable depuis 2024-08-07, non dépréciée) ; artifact toujours résolvable sur Google Maven. Confiance HIGH |
| `com.github.TeamNewPipe:NewPipeExtractor` (JitPack) | **v0.26.5** | Métadonnées + flux audio/vidéo YouTube (Fonctions A/F) | Release du 2026-08-15 ; **build JitPack « ok » vérifié** ; dépôt actif (dernier push 2026-09-04). ⚠️ **GPL-3.0** (voir Alerte 2). Épingler le tag `v0.26.5` dans `libs.versions.toml` (les tags sans « v » résolvent aussi). Confiance HIGH |
| `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl` | **8.1.7** (remplace `com.arthenica:ffmpeg-kit-full-gpl:6.0-2`) | Conversion 16 kHz, volumedetect, crop, hardsub libass, loudnorm, x264 (Fonctions A/D/E/F) | Voir Alerte 1. Drop-in (API `com.arthenica.ffmpegkit` conservée), 16 KB imposé, 3 lignes LTS maintenues. Confiance HIGH (obtenabilité) / MEDIUM (détails arm64-only) |
| whisper.cpp (source, compilé CMake/NDK + JNI) | **b4938** (2026-08-20) | Transcription mot-à-mot `token_timestamps=true` (Fonction B) | Pas d'artifact Maven : intégration en source via CMake/NDK, comme imposé. Le repo fournit l'exemple officiel `examples/whisper.android` (JNI Kotlin + chaîne CMake). Releases numérotées par build ; version linguistiquement stable. Modèles GGML tiny/base/small sur Hugging Face + SHA-256 (Partie 5 §4). Confiance HIGH |
| `androidx.media3:media3-exoplayer` (+ `media3-ui`) | **1.11.0** | Lecteur `ShortPlayerSheet` 9:16 (Partie 2 §3) | Implicite dans le cahier des charges (« Media3 ExoPlayer ») ; dernière stable Google Maven. Confiance HIGH |
| `com.squareup.okhttp3:okhttp` | **5.5.0** | Smart download audio + Range Requests vidéo, HTTPS strict (Fonctions A/F) | Ligne 5.x stable. Configurer sans fallback cleartext (Partie 5). Confiance HIGH |
| `androidx.datastore:datastore-preferences` | **1.2.1** | Persistance thème/langue (non sensible — Partie 5 §1.3) | Dernière stable (1.3.0 en alpha → rester 1.2.1). Confiance HIGH |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | **1.11.0** | Coroutines + Flow (MVVM, annulation propagée) | Dernière stable Google Maven/Maven Central. Confiance HIGH |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.11.0** | Décodage sortie JSON Whisper + client Gemini structured outputs | Dernière stable (1.12.0-RC ignorée). Confiance HIGH |
| `io.coil-kt.coil3:coil-compose` | **3.6.2** | Chargement miniatures locales (Écrans 1/2/3) | Option le plus simple et standard Compose pour thumbnails filesDir (règle §0.1 du spec). Apache 2.0. Confiance HIGH |
| `com.google.genai:google-genai` | 1.70.0 | *Alternative* au client Gemini artisanal (BYOK, C.2) | SDK officiel Java existe ; l'option la plus simple et locale reste OkHttp + kotlinx.serialization (une seule endpoint HTTPS). Choisir l'un, ne pas mixer. Confiance HIGH (obtenabilité) |

### Outils de développement

| Outil | Rôle | Notes |
|---|---|---|
| NDK **r28+** (défaut AGP 9.4 : 28.2) | Compilation whisper.cpp + JNI | r28 aligne **16 KB par défaut** ; avec AGP ≥ 8.5.1 le packaging AAB est aligné 16 KB automatiquement. Toute prébuilt `.so` tierce doit être vérifiée (voir vérifications ci-dessous) |
| CMake (toolchain NDK) | Build whisper.cpp | Passer `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` explicitement : le master whisper.cpp a produit du `max-page-size=4096` par le passé (issue ggml-org/whisper.cpp#3440) — ne pas se fier au défaut |
| Android Studio | IDE | Prendre la version compatible AGP 9.4 (page « about-agp » non lue intégralement — vérifier à l'installation). Confiance MEDIUM |
| R8 (activé par AGP release) | Obfuscation + stripping logs | Règles `-assumenosideeffects android.util.Log` (Partie 5) ; `keep` Room/Hilt auto via leurs règles embarquées |
| `check_elf_alignment.sh` / `llvm-readelf` / APK Analyzer | Vérification 16 KB | `llvm-readelf -l lib*.so \| grep LOAD` → align `2**14` exigé ; `zipalign -v -c -P 16 4` ; échéance Play : **mise à jour refusée à partir du 2027-02-01** si non conforme (apps ciblant API 35+) |

---

## Installation

Tout passe par le **catalogue unique** `gradle/libs.versions.toml` (exigence Partie 1 §2.2). Extrait vérifié :

```toml
[versions]
kotlin = "2.4.10"              # clause fraîcheur 2026-09-04 ; spec 2.3.0
agp = "9.4.0"                  # exige Gradle >= 9.6.0
gradle-wrapper = "9.7.1"
ksp = "2.3.11"                 # 2.3.10+ = compat Kotlin 2.4
composeBom = "2026.08.00"
hilt = "2.60.1"
room = "2.8.4"
navigationCompose = "2.10.0"
workManager = "2.11.2"
securityCrypto = "1.1.0"       # stable ; API dépréciée mais imposée par le spec
playServicesAds = "25.4.0"
ump = "4.0.0"
mlkitFaceDetection = "16.1.7"
newPipeExtractor = "v0.26.5"   # JitPack ; GPL-3.0
ffmpegKit = "8.1.7"            # dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl ; GPL-3.0
media3 = "1.11.0"
okhttp = "5.5.0"
datastore = "1.2.1"
coroutines = "1.11.0"
serializationJson = "1.11.0"
coil = "3.6.2"

[libraries]
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
play-services-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "playServicesAds" }
ump = { module = "com.google.android.ump:user-messaging-platform", version.ref = "ump" }
mlkit-face-detection = { module = "com.google.mlkit:face-detection", version.ref = "mlkitFaceDetection" }
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newPipeExtractor" }
ffmpeg-kit-full-gpl = { module = "dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl", version.ref = "ffmpegKit" }
# ... room-runtime/room-compiler/ksp, hilt-android/hilt-compiler, navigation-compose,
#     work-runtime-ktx, media3-exoplayer, okhttp, datastore-preferences, coil-compose ...

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

```toml
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
```

Dépôts requis : `google()` , `mavenCentral()` , **`mavenjitpack()`** (`https://jitpack.io`) — JitPack est indispensable pour NewPipeExtractor uniquement.

whisper.cpp (aucun package) : submodule git épinglé sur `b4938` + module Gradle `externalNativeBuild` CMake ; bindings JNI dans `data/native/whisper/` ; flags linker 16 KB obligatoires (voir Outils).

---

## Alternatives considérées

| Recommandé | Alternative | Quand utiliser l'alternative |
|---|---|---|
| Fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` | Auto-build FFmpeg (libass+x264+loudnorm) via les scripts ffmpeg-kit d'origine | Uniquement si le fork disparaît ou si l'arm64-only est bloquant ; coût : chaîne de build NDK complète à maintenir soi-même + re-démontrer l'alignement 16 KB. Confiance HIGH sur la faisabilité, MEDIUM sur l'effort |
| `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:6.0.3` | — | Substitution la plus littérale de l'épinglage « 6.0-2 » si on veut minimiser l'écart fonctionnel avec le spec (FFmpeg 6.x) ; la clause de fraîcheur plaide quand même pour 8.1.7 |
| OkHttp + kotlinx.serialization pour Gemini | `com.google.genai:google-genai:1.70.0` | Si le parsing des structured outputs maison devient coûteux ; ajoute un SDK de plus (spécifie « option la plus simple » → client maison par défaut) |
| `security-crypto:1.1.0` (spec) | DataStore + Tink (chiffré Keystore) | Aucun en v1 sans amendement du spec ; à garder comme plan de migration documenté derrière `SecureStorageRepository` |
| NewPipeExtractor | yt-dlp (Python), API InnerTube artisanale | Aucun réaliste pour Android on-device en Kotlin : yt-dlp n'est pas embarquable sans runtime Python ; InnerTube artisanal casse en permanence. → accepter la GPL-3.0 (Alerte 2) |
| Kotlin 2.4.10 | Kotlin 2.3.21 | Si un plugin critique (Hilt/KSP/Compose) révèle une régression 2.4 à l'échafaudage ; les deux satisfont le spec |

## Ce qu'il ne faut PAS utiliser

| À éviter | Pourquoi | À utiliser à la place |
|---|---|---|
| `com.arthenica:ffmpeg-kit-*` (toutes versions) | **Retiré de Maven Central depuis le 2025-04-01** (404 vérifié) ; tout miroir tiers non signé = risque supply-chain | `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` |
| `ffmpeg-kit` variantes non-GPL (`full`, `video`) | N'incluent **pas** libx264 → la Fonction F (`-c:v libx264`) est impossible | `full-gpl` (décision GPL assumée, Alerte 2) |
| NewPipeEncoder/extracteurs non maintenus (youtube-dl forks Android non vérifiés) | Aucun n'a la maintenance ni la couverture de NewPipeExtractor (actif au 2026-09-04) | NewPipeExtractor v0.26.5 |
| kapt / KSP1 | Dépréciés ; KSP1 cassé avec AGP 9.0+ (fix KSP 2.3.5) et Kotlin 2.4 (fix KSP 2.3.10) | KSP2 (`com.google.devtools.ksp` 2.3.11) |
| `fallbackToDestructiveMigration`, `COLLATE NOCASE` pour la recherche | Interdits par le spec (Parties 4) ; `NOCASE` = ASCII seulement | Migrations additives + `titleSearch` pré-minorisé Kotlin |
| Tout `.so` prébuilt sans vérification d'alignement 16 KB | Échec d'installation sur devices 16 KB ; refus Play dès le 2027-02-01 | Vérifier chaque `.so` (llvm-readelf align 2**14) avant intégration |
| Stockage du solde dans Room/DataStore, GMA SDK « next-gen » (early access) | Violation Partie 5 ; SDK ads next-gen pas encore généralisé | EncryptedSharedPreferences (spec) + `play-services-ads` 25.x legacy |

## Patterns de stack par variante

**Si le propriétaire accepte GPL-3.0 pour l'app (recommandé) :**
- Utiliser `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` + `NewPipeExtractor v0.26.5` tels quels.
- Parce que c'est la seule combinaison qui couvre l'intégralité du pipeline A→F (extraction YouTube + x264 + libass) avec des composants maintenues.

**Si la liste blanche MIT/Apache/BSD/LGPL doit rester stricte :**
- Utiliser `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` (LGPL, sans x264) → remplacer `-c:v libx264` par l'encodage matériel MediaCodec ou OpenH264, et trouver un extracteur YouTube non-GPL (aucun crédible identifié) → **le pipeline A n'a pas de solution conforme** : escalade décisionnelle requise avant tout développement.
- Parce que le spec impose NewPipeExtractor nominativement, ce scénario exige un amendement du cahier des charges quoi qu'il arrive.

**Si les émulateurs x86_64 sont indispensables au dev (Windows) :**
- Vérifier à l'échafaudage si le fork publie `x86_64` ; sinon utiliser un device physique arm64 ou l'émulateur ARM sur hôte ARM — le fork annonce arm64-v8a seul (MEDIUM, à confirmer).

## Compatibilités de versions

| Paquet A | Compatible avec | Notes |
|---|---|---|
| AGP 9.4.0 | Gradle ≥ 9.6.0, JDK ≥ 17, build-tools 36 | Gradle 9.7.1 validé au-dessus du minimum ; API max 37 |
| Kotlin 2.4.10 | KSP ≥ 2.3.10 (recommandé 2.3.11) | KSP 2.3.10 = fix explicite des noms de modules Kotlin 2.4.0 ; les tags `2.2.21-x` de KSP sont l'ancien schéma |
| Room 2.8.4 / Hilt 2.60.1 | KSP 2.3.11 (KSP2) | Les deux consomment KSP ; kapt proscrit. Vérifier le build croisé à l'échafaudage (premier jet de `libs.versions.toml`) |
| Compose BOM 2026.08.00 | compileSdk 36, Kotlin 2.x (plugin Compose embarqué) | compileSdk 36 imposé par le BOM récent ; le compilateur Compose suit la version Kotlin via `org.jetbrains.kotlin.plugin.compose` |
| play-services-ads 25.4.0 | minSdk ≥ 23 ; breaking changes v24→v25 (callbacks retirés, taille bannières) | minSdk 33 sans objet ; suivre la page migration AdMob à l'intégration |
| fork ffmpeg-kit 8.1.7 | NDK r27c, 16 KB imposé, minSdk 24, arm64-v8a (à confirmer) | API Java `com.arthenica.ffmpegkit` inchangée → `FFmpegWrapper` (Partie 1 §3.2) sans changement |
| whisper.cpp b4938 | NDK r28+ (16 KB par défaut) + flags explicites | Ajouter `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` quel que soit le NDK ; vérifier avec llvm-readelf (issue #3440 : défaut à 4096 constaté par le passé) |
| Google Play | 16 KB obligatoire pour les updates à partir du 2027-02-01 ; targetSdk 36 = exigence 2026 | Détail de la règle : apps ciblant API 35+, 64 bits |

## Sources

- `https://dl.google.com/android/maven2/...` (maven-metadata : compose-bom, room, work, navigation, security-crypto, play-services-ads, face-detection, ump, media3, datastore) — versions Google Maven officielles — **HIGH**
- `https://repo1.maven.org/maven2/...` (hilt-android, symbol-processing-gradle-plugin, okhttp, kotlinx-coroutines-android, kotlinx-serialization-json, coil3, google-genai, arthenica/ffmpeg-kit-full-gpl [404], dev.ffmpegkit-maintained/ffmpeg-kit-full-gpl) — **HIGH**
- `https://services.gradle.org/versions/current` — Gradle 9.7.1 (2026-08-19) — **HIGH**
- `https://kotlinlang.org/docs/releases.html` — Kotlin 2.4.10 / 2.3.x — **HIGH**
- `https://api.github.com/repos/TeamNewPipe/NewPipeExtractor` (+ `/releases/latest`) + `https://jitpack.io/api/builds/...` — v0.26.5, actif, GPL-3.0, build JitPack ok — **HIGH**
- `https://raw.githubusercontent.com/ffmpegkit-maintained/ffmpeg-kit/main/README.md` + metadata Maven Central du fork — retraite Arthenica 2025-01-06, suppression binaires 2025-04-01, variants, licences, 16 KB — **HIGH (obtenabilité) / MEDIUM (arm64-only, détails README)**
- `https://api.github.com/repos/ggml-org/whisper.cpp/releases/latest` (b4938, 2026-08-20) ; issue `ggml-org/whisper.cpp#3440` (alignement 16 KB) — **HIGH / MEDIUM pour l'état exact du CMakeLists courant**
- `https://developer.android.com/build/releases/gradle-plugin` — AGP 9.4.0 : Gradle ≥ 9.6.0, JDK 17, NDK 28.2 — **HIGH**
- `https://developer.android.com/guide/practices/page-sizes` — exigence 16 KB, échéance 2027-02-01, NDK r28+, AGP 8.5.1+ — **HIGH**
- `https://developers.google.com/admob/android/migration` + release notes — migration v24→v25, minSdk — **HIGH**
- Dépréciation security-crypto : docs officielles `developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences` + analyses ProAndroidDev / ed-george.github.io — **HIGH pour le statut, MEDIUM pour la qualité des articles tiers**

---
*Stack research for: ShortifyLocal AI (Android on-device AI video clipping)*
*Researched: 2026-09-04*
