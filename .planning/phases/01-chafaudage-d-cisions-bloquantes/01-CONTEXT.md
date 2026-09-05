# Phase 1: Échafaudage & décisions bloquantes - Context

**Gathered:** 2026-09-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Livrer le socle build Android natif : un projet Gradle single-module (`app/`, package `com.shortifylocal.ai`) qui compile via `./gradlew assembleDebug` en un APK debug installable et lançable sur émulateur Android 13+ ET appareil physique ARM64, avec une coquille Compose à 3 destinations (Accueil, Historique, Paramètres) thémée Soft-Clean (tokens couleurs/rayons/ombre), un thème clair/sombre persisté, la plomberie Hilt/WorkManager/Room initialisée (Room v1 sans entités), et le fork `ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` épinglé avec checksum — zéro référence à l'artefact mort `com.arthenica`. Aucun natif exécuté en P1.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**7 requirements are locked.** See `01-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `01-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):** projet Gradle single-module `app/` + wrapper + `.gitignore` Android ; catalogue unique `gradle/libs.versions.toml` complet (toutes les entrées E2 de l'errata) ; coquille Compose : 3 destinations vides + navigation + tokens Soft-Clean clair/sombre ; bascule thème persistée DataStore ; arborescence Clean Architecture (packages `presentation/ domain/ data/ worker/ di/ util/`) ; wiring Hilt + WorkManager (worker factory) + Room (v1 sans entités, schéma exporté) ; dépendance fork ffmpeg-kit 8.1.7 déclarée et résolue, checksum consigné ; icône launcher par défaut Android Studio.

**Out of scope (from SPEC.md):** exécution FFmpeg/whisper et module `whisper-native/` (Phase 2) ; entités Room, DAOs, repositories (Phase 3) ; coffre-fort HMAC, économie de tokens (Phase 3) ; pipeline A→F et NewPipeExtractor actif (Phases 4–5) ; composants design system complets, écrans réels, typographie complète & polices (Phase 6) ; AdMob, UMP, i18n 7 langues, build release/signing (Phase 7) ; positionnement Play / canal APK direct (décision consignée, aucune action build P1).

</spec_lock>

<decisions>
## Implementation Decisions

### Environnement de développement
- **D-01:** L'environnement local est partiellement prêt : Android Studio, Android SDK et un émulateur existent déjà ; **le JDK 17 est le seul composant manquant**. Le plan ne doit PAS inclure de tâches d'installation SDK/Studio/AVD. — **Reversibility:** reversible — configuration locale, ne touche pas le dépôt
- **D-02:** Le JDK 17 est provisionné par **toolchain auto-provisioning Gradle** (resolveur foojay, `java-toolchain` dégradé automatiquement) plutôt qu'une installation manuelle — reproductible sur toute machine, rien à installer à la main. Le plan inclut le resolveur de toolchain dès l'échafaudage (build.gradle settings + `org.gradle.java.installations.auto-download` si pertinent).

### Vérification double cible (R7)
- **D-03:** L'appareil physique de vérification est un **téléphone ARM64 sous Android 13+**, connecté en **adb USB**. Les vérifications d'install/lancement peuvent être scriptées (`adb install -r` + `am start` + `adb shell`). — **Reversibility:** reversible
- **D-04:** L'émulateur sert de première cible rapide, l'appareil physique valide la cible arm64 (les `.so` arm64-only du fork ne sont jamais chargés en P1 — l'install émulateur x86_64 doit rester possible).

### Thème
- **D-05:** Au **premier lancement, le thème suit le système** (`isSystemInDarkTheme()` comme valeur initiale). La bascule utilisateur devient une **préférence explicite persistée** (DataStore) qui prime ensuite sur le système ; tant qu'aucun choix explicite n'existe, l'app reste synchronisée sur le système. — **Reversibility:** costly — devient le comportement observable du premier lancement et la sémantique du flag DataStore ; le changer après coup exige de redéfinir la logique de préférence (système vs override) et sa persistance
- **D-06:** Les tokens Soft-Clean (Partie 2 §1.1–1.3 : couleurs clair/sombre, rayons 32/24/16/28, ombre unique verticale 5 % clair / 30 % sombre) sont définis dès P1 dans le thème ; le **mapping vers les slots Material 3** (`colorScheme`, shapes) et l'implémentation de l'ombre unique (`Modifier.shadow` vs `drawBehind`) sont laissés à la discrétion de recherche/planification sous contrainte du résultat visuel normatif.

### Identité affichée
- **D-07:** Label launcher (`app_name`) = **« ShortifyLocal »** (plus court sous l'icône). Le nom complet « ShortifyLocal AI » reste utilisé dans le texte de partage `share_app`, l'à-propos et le futur listing store. Chaîne via ressources (`values/strings.xml`), jamais en dur. — **Reversibility:** reversible — une ressource à changer

### Claude's Discretion
- Mapping tokens Soft-Clean → slots MaterialTheme (D-06)
- Choix du composant de barre de navigation de la coquille (le composant définitif `BottomNavPill` arrive en Phase 6 ; la coquille P1 peut utiliser une NavigationBar standard stylée par les tokens)
- Structure fine des fichiers Gradle (convention plugins, ordre des blocs) sous contrainte : catalogue unique + zéro version inline + KSP2 uniquement

### Folded Todos
Aucun todo plié (0 correspondance à la phase).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Exigences verrouillées & contexte projet
- `.planning/phases/01-chafaudage-d-cisions-bloquantes/01-SPEC.md` — **Locked requirements — MUST read before planning** : 7 exigences, boundaries, 12 critères d'acceptation, 8 interdits, coverage des 13 cas limites
- `.planning/PROJECT.md` — Key Decisions : 4 décisions propriétaire (GPL-3.0, fork ffmpeg-kit, distribution hybride, épinglage versions), Core Value, contraintes
- `.planning/REQUIREMENTS.md` — exigences PROJ-01…03 couvertes par cette phase
- `.planning/research/STACK.md` — versions vérifiées contre les registres (2026-09-04), coordonnées Maven candidats, matrice de compatibilité
- `.planning/research/SUMMARY.md` — synthèse recherche, 3 risques structurants, ordre de construction convergent

### Cahier des charges normatif (prime : `docs/ERRATA-2026-09-05.md`)
- `docs/ERRATA-2026-09-05.md` — **errata normatif, prime sur docs/01–04** : E2 versions & coordonnées exactes, E3 amendements techniques, E4 distribution
- `docs/01-Description.md` §2–§3 — stack imposé, permissions manifest, arborescence normative `com/shortifylocal/ai/` (§3.2), règles MVVM (§3.3)
- `docs/02-UI-UX.md` §1–§2 — design system Soft-Clean : tokens couleurs (§1.1), ombre unique (§1.2), rayons (§1.3), typographie (§1.4), thème clair/sombre (§1.5), RTL (§1.6)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Aucun — **codebase vide** (greenfield confirmé au scout : aucun `.kt`, aucun Gradle, aucun manifest). Seuls existent `.planning/`, `docs/` et l'outillage `.zcode/`.

### Established Patterns
- Aucun pattern de code établi. Les « patterns » normatifs viennent des documents : arborescence Clean Architecture (docs/01 §3.2), règles MVVM (§3.3), design system (docs/02 §1).

### Integration Points
- Aucun point d'intégration existant. Points de raccord futurs à ne pas obstruer en P1 : module `whisper-native/` (Phase 2, prévoir que `settings.gradle.kts` reste extensible), entités Room (Phase 3), workers Hilt (Phase 4).

</code_context>

<specifics>
## Specific Ideas

- Le premier build doit tourner **sans installation manuelle de JDK** grâce au toolchain auto-provisioning (D-02) — c'est aussi le mécanisme qui garantit la reproductibilité « clone frais » du critère d'acceptation 1 du SPEC.
- La bascule de thème P1 est un contrôle simple dans la coquille (pas encore la tuile Paramètres définitive de la Partie 2 §7 qui arrive en Phase 6).
- Vérification scriptée : `adb install -r app-debug.apk` + `adb shell am start -n com.shortifylocal.ai/.MainActivity` sur émulateur puis appareil USB.

</specifics>

<deferred>
## Deferred Ideas

Aucune idée différée — la discussion est restée dans le périmètre de la phase.

</deferred>

---

*Phase: 01-échafaudage-décisions-bloquantes*
*Context gathered: 2026-09-05*
