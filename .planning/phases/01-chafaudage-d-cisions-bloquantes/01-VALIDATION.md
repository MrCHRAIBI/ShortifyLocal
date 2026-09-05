---
phase: "1"
slug: "chafaudage-d-cisions-bloquantes"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-05"
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build checks + grep assertions + adb install/launch (see 01-RESEARCH.md § Validation Architecture) |
| **Config file** | none — Wave 0 installs (gradle wrapper + project scaffold) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug` + grep assertions + `adb install -r` + launch |
| **Estimated runtime** | ~60-180 seconds (first build longer) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleDebug` (or `:help` for config-only tasks)
- **After every plan wave:** Run full suite command + install on emulator
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (à remplir par validate-phase après exécution) | 01 | 1 | PROJ-01 | — | N/A | build | `./gradlew assembleDebug` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Gradle wrapper + settings/build scripts (le scaffold EST le Wave 0 — aucun framework de test externe requis en P1)
- [ ] `.gitattributes` avec `gradlew text eol=lf` (gotcha Windows de la recherche)

*Infrastructure inexistante : le scaffold lui-même crée l'infrastructure de validation P1.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Install + lancement sur appareil physique ARM64 | R7 (SPEC) | Appareil physique non attaché à la session (D-03) — étape manuelle de fin de phase | `adb install -r app-debug.apk` puis lancement sur l'appareil connecté en USB |
| Rendu visuel des tokens Soft-Clean (ombre unique, rayons) | R2 (SPEC) | Jugement visuel contre docs/02 §1.1–1.3 | Capture d'écran émulateur, comparaison clair/sombre |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
