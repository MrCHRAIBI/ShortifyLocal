---
schema_version: 1
open_count: 3
waived_count: 0
fixed_count: 1
total_count: 4
last_updated: 2026-09-05T21:16:55.645Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 01 | stub | app/src/main/java/com/shortifylocal/ai/MainActivity.kt | 11 | MainActivity coquille minimale (Text app_name) — intentionnel, prescrit par le plan ; remplace par l'ecran reel au plan 01-03 | fixed |  | 2026-09-05T17:02:43.794Z | 2026-09-05T21:16:55.645Z |
| 2 | 01 | stub | app/src/main/java/com/shortifylocal/ai/presentation/ui/home/HomeScreen.kt |  | Ecran Accueil vide ASSUME P1 (decision SPEC) — contenu reel Phase 6 (docs/02 §4) | open |  | 2026-09-05T21:14:34.114Z |  |
| 3 | 01 | stub | app/src/main/java/com/shortifylocal/ai/presentation/ui/history/HistoryScreen.kt |  | Ecran Historique vide ASSUME P1 (decision SPEC) — contenu reel Phase 6 (docs/02 §6) | open |  | 2026-09-05T21:14:34.604Z |  |
| 4 | 01 | stub | app/src/main/java/com/shortifylocal/ai/presentation/ui/settings/SettingsScreen.kt |  | Ecran Paramettes P1 reduit a la tuile Apparence/bascule (decision SPEC) — tuiles completes Phase 6 (docs/02 §7) | open |  | 2026-09-05T21:14:35.072Z |  |

````json
[
  {
    "id": 1,
    "kind": "stub",
    "phase": "01",
    "file": "app/src/main/java/com/shortifylocal/ai/MainActivity.kt",
    "line": 11,
    "description": "MainActivity coquille minimale (Text app_name) — intentionnel, prescrit par le plan ; remplace par l'ecran reel au plan 01-03",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-09-05T17:02:43.794Z",
    "resolved_at": "2026-09-05T21:16:55.645Z"
  },
  {
    "id": 2,
    "kind": "stub",
    "phase": "01",
    "file": "app/src/main/java/com/shortifylocal/ai/presentation/ui/home/HomeScreen.kt",
    "line": null,
    "description": "Ecran Accueil vide ASSUME P1 (decision SPEC) — contenu reel Phase 6 (docs/02 §4)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-09-05T21:14:34.114Z",
    "resolved_at": null
  },
  {
    "id": 3,
    "kind": "stub",
    "phase": "01",
    "file": "app/src/main/java/com/shortifylocal/ai/presentation/ui/history/HistoryScreen.kt",
    "line": null,
    "description": "Ecran Historique vide ASSUME P1 (decision SPEC) — contenu reel Phase 6 (docs/02 §6)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-09-05T21:14:34.604Z",
    "resolved_at": null
  },
  {
    "id": 4,
    "kind": "stub",
    "phase": "01",
    "file": "app/src/main/java/com/shortifylocal/ai/presentation/ui/settings/SettingsScreen.kt",
    "line": null,
    "description": "Ecran Paramettes P1 reduit a la tuile Apparence/bascule (decision SPEC) — tuiles completes Phase 6 (docs/02 §7)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-09-05T21:14:35.072Z",
    "resolved_at": null
  }
]
````
