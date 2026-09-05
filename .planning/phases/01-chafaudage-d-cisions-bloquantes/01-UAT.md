---
status: testing
phase: 01-Échafaudage & décisions bloquantes
source: [01-VERIFICATION.md]
started: 2026-09-05T22:20:00Z
updated: 2026-09-05T23:59:00Z
---

## Current Test

number: 2
name: Revue visuelle anti-neumorphisme + fidélité tokens (WR-03 amendé, en attente de confirmation propriétaire)
expected: |
  Amendement WR-03 appliqué (commit 7c5d5a7) : pilule onglet sélectionné terracotta #E4590C + icône blanche en clair ; #FF7A3D + icône sombre en sombre ; libellé actif inchangé (terracotta). Captures device : screens/device-shell.png (clair) + screens/device-after-toggle.png (sombre). Aucun autre écart néumorphisme/tokens. En attente : confirmation visuelle du propriétaire.
awaiting: user response

## Tests

### 1. Lég appareil physique ARM64 (D-03/R7)
expected: « ÉMU OK — install + lancement + 3 onglets + bascule + persistance prouvés sur le téléphone physique ARM64 », sans échec ABI ; en cas de blocage ABI : STOP + escalade propriétaire de spec
result: pass

### 2. Revue visuelle anti-neumorphisme + fidélité tokens (docs/02-UI-UX.md §0/§1.1–1.3)
expected: Aucune ombre double, aucun halo néumorphique ; UNE seule ombre douce verticale par surface (5 % clair / 30 % sombre) ; rayons 32/24/16/28 ; couleurs conformes aux tokens en clair ET en sombre.
result: issue
reported: "Amendement WR-03 (écart visuel confirmé sur captures appareil) : pilule onglet sélectionné = secondaryContainer M3 par défaut (violet) — prescription : mapper secondaryContainer sur AccentActiveDark/Light et onSecondaryContainer sur 0xFF0E0E10/White dans Theme.kt, rebuild + reinstall + 2 captures"
severity: cosmetic

## Summary

total: 2
passed: 1
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-01-02
  truth: "Pilule de l'onglet sélectionné conforme aux tokens Soft-Clean (AccentActive terracotta clair #E4590C / #FF7A3D sombre, icône contrastée), pas le secondaryContainer M3 par défaut (violet)"
  status: failed
  reason: "User reported: Amendement WR-03 — écart visuel confirmé sur captures appareil (pilule violette M3)"
  severity: cosmetic
  test: 2
  root_cause: "WR-03 : slots M3 secondaryContainer/onSecondaryContainer non mappés dans ShortifyLocalTheme (Theme.kt) — leNavigationBarItem utilise le défaut violet du schéma de base"
  artifacts:
    - path: "app/src/main/java/com/shortifylocal/ai/presentation/theme/Theme.kt"
      issue: "base.copy() sans mapping secondaryContainer/onSecondaryContainer"
  missing:
    - "Mapper secondaryContainer = accentActive et onSecondaryContainer = 0xFF0E0E10 (sombre) / White (clair) — FAIT, commit 7c5d5a7, en attente de confirmation propriétaire"
