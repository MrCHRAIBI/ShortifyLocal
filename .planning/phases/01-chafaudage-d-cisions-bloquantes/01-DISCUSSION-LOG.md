# Phase 1: Échafaudage & décisions bloquantes - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-05
**Phase:** 1-Échafaudage & décisions bloquantes
**Areas discussed:** Environnement dev local, Appareil physique ARM64, Thème initial, Nom affiché

---

## Environnement dev local

| Option | Description | Selected |
|--------|-------------|----------|
| Tout est prêt | Android Studio (ou SDK CLI) + JDK 17 + au moins un émulateur Android 13+ déjà opérationnels | |
| Partiellement prêt | Une partie existe — préciser ce qui manque | ✓ |
| Rien d'installé | Machine vierge côté Android — le plan inclut l'installation SDK/JDK/émulateur | |

**User's choice:** Partiellement prêt
**Notes:** Relance « quels composants manquent ? » (multiSelect : JDK 17 / Android SDK / Émulateur-AVD / Android Studio) → **JDK 17 uniquement**. Relance « comment provisionner le JDK ? » → **Toolchain auto** (foojay) plutôt qu'installation manuelle.

| Option | Description | Selected |
|--------|-------------|----------|
| Toolchain auto | Gradle télécharge et provisionne JDK 17 via le resolveur foojay — reproductible sur toute machine | ✓ |
| Install manuelle | JDK 17 installé à la main (ex. Temurin), pointé via org.gradle.java.home | |

---

## Appareil physique ARM64

| Option | Description | Selected |
|--------|-------------|----------|
| Oui, Android 13+ | Téléphone ARM64 sous Android 13+ disponible pour les vérifications | ✓ |
| Oui, mais < Android 13 | Appareil présent mais exclu par minSdk 33 | |
| Aucun appareil | Emprunter, acheter, ou adapter le critère R7 | |

| Option | Description | Selected |
|--------|-------------|----------|
| USB | adb via câble — le plus fiable pour install + logs | ✓ |
| WiFi | adb over WiFi (wireless debugging) | |
| USB + WiFi | Les deux selon les cas | |

**User's choice:** Appareil ARM64 Android 13+ disponible · connexion USB
**Notes:** Les vérifications R7 seront scriptées (`adb install -r`, `am start`).

---

## Thème initial

| Option | Description | Selected |
|--------|-------------|----------|
| Suivre le système | Premier lancement synchronisé sur le thème du téléphone ; les deux jeux de tokens existent de toute façon | ✓ |
| Clair par défaut | Toujours démarrer en clair (tokens clairs listés « défaut » §1.1) | |
| Sombre par défaut | Toujours démarrer en sombre | |

**User's choice:** Suivre le système
**Notes:** La bascule utilisateur devient une préférence explicite persistée (DataStore) qui prime ensuite sur le système (D-05).

---

## Nom affiché

| Option | Description | Selected |
|--------|-------------|----------|
| ShortifyLocal AI | Nom produit complet partout | |
| ShortifyLocal | Plus court sous l'icône ; le nom complet reste pour le partage `share_app` et le store | ✓ |

**User's choice:** ShortifyLocal
**Notes:** Chaîne via ressources (`app_name`), jamais en dur (interdit SPEC).

---

## Claude's Discretion

- Mapping tokens Soft-Clean → slots Material 3 (`colorScheme`, shapes) et implémentation de l'ombre unique (D-06)
- Composant de barre de navigation de la coquille P1 (le `BottomNavPill` définitif arrive en Phase 6)
- Structure fine des fichiers Gradle (convention plugins) sous contraintes catalogue unique / zéro version inline / KSP2

## Deferred Ideas

Aucune — la discussion est restée dans le périmètre de la phase.
