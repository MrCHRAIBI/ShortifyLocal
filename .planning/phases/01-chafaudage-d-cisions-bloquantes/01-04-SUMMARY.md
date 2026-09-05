---
phase: 01-chafaudage-d-cisions-bloquantes
plan: 04
subsystem: testing
tags: [verification, e2e, emulator, adb, scripts, uiautomator]

requires:
  - 01-01 (socle build : APK app-debug.apk, catalogue E2, checksum fork consigné)
  - 01-02 (persistance thème DataStore « settings », sémantique D-05)
  - 01-03 (coquille Compose installable : 3 onglets + bascule thème)
provides:
  - scripts/verify_p1.sh — batterie mécanique rejouable (8 interdits SPEC + socle R1/R4/R5/R6, 7 sections)
  - scripts/verify_emulator_p1.sh — cible émulateur paramétrable ANDROID_SERIAL (install/lancement/3 onglets/bascule/persistance/captures)
  - captures d'écran coquille clair+sombre (.planning/.../screens/emulator-*.png)
  - preuve install x86_64 du fork (blocage ABI hérité de STATE.md clos côté émulateur)
  - human-checks consolidés pour l'UAT fin de phase (lég appareil D-03 + revue anti-neumorphisme)
affects: [vérification fin de phase 01, UAT, Phase 2 (spike natif — fork prouvé installable x86_64)]

actuals:
  tokens: 3700 # chars/4 sur le diff réel (14 652 chars, 4 fichiers) — estimation plan 35000
  tasks: 3
  commits: 3 # 2 production + 1 metadata docs

tech-stack:
  added: [] # aucune nouvelle dépendance — outillage bash/adb uniquement
  patterns:
    - Batterie d'acceptation scriptée : chaque interdit SPEC devient un check grep rejouable à échec explicite (ÉCHEC + exit non-zéro, zéro repli silencieux)
    - E2E adb scripté : uiautomator dump comme oracle UI (labels + bounds → input tap), run-as cat du DataStore comme oracle de persistance
    - Script cible unique paramétrable ANDROID_SERIAL — émulateur et appareil physique partagent la même batterie

key-files:
  created:
    - scripts/verify_p1.sh
    - scripts/verify_emulator_p1.sh
    - .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/emulator-shell.png
    - .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/emulator-after-toggle.png
    - .planning/phases/01-chafaudage-d-cisions-bloquantes/01-USER-SETUP.md
  modified: []

key-decisions:
  - "Bascule mécanisée par navigation scriptée : tap sur l'onglet Paramètres (bounds text=) AVANT extraction des bounds de la bascule (content-desc) — la bascule vit sur l'écran Paramètres, pas dans le dump de l'écran Accueil"
  - "Attente de coquille affichée (boucle dump bornée) au lieu d'un somme fixe — le screencap à 3 s attrapait le splash system au cold start de l'émulateur"
  - "device-shell.png non créé (aucun appareil physique attaché à l'exécution) — la lég est automatisée à une commande près via ANDROID_SERIAL et routée à l'UAT (fallback prévu par le plan)"

patterns-established:
  - "Pattern: tout interdit SPEC est prouvé par un check rejouable dans verify_p1.sh (7 sections, échec au premier KO)"
  - "Pattern: preuve de persistance = pidof après relance à froid + grep -a LIGHT|DARK dans files/datastore/settings.preferences_pb (run-as)"
  - "Pattern: les scripts de vérification impriment ÉCHEC: <check> + exit non-zéro au premier KO — jamais de repli silencieux"

requirements-completed: [PROJ-01]

coverage:
  - id: D1
    description: "Batterie mécanique verify_p1.sh — 8 interdits SPEC + pureté domain + manifest + 21 versions E2 + SHA-256 + absence tranche Room (décision propriétaire B)"
    verification:
      - kind: integration
        ref: "bash scripts/verify_p1.sh → P1 OK, 7/7 sections, exit 0 ; test inverse (kapt injecté) → ÉCHEC exit 1, puis annulé"
        status: pass
    human_judgment: false
  - id: D2
    description: "Cible émulateur : install (sonde ABI fork), lancement sans crash, 3 onglets au dump uiautomator, bascule, force-stop → relance → mode persisté"
    verification:
      - kind: e2e
        ref: "bash scripts/verify_emulator_p1.sh → ÉMU OK (run4) : install Success, pid 11250/11399, DARK→LIGHT persisté (LIGHT dans settings.preferences_pb)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Captures d'écran coquille (thème effectif) et après bascule (thème inversé)"
    verification:
      - kind: automated_ui
        ref: "screens/emulator-shell.png (sombre, 3 onglets, Accueil) + screens/emulator-after-toggle.png (clair, Paramètres, icône soleil) — relues par l'exécuteur : changement de thème visuellement confirmé"
        status: pass
    human_judgment: false
  - id: D4
    description: "Revue visuelle anti-neumorphisme (une seule ombre douce verticale par surface, rayons 32/24/16/28, docs/02 §0/§1.2)"
    verification:
      - kind: automated_ui
        ref: "pré-lecture exécuteur des captures : aucune ombre double clair+sombre, aucun relief de la couleur du fond, pastille M3 standard sur NavigationBar"
        status: pass
    human_judgment: true
    rationale: "Le jugement de fidélité visuelle finale appartient à l'humain (interdit neumorphisme = judgment dans la SPEC) — consolidé dans l'UAT fin de phase avec les captures D3 comme appui."
  - id: D5
    description: "Lég appareil physique ARM64 (D-03) : install + 3 onglets + persistance sur téléphone, screens/device-shell.png"
    verification: []
    human_judgment: true
    rationale: "Aucun appareil physique attaché à l'exécution (adb devices : émulateur seul) — fallback prévu par le plan : lég consignée « en attente utilisateur » dans 01-USER-SETUP.md, automatisée à une commande près (ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh) ; routée à l'UAT fin de phase."

duration: 17 min
completed: 2026-09-05
status: complete
---

# Phase 01 Plan 04: Vérification E2 de la Phase 1 — batterie mécanique + cible émulateur Summary

Batterie statique rejouable (`verify_p1.sh`, 7 sections vertes, test inverse validé) + E2E scripté sur émulateur (`verify_emulator_p1.sh` : install x86_64 du fork — blocage ABI hérité clos —, lancement, 3 onglets au dump uiautomator, bascule DARK→LIGHT, persistance prouvée après force-stop/relance) + captures d'écran d'appui ; lég appareil physique ARM64 et revue anti-neumorphisme consolidées pour l'UAT.

## Performance
- **Duration :** 17 min (21:21 – 21:38 UTC, 2026-09-05)
- **Started :** 2026-09-05T21:21:56Z
- **Completed :** 2026-09-05T21:38:xxZ
- **Tasks :** 3/3
- **Files :** 5 créés (2 scripts, 2 captures, 1 USER-SETUP), 0 modifiés

## Accomplishments
- **Task 1** (`6257078`) — `scripts/verify_p1.sh` : les 8 interdits de la SPEC et les checks mécaniques R1/R4/R5/R6 rejouables en une commande, 7 sections ordonnées (interdits build, interdits UI, pureté domain, manifest, 21 versions E2 + SHA-256, absence tranche Room P1 — décision propriétaire B, checksum AAR fork 8.1.7). Test inverse validé : violation `kapt` injectée → `ÉCHEC` + exit 1, annulée, batterie verte.
- **Task 2** (`01d5e69`) — `scripts/verify_emulator_p1.sh` + captures : démarrage AVD auto borné (~180 s), `adb install -r` Success sur `sdk_gphone64_x86_64` (sonde ABI du fork — voir Issues), lancement sans crash, preuve mécanique des 3 onglets (uiautomator dump), bascule par bounds `content-desc` → tap, force-stop → relance à froid → `run-as` cat du DataStore contenant le mode choisi. Run final : ÉMU OK, DARK→LIGHT, pid vivant après relance, 2 captures non vides relues visuellement (sombre Accueil → clair Paramètres, icône soleil/lune conforme au mode).
- **Task 3** (pas de commit — aucun fichier produit, fallback prévu) — aucun appareil physique attaché (`adb devices` : émulateur seul) : lég consignée « en attente utilisateur » avec commande exacte dans `01-USER-SETUP.md` (status Incomplete) ; `device-shell.png` non créé (fichier conditionnel) ; batterie statique repassée verte après tout changement ; human-checks consignés pour l'UAT (voir ci-dessous).

## Task Commits
1. **Task 1** - `6257078` (feat) — batterie mécanique verify_p1.sh (8 interdits + socle)
2. **Task 2** - `01d5e69` (feat) — cible émulateur verify_emulator_p1.sh + captures
3. **Task 3** - (aucun commit — aucun fichier ; résultats consignés dans ce SUMMARY + 01-USER-SETUP.md)
**Plan metadata:** commit docs (voir § suivant)

## Human-checks consolidés (pour l'UAT fin de phase)
1. **Lég appareil (D-03)** : sur le téléphone ARM64 Android 13+ connecté en USB — `adb install -r app/build/outputs/apk/debug/app-debug.apk` puis `adb shell am start -n com.shortifylocal.ai/.MainActivity` réussissent ; les 3 onglets Accueil/Historique/Paramètres naviguent ; bascule de thème → force-stop → relance : le thème restauré est le dernier choisi ; la relance à froid redémarre proprement. Commande scriptée fournie : `ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh`.
2. **Revue visuelle (interdit neumorphisme)** : les captures émulateur (screens/emulator-shell.png sombre + emulator-after-toggle.png clair, et device-shell.png dès que la lég appareil est passée) doivent montrer une seule ombre douce verticale par surface, aucun relief néumorphique (pas d'ombre double clair+sombre, aucun élément de la couleur du fond en relief), rayons 32/24/16/28 conformes aux tokens docs/02 §1.1–1.3 en clair ET en sombre. Pré-lecture exécuteur : conforme sur les 2 captures émulateur (ombre unique sous la NavigationBar, pastille M3 standard, aucun halo double) — le jugement final reste humain.

## Files Created/Modified
- Créés : `scripts/verify_p1.sh`, `scripts/verify_emulator_p1.sh`, `screens/emulator-shell.png`, `screens/emulator-after-toggle.png`, `01-USER-SETUP.md`
- Non créés (conditionnel) : `screens/device-shell.png` — aucun appareil physique attaché

## Decisions Made
- Bascule mécanisée en deux taps scriptés (onglet Paramètres d'abord, bascule ensuite) — le dump uiautomator ne voit que l'écran affiché.
- Attente de coquille par boucle dump bornée (~30 s) au lieu du somme fixe de 3 s (anti-splash).
- Task 3 sans commit : le plan prévoit explicitement le fallback « consigner et ne pas bloquer » ; aucun fichier ne change sans appareil.

## Deviations from Plan

1. **[Rule 3 - Blocker] Mangling MSYS des chemins distants adb (Git Bash)**
   - **Found during:** Task 2 (run 1 — `adb pull /sdcard/...` → `C:/Program Files/Git/sdcard/...`).
   - **Issue:** Git Bash réécrit les arguments commençant par `/` en chemins Windows — le pull du dump uiautomator échoue systématiquement sur cette machine.
   - **Fix:** `export MSYS_NO_PATHCONV=1` dans le script (inerte hors MSYS) + check de contenu (`<hierarchy`) après pull.
   - **Files modified:** scripts/verify_emulator_p1.sh
   - **Verification:** run 2 — pull OK, 3 onglets trouvés.
   - **Commit:** `01d5e69`

2. **[Rule 3 - Blocker] La bascule n'est pas dans le dump du premier écran + mort silencieuse de set -e**
   - **Found during:** Task 2 (run 2 — exit 1 sans ÉCHEC après « 3 onglets affichés »).
   - **Issue:** le plan extrait les bounds de la bascule « du même dump », mais ce dump est celui de l'écran Accueil — la bascule vit sur Paramètres ; l'affectation `$(grep -o …)` sans match tuait en plus le script sous `set -e -o pipefail` sans message.
   - **Fix:** étape de navigation scriptée (bounds de `text="Paramètres"` → tap → re-dump) avant l'extraction ; helper `bounds_de` avec diagnostics explicites (cat du dump + ÉCHEC nommé) au lieu de la mort silencieuse.
   - **Files modified:** scripts/verify_emulator_p1.sh
   - **Verification:** run 3 — bascule tapée (850,1153), persistance DARK prouvée, ÉMU OK exit 0.
   - **Commit:** `01d5e69`

3. **[Rule 1 - Bug] Le screencap attrapait le splash system (somme fixe 3 s)**
   - **Found during:** Task 2 (revue visuelle des captures — emulator-shell.png montrait l'icône launcher sur fond clair, pas la coquille).
   - **Issue:** au cold start de l'émulateur la coquille n'est pas encore composée à 3 s ; la capture « shell » ne prouvait rien visuellement.
   - **Fix:** boucle bornée (~30 s) qui attend `Accueil` dans le dump uiautomator avant le screencap.
   - **Files modified:** scripts/verify_emulator_p1.sh
   - **Verification:** run 4 — capture shell montre la coquille 3 onglets (sombre, restaurée du run précédent) ; ÉMU OK exit 0.
   - **Commit:** `01d5e69`

**Total deviations:** 3 auto-fixed (2 Rule 3 bloqueurs script, 1 Rule 1 bug timing). **Impact:** aucune dérive de périmètre — le comportement prescrit (install → lancement → 3 onglets → bascule → persistance) est livré intégralement et rejouable ; les fixes durcissent la robustesse (T-01-11) sans changer la séquence.

## Issues Encountered
- **Disponibilité x86_64 du fork (concern ouvert de STATE.md) : CLOS positivement.** `adb install -r` sur `sdk_gphone64_x86_64` (API 36, émulateur) retourne `Success` — l'ABI du fork n'est pas un blocage à l'install (le catalogue annonce arm64-v8a + x86_64 dans l'AAR 8.1.7 ; les .so ne sont jamais chargés en P1). Reste la moitié physique (D-03) pour sceller la double cible à l'UAT.
- Émulateur/AVD disponibles et utilisés : `Medium_Phone_2` (boot ~40 s). Aucun stall.
- Warning uiautomator transient « null root node returned by UiTestAutomationBridge » sur un dump — absorbé par la boucle bornée ; dump suivant valide.
- Aucune auth gate.

## Known Stubs
Aucun. (Les scripts sont câblés au réel : greps, adb, checksum ; `device-shell.png` est un livrable conditionnel non produit, tracé dans 01-USER-SETUP.md — pas un stub de code.)

## User Setup Required
Voir `.planning/phases/01-chafaudage-d-cisions-bloquantes/01-USER-SETUP.md` (Incomplete) — brancher le téléphone ARM64 Android 13+ en USB (débogage USB) puis lancer `ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh`. Les human-checks (lég appareil + revue anti-neumorphisme) sont consolidés pour l'UAT fin de phase.

## Next Phase Readiness
Phase 1 scellée côté automatisable : socle build épinglé et prouvé checksum, coquille 3 onglets installée/lancée/naviguée sur émulateur, thème persistant au force-stop, 8 interdits prouvés absents par checks rejouables. Prêt pour la vérification fin de phase (UAT : lég appareil D-03 + revue visuelle) puis Phase 2 (spike natif — le fork est prouvé installable x86_64 ; l'ABI arm64 du téléphone reste à confirmer à la lég).

## Self-Check: PASSED

- Fichiers créés vérifiés présents : scripts/verify_p1.sh, scripts/verify_emulator_p1.sh, screens/emulator-shell.png (non vide), screens/emulator-after-toggle.png (non vide), 01-USER-SETUP.md.
- Commits vérifiés dans l'historique : `6257078` (Task 1), `01d5e69` (Task 2).
- `bash scripts/verify_p1.sh` vert (P1 OK, exit 0) après tout changement ; `bash scripts/verify_emulator_p1.sh` vert (ÉMU OK, exit 0, run 4).
