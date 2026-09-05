---
schema_version: 1
open_count: 1
waived_count: 0
fixed_count: 0
total_count: 1
last_updated: 2026-09-05T17:02:43.794Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 01 | stub | app/src/main/java/com/shortifylocal/ai/MainActivity.kt | 11 | MainActivity coquille minimale (Text app_name) — intentionnel, prescrit par le plan ; remplace par l'ecran reel au plan 01-03 | open |  | 2026-09-05T17:02:43.794Z |  |

````json
[
  {
    "id": 1,
    "kind": "stub",
    "phase": "01",
    "file": "app/src/main/java/com/shortifylocal/ai/MainActivity.kt",
    "line": 11,
    "description": "MainActivity coquille minimale (Text app_name) — intentionnel, prescrit par le plan ; remplace par l'ecran reel au plan 01-03",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-09-05T17:02:43.794Z",
    "resolved_at": null
  }
]
````
