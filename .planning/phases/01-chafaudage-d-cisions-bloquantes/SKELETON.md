# Walking Skeleton — ShortifyLocal AI

**Phase:** 1 (Échafaudage & décisions bloquantes)
**Generated:** 2026-09-05

## Capability Proven End-to-End

> Un clone frais du dépôt buildé avec le wrapper Gradle produit un APK debug qui s'installe et se lance sur émulateur Android 13+ : l'utilisateur voit une coquille Compose à 3 onglets (Accueil / Historique / Paramètres) thémée Soft-Clean, et la bascule clair/sombre lit et écrit une préférence réelle persistée dans DataStore qui survit au force-stop.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Kotlin 2.4.10 natif Android, AGP 9.4.0, Gradle wrapper 9.7.1, JDK 17 (toolchain foojay 1.0.0 auto-provisionnée, D-02) | Errata E2 fait foi ; AGP 9 built-in Kotlin désactivé (`android.builtInKotlin=false` + `android.newDsl=false`) pour honorer Kotlin 2.4.10 + plugin Compose |
| Build system | Projet Gradle single-module `app/`, catalogue unique `gradle/libs.versions.toml` (toutes versions E2, zéro version inline sauf le resolveur foojay dans settings — limitation Gradle documentée) | Reproductibilité clone frais (SPEC R1) ; supply-chain épinglée |
| Data layer | DataStore Preferences (`settings`, clé `theme_mode`) pour la préférence thème ; Room 2.8.4 initialisée v1 SANS entités (schéma exporté commité, DB `shortify_local.db`) — entités en Phase 3 | D-05 : préférence non sensible → DataStore (jamais Room ni stockage chiffré) ; Room câblée pour la Phase 3 |
| Auth | Aucune — app locale sans compte (philosophie local-first) | Partie 1 §0 ; SEC-01/02 arrivent en Phase 3 |
| DI / async | Hilt 2.60.1 (`@HiltAndroidApp`, KSP2 2.3.11 uniquement) + WorkManager 2.11.2 on-demand (`Configuration.Provider`, propriété Kotlin `workManagerConfiguration`, initialiseur par défaut retiré du manifest) | API courantes vérifiées dans androidx source (les snippets officiels sont périmés) |
| UI | Jetpack Compose (BOM 2026.08.00, Material 3) + Navigation Compose 2.10.0 (routes string `accueil`/`historique`/`parametres`) ; tokens Soft-Clean (docs/02 §1.1–1.3) mappés sur `colorScheme`/`Shapes` ; ombre unique via `Modifier.shadow` couleurs tokens ; NavigationBar standard stylée (le `BottomNavPill` définitif arrive en Phase 6) | D-06 discrétion de mapping sous contrainte du résultat visuel normatif ; structure RTL-ready (Start/End, `supportsRtl=true`) |
| Deployment target | Émulateur AVD android-36 (x86_64 — le fork ffmpeg-kit 8.1.7 embarque aussi x86_64) vérifié par script adb ; appareil physique ARM64 Android 13+ en vérification humaine fin de phase (D-03/D-04) | Double cible R7 ; les `.so` du fork ne sont jamais chargés en P1 |
| Supply chain | Fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` épinglé, SHA-256 `374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b` consigné en commentaire du catalogue, résolution Maven Central uniquement (pas de JitPack en P1) | Substitution de l'artefact mort validée (PROJECT.md Key Decisions, ERRATA E2) |
| Dépendance déclarée non exécutée | ffmpeg-kit déclarée dans `app/` mais jamais appelée en P1 (aucun natif exécuté) ; NewPipeExtractor en entrée catalogue seulement (repo JitPack différé en Phase 4) | Périmètre P1 : le build résout, rien n'exécute |

## Stack Touched in Phase 1

- [x] Project scaffold (Gradle wrapper 9.7.1, AGP 9.4.0, KSP2, catalogue unique, `.gitignore`/`.gitattributes`)
- [x] Routing — 3 routes Compose (`accueil`, `historique`, `parametres`) navigables
- [x] Database — une lecture ET une écriture réelles : DataStore `theme_mode` (Room v1 câblée, schéma exporté, sans entités)
- [x] UI — interaction réelle : bouton de bascule de thème câblé DataStore → recomposition
- [x] Deployment — `adb install -r` + `am start` scriptés sur émulateur (déploiement dev) ; appareil physique en vérification fin de phase

## Out of Scope (Deferred to Later Slices)

- Exécution FFmpeg/whisper, module `whisper-native/` (CMake/NDK/JNI) — Phase 2
- Entités Room, DAOs, repositories, migrations — Phase 3 (DATA-01…05)
- Coffre-fort chiffré, HMAC, économie de tokens — Phase 3 (SEC-01/02, TOK-01/04)
- Pipeline A→F, NewPipeExtractor actif (repo JitPack déclaré en Phase 4) — Phases 4–5
- Composants design system (`SoftCard`, `TokenPill`, `BottomNavPill`…), écrans réels, typographie & polices bundlées — Phase 6
- AdMob, UMP, i18n 7 langues + RTL complet, `allowBackup=false`/durcissement release, deep link `shortify://invite` — Phase 7

## Subsequent Slice Plan

- Phase 2: prouver la chaîne native sur appareil réel (whisper.cpp JNI mot-à-mot + commande FFmpeg du fork, alignement 16 KB)
- Phase 3: contrats domain pur Kotlin + Room SSOT (4 entités) + coffre-fort HMAC + économie de tokens + Whisper Manager
- Phase 4: pipeline A→C (métadonnées → audio → transcription → segments scorés, worker foreground)
- Phase 5: pipeline D→F (premier Short rendu, karaoké hardsubbé, export, déverrouillage tokens)
- Phase 6: 4 écrans Compose branchés sur le réel + design system Soft-Clean complet
- Phase 7: monétisation (AdMob/UMP), i18n 7 langues + RTL intégral, build release durci
