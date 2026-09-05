# Phase 1: Échafaudage & décisions bloquantes — Specification

**Created:** 2026-09-05
**Ambiguity score:** 0.12 (gate: ≤ 0.20)
**Requirements:** 7 locked

## Goal

Le dépôt passe de « zéro fichier build » à « `./gradlew assembleDebug` produit un APK debug installable et lançable sur émulateur Android 13+ ET appareil physique ARM64 », depuis un socle verrouillé : catalogue de versions unique conforme à l'errata du 2026-09-05, fork `ffmpegkit-maintained` épinglé avec checksum, coquille Compose à 3 destinations thémée Soft-Clean, et plomberie Hilt/WorkManager initialisée (tranche Room reprogrammée en Phase 3 — décision propriétaire B du 2026-09-05, voir exigence 5).

## Background

Aucun code n'existe : le dépôt ne contient que le cahier des charges normatif (`docs/01`–`05` + `docs/ERRATA-2026-09-05.md`) et le planning (`.planning/`). Pas de `.kt`, pas de Gradle, pas de manifest. Les 4 décisions propriétaire (GPL-3.0, substitution fork `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7`, distribution hybride, épinglage versions 2026-09) sont **déjà consignées** dans PROJECT.md Key Decisions et l'errata — la Phase 1 ne les re-décide pas, elle les **applique dans le build**. Livrable principal : le projet Android qui n'existe pas encore. Bloquants ouverts hérités de STATE.md : disponibilité x86_64 du fork (l'install émulateur + physique de R7 la sonde) et coordonnée Maven exacte + checksum à consigner (R6).

## Requirements

1. **Échafaudage build reproductible** : un clone frais buildé avec le wrapper produit un APK debug installable.
   - Current: aucun fichier Gradle, aucun wrapper, aucun code
   - Target: projet Gradle single-module `app/` (package racine `com.shortifylocal.ai`), wrapper Gradle 9.7.1 commité, catalogue unique `gradle/libs.versions.toml` contenant les versions exactes de l'errata E2 (Kotlin 2.4.10, AGP 9.4.0, Gradle 9.7.1, JDK 17, minSdk 33, compile/targetSdk 36, Compose BOM 2026.08.00, Hilt 2.60.1, Room 2.8.4 + KSP2 2.3.11, Navigation 2.10.0, WorkManager 2.11.2, security-crypto 1.1.0, ads 25.4.0, UMP 4.0.0, ML Kit 16.1.7, Media3 1.11.0, OkHttp 5.5.0, Coil 3.6.2, DataStore 1.2.1, NewPipeExtractor v0.26.5, fork ffmpeg-kit 8.1.7, whisper.cpp b4938 en commentaire) ; `local.properties` gitignoré
   - Acceptance: `./gradlew assembleDebug` réussit sur un clone frais (machine avec JDK 17 + Android SDK, sans Gradle global) et produit `app-debug.apk` ; les versions du catalogue correspondent 1:1 à l'errata E2

2. **Coquille Compose thémée Soft-Clean** : navigation à 3 destinations vides avec les tokens du design system appliqués dès P1.
   - Current: rien n'existe
   - Target: coquille Compose avec 3 destinations (Accueil, Historique, Paramètres) navigables via une barre de navigation ; tokens Soft-Clean définis dans le thème : couleurs clair/sombre §1.1 (Background #EEF0F2/#0E0E10, Card #FFFFFF/#1A1A1E, AccentActive #E4590C/#FF7A3D…), rayons 32/24/16/28, UNE seule ombre douce verticale §1.2 (5 % clair / 30 % sombre) ; structure RTL-ready (alignements Start/End, `supportsRtl=true`)
   - Acceptance: l'APK lancé affiche les 3 onglets navigables ; le thème utilise les tokens ci-dessus (inspectable dans le code thème) ; grep = 0 alignement Left/Right ; libellés d'onglets via ressources (zéro chaîne en dur)

3. **Thème clair/sombre persisté** : la bascule survit au processus.
   - Current: rien n'existe
   - Target: bascule clair/sombre dans la coquille, choix persisté via DataStore, restauré après force-stop + relance
   - Acceptance: basculer → force-stop → relancer : le thème restauré est le dernier choisi (test manuel scriptable via adb)

4. **Squelette Clean Architecture** : la structure normative est en place et contrôlable.
   - Current: rien n'existe
   - Target: packages `presentation/ domain/ data/ worker/ di/ util/` sous `com.shortifylocal.ai` conformes à l'arborescence Partie 1 §3.2 ; `domain/` pur Kotlin
   - Acceptance: l'arborescence existe ; grep `import android`/`import androidx` dans `domain/` = 0

5. **Plomberie Hilt / WorkManager initialisée** : les frameworks sont câblés, prêts pour les Phases 3–4. AMENDEMENT (2026-09-05, décision propriétaire B) : la tranche Room v1 — `AppDatabase` sans entités, `DatabaseModule`, DB `shortify_local.db`, schéma exporté commité — est **reprogrammée en Phase 3 (DATA-01)**. Justification : Room 2.8.4 rejette sans condition un `@Database` sans entités (échec KSP « @Database annotation must specify list of entities », aucun flag de contournement, pas d'alternative views) — toute variante P1 exigerait du code jetable refusé par le propriétaire.
   - Current: rien n'existe
   - Target: `@HiltAndroidApp` + `Configuration.Provider` avec `HiltWorkerFactory` ; arborescence Clean Architecture en place (packages normatifs avec .gitkeep) ; dépendances Room 2.8.4 + KSP2 restant épinglés au catalogue E2 (inertes sans classe `@Database`) pour la Phase 3
   - Acceptance: l'app démarre sans crash avec Hilt opérationnel (injection vérifiable) ; `assembleDebug` vert sans aucun fichier Room en P1 ; grep `fallbackToDestructiveMigration` = 0

6. **Fork ffmpeg-kit épinglé et résoluble** : l'artefact mort est banni du build.
   - Current: aucun build ; le cahier des charges référence encore `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (404 Maven Central)
   - Target: dépendance `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` déclarée dans `app/` (embarque les `.so` arm64, jamais chargée en P1) ; checksum SHA-256 de l'AAR résolu consigné en commentaire du catalogue ; résolution exclusivement Maven Central (jamais JitPack/-SNAPSHOT)
   - Acceptance: `assembleDebug` résout le fork ; le checksum consigné correspond à l'AAR téléchargé ; grep `com.arthenica` dans les fichiers de build = 0

7. **Vérification double cible** : l'APK fonctionne là où la Phase 2 en aura besoin.
   - Current: rien n'existe
   - Target: install + lancement réussis sur émulateur Android 13+ ET appareil physique ARM64 (décision de l'entretien) ; la coquille et la bascule de thème y fonctionnent
   - Acceptance: `adb install` + lancement OK sur les deux cibles ; navigation 3 onglets + bascule thème opérationnelles sur les deux ; la relance à froid redémarre proprement

## Boundaries

**In scope:**
- Projet Gradle single-module `app/` + wrapper + `.gitignore` Android (build/, local.properties)
- Catalogue unique `gradle/libs.versions.toml` complet (toutes les entrées E2 de l'errata)
- Coquille Compose : 3 destinations vides + navigation + tokens Soft-Clean (couleurs, rayons, ombre) clair/sombre
- Bascule thème persistée DataStore
- Arborescence Clean Architecture (packages vides ou quasi vides)
- Wiring Hilt + WorkManager (worker factory) — la tranche Room (v1 sans entités, schéma exporté) est reportée en Phase 3 (décision propriétaire B)
- Dépendance fork ffmpeg-kit 8.1.7 déclarée et résolue, checksum consigné
- Icône launcher par défaut d'Android Studio (branding final hors P1)

**Out of scope:**
- Exécution de commandes FFmpeg ou whisper — Phase 2 (spike natif ; les `.so` embarquent mais ne sont jamais chargés en P1)
- Module `whisper-native/` (CMake/NDK/JNI) — Phase 2
- Entités Room, DAOs, repositories — Phase 3 (DATA-01…05) ; la tranche Room v1 (`AppDatabase` sans entités, `DatabaseModule`, schéma exporté, DB `shortify_local.db`) est également reprogrammée Phase 3 (décision propriétaire B du 2026-09-05 : Room 2.8.4 rejette un `@Database` sans entités)
- Coffre-fort chiffré, HMAC, économie de tokens — Phase 3 (SEC-01/02, TOK-01/04)
- Pipeline A→F, NewPipeExtractor en dépendance active — Phases 4–5 (PIPE-*)
- Composants du design system (SoftCard, TokenPill, etc.), écrans réels, typographie complète & polices bundlées — Phase 6 (UI-* ; P1 n'apporte que les tokens couleurs/rayons/ombre)
- AdMob, UMP, i18n 7 langues (les 3 libellés d'onglets passent par ressources FR par défaut), build release/signing — Phase 7
- Positionnement Play / canal APK direct — décision déjà consignée (ERRATA E4), aucune action build en P1

## Constraints

- Versions non négociables : errata E2 du 2026-09-05 fait foi (clause de fraîcheur appliquée une fois pour P1 ; tout écart = amendement de l'errata, pas un choix local)
- JDK toolchain 17 ; minSdk 33 / compileSdk-targetSdk 36 ; `android.supportsRtl=true`
- Aucun natif exécuté en P1 (les `.so` arm64-only du fork embarquent sans être chargés — l'install émulateur x86_64 doit donc rester possible)
- Design tokens : Partie 2 §1.1–1.2 via `docs/02-UI-UX.md` + errata ; interdiction du neumorphisme (une seule ombre douce verticale)
- HTTPS-only : `usesCleartextTraffic` non activé ; permissions minimales (P1 : aucune permission runtime nécessaire)

## Acceptance Criteria

- [ ] `./gradlew assembleDebug` réussit sur un clone frais (JDK 17 + SDK, sans Gradle global) et produit un APK installable
- [ ] `gradle/libs.versions.toml` contient toutes les versions E2 de l'errata ; grep `com.arthenica` dans les fichiers de build = 0 résultat
- [ ] Le checksum SHA-256 de l'AAR du fork 8.1.7 est consigné dans le catalogue et correspond à l'artefact résolu (Maven Central uniquement)
- [ ] L'APK s'installe et se lance sur émulateur Android 13+ ET sur appareil physique ARM64 ; les 3 onglets naviguent sur les deux
- [ ] Bascule clair/sombre → force-stop → relance : le thème restauré est le dernier choisi (émulateur ET physique)
- [ ] Les tokens Soft-Clean sont définis dans le thème : couleurs §1.1 clair/sombre, rayons 32/24/16/28, une seule ombre douce verticale §1.2
- [ ] `domain/` contient 0 import `android.*`/`androidx.*` (grep)
- [ ] *(Reporté Phase 3 — décision propriétaire B du 2026-09-05)* `AppDatabase` v1 compile, `exportSchema=true`, schéma JSON commité, DB nommée `shortify_local.db` — Room 2.8.4 rejette un `@Database` sans entités : la tranche complète (schéma inclus) est livrée avec les entités en DATA-01
- [ ] Zéro chaîne UI en dur dans la coquille — les 3 libellés d'onglets passent par les ressources (grep littéraux dans les Composables = 0)
- [ ] Zéro alignement Left/Right dans le code UI (grep) ; `supportsRtl=true` dans le manifest
- [ ] Manifest sans `READ_MEDIA*`, `READ_EXTERNAL_STORAGE`, `ACCESS_*_LOCATION`, `READ_CONTACTS` et sans `usesCleartextTraffic="true"` (grep)
- [ ] Zéro `kapt`/KSP1 dans les fichiers de build (grep) et zéro `fallbackToDestructiveMigration` (grep)

## Edge Coverage

**Coverage:** 8/13 applicable edges resolved · 0 unresolved · 5 dismissed with reason

| Category | Requirement | Status | Resolution / Reason |
|----------|-------------|--------|---------------------|
| reproductibilité (relevé manuel) | R1 | ✅ covered | Clone frais + wrapper : critère d'acceptation 1 (`./gradlew` sans Gradle global, `local.properties` gitignoré) |
| unclassified | R1 | ✅ covered | Critère d'acceptation 2 : versions du catalogue = errata E2 1:1 |
| RTL structurel (relevé manuel) | R2 | ✅ covered | Critères d'acceptation 2 & 10 : Start/End uniquement, `supportsRtl=true` |
| unclassified | R2 | ⛔ dismissed | Écrans vides assumés en P1 — le contenu réel arrive en Phase 6 |
| idempotency | R3 | ⛔ dismissed | DataStore écrit atomiquement ; la persistance après relance (critère 5) couvre le cas |
| concurrency | R3 | ⛔ dismissed | Écritures DataStore sérialisées ; UI mono-utilisateur |
| empty | R5 | 🔁 reprogrammé | Critère d'acceptation 8 reporté Phase 3 (DATA-01, décision propriétaire B du 2026-09-05) : Room 2.8.4 rejette un `@Database` sans entités — le schéma sera exporté avec les entités |
| encoding | R5 | ⛔ dismissed | Aucune donnée textuelle utilisateur en P1 ; Unicode = exigence DATA-03 (Phase 3) |
| supply-chain (relevé manuel) | R6 | ✅ covered | Critère d'acceptation 3 : checksum SHA-256 consigné + résolution Maven Central uniquement |
| ABI (relevé manuel) | R6 | ✅ covered | Couvert par R7 (critère 4) : install émulateur x86_64 + physique arm64, `.so` jamais chargés en P1 |
| unclassified | R6 | ✅ covered | Critère d'acceptation 2 : grep `com.arthenica` = 0 |
| idempotency | R7 | ✅ covered | Critère d'acceptation 5 : relance à froid propre, thème restauré |
| concurrency | R7 | ⛔ dismissed | Installation interrompue = comportement OS, hors périmètre spec |

## Prohibitions (must-NOT)

**Coverage:** 8/8 applicable prohibitions resolved · 0 unresolved

| Prohibition (must-NOT statement) | Requirement | Status | Verification / Reason |
|----------------------------------|-------------|--------|------------------------|
| MUST NOT référencer `com.arthenica:ffmpeg-kit-full-gpl` dans aucun fichier de build | R6 | resolved | verification: test — grep fichiers de build (descripteur de check câblé : à produire au plan-phase) |
| MUST NOT coder en dur la moindre chaîne UI (y compris les 3 libellés d'onglets → ressources) | R2 | resolved | verification: test — grep littéraux FR/EN dans les .kt de présentation (ajustement utilisateur : judgment → test) |
| MUST NOT utiliser d'alignements Left/Right (structure RTL dès le premier composant) | R2 | resolved | verification: test — grep |
| MUST NOT déclarer des permissions stockage/localisation/contacts dans le manifest P1 | R1 | resolved | verification: test — grep `READ_MEDIA*`/`READ_EXTERNAL_STORAGE`/`ACCESS_*_LOCATION`/`READ_CONTACTS` (ajustement utilisateur : judgment → test) |
| MUST NOT introduire de neumorphisme (ombre double clair+sombre, élément de la couleur du fond) dans la coquille | R2 | resolved | verification: judgment — revue visuelle du thème contre Partie 2 §0/§1.2 |
| MUST NOT utiliser kapt ou KSP1 — KSP2 uniquement (`com.google.devtools.ksp` 2.3.11) | R1 | resolved | verification: test — grep `kapt` dans les fichiers de build (ajout utilisateur) |
| MUST NOT appeler `fallbackToDestructiveMigration()` sur la base Room initialisée en P1 | R5 | resolved | verification: test — grep (ajout utilisateur ; rappel DATA-05) |
| MUST NOT déclarer `android:usesCleartextTraffic="true"` dans le manifest P1 (HTTPS-only) | R1 | resolved | verification: test — grep manifest (ajout utilisateur ; ERRATA/SEC-04) |

*(Interdits candidats écartés au filtrage : gitignore/commit d'artefacts build = routine engineering ; secrets/signing release = rien à protéger en P1 ; positions adverse possédées par les specs ultérieures.)*

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                              |
|--------------------|-------|------|--------|------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | ✓      | Livrable mesurable : APK installable double cible |
| Boundary Clarity   | 0.88  | 0.70 | ✓      | Périmètre UI tranché (tokens oui, composants non) |
| Constraint Clarity | 0.85  | 0.65 | ✓      | applicationId, versions E2, aucun natif exécuté |
| Acceptance Criteria| 0.82  | 0.70 | ✓      | 12 critères pass/fail dont 6 checks mécaniques grep |
| **Ambiguity**      | 0.12  | ≤0.20| ✓      | Gate passé après le round 1 |

## Interview Log

| Round | Perspective    | Question summary         | Decision locked                    |
|-------|----------------|--------------------------|------------------------------------|
| 1     | Researcher     | Quel applicationId / package racine ? | `com.shortifylocal.ai` (arborescence normative Partie 1 §3.2) |
| 1     | Researcher     | Sur quelle cible vérifier l'APK P1 ?  | Émulateur Android 13+ ET appareil physique ARM64 (sonde le blocage ABI du fork arm64-only dès P1) |
| 1     | Researcher     | Quel périmètre UI pour la coquille ?  | Tokens Soft-Clean inclus dès P1 (couleurs §1.1, rayons, ombre §1.2) — les composants complets restent en Phase 6 |
| —     | Gate           | Ambiguïté 0.128 après round 1         | Utilisateur : « Oui — écrire SPEC.md » |
| 2     | Edge + Prohibition | Résolution batch des 13 edges et 5 interdits | Edges validés ; interdits : +3 ajouts (KSP2 only, pas de `fallbackToDestructiveMigration`, HTTPS-only) et 2 upgrades judgment → test (chaînes en dur, permissions) |

---

*Phase: 01-chafaudage-d-cisions-bloquantes*
*Spec created: 2026-09-05*
*Next step: /gsd:discuss-phase 1 — implementation decisions (how to build what's specified above)*
