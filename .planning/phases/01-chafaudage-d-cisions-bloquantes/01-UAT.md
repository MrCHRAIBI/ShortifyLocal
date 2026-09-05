---
status: testing
phase: 01-Échafaudage & décisions bloquantes
source: [01-VERIFICATION.md]
started: 2026-09-05T22:20:00Z
updated: 2026-09-05T22:20:00Z
---

## Current Test

number: 1
name: Lég appareil physique ARM64 (D-03/R7)
expected: |
  Brancher un téléphone ARM64 sous Android 13+ en USB (débogage USB activé), puis :
  `adb devices` (récupérer le serial), puis :
  `ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh`
  puis : `cp .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/emulator-shell.png .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/device-shell.png`
  Attendu : « ÉMU OK — install + lancement + 3 onglets + bascule + persistance (LIGHT|DARK) prouvés sur <serial> », SANS INSTALL_FAILED_NO_MATCHING_ABIS / INSTALL_FAILED_CPU_ABI_INCOMPATIBLE. En cas de blocage ABI : STOP, escalade au propriétaire de spec, ne rien changer.
awaiting: user response

## Tests

### 1. Lég appareil physique ARM64 (D-03/R7)
expected: « ÉMU OK — install + lancement + 3 onglets + bascule + persistance prouvés sur le téléphone physique ARM64 », sans échec ABI ; en cas de blocage ABI : STOP + escalade propriétaire de spec
result: [pending]

### 2. Revue visuelle anti-neumorphisme + fidélité tokens (docs/02-UI-UX.md §0/§1.1–1.3)
expected: Aucune ombre double, aucun halo néumorphique ; UNE seule ombre douce verticale par surface (5 % clair / 30 % sombre) ; rayons 32/24/16/28 ; couleurs conformes aux tokens en clair ET en sombre. À relire : screens/emulator-shell.png (clair), screens/emulator-after-toggle.png (sombre), device-shell.png (dès le test 1). Points connus à disposer : pilule d'onglet sélectionné = secondaryContainer M3 par défaut (violet, WR-03) ; icônes de barre d'état suivant le thème système (WR-04) — accepter, amender ou programmer la correction.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
