# Archive Report: permisos-camara-fotos

**Change**: permisos-camara-fotos | **Project**: temi-deamon-db | **Archived**: 2026-05-01

---

## Executive Summary

Camera permission gating for the Fotos flow has been successfully implemented, tested, and verified. A pure decision logic class (`CameraPermissionGate`) handles all permission state transitions, while `MainActivity` gates navigation at both app startup and tile-click points. PartyActivity retains its backstop check. The change is complete, passes all 15 spec scenarios, and has zero CRITICAL/WARNING issues.

---

## Artifacts Archived

All artifacts are persisted in Engram with the following topic keys and observation IDs:

| Artifact | Topic Key | ID | Type | Date |
|----------|-----------|-----|------|------|
| Proposal | `sdd/permisos-camara-fotos/proposal` | #728 | architecture | 2026-05-01 18:34:28 |
| Spec | `sdd/permisos-camara-fotos/spec` | #729 | architecture | 2026-05-01 18:35:56 |
| Design | `sdd/permisos-camara-fotos/design` | #730 | architecture | 2026-05-01 18:36:45 |
| Tasks | `sdd/permisos-camara-fotos/tasks` | #731 | architecture | 2026-05-01 18:38:04 |
| Verify Report | `sdd/permisos-camara-fotos/verify-report` | #733 | architecture | 2026-05-01 18:43:55 |

---

## Implementation Summary

### Problem Addressed
The camera permission was being checked too late in the activity transition chain—at `PartyActivity.onCreate`, two screens after tapping Fotos. This caused poor UX on first install and silent failures on permanent denial.

### Solution Delivered

**New Files**:
- `app/src/main/java/com/spatium/deamon/db/temi/core/CameraPermissionGate.kt` — Pure decision logic (sealed `Decision` class, `decide(granted, shouldShowRationale, previouslyAsked)` function). Zero Android imports, fully JUnit-testable.
- `app/src/test/java/com/spatium/deamon/db/temi/core/CameraPermissionGateTest.kt` — 4 JUnit 4 unit tests covering all four decision branches.

**Modified Files**:
- `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt` — Added:
  - `pendingFotosLaunch` flag to track Fotos-triggered permission requests
  - `checkCameraOnStart()` method for app-startup permission check (silent if not yet asked, no nag for permanent denial)
  - Full gating logic in `tileFotos` click handler (Allow → navigate, Request → request + set flag, ShowSettings → AlertDialog with Settings intent)
  - Extended `requestCameraPermission` callback to branch on pending flag and persist `asked_once` state

**Unchanged** (Intentional):
- `PartyActivity.kt` — Remains as defensive backstop
- `AndroidManifest.xml` — CAMERA permission already declared
- QR scanner flow in MainActivity — fully intact

---

## Verification Results

### Build & Tests
- **Build**: PASSED — `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL in 2s
- **Tests**: 4/4 passed, 0 failed, 0 skipped
- **All scenarios**: 15/15 spec scenarios COMPLIANT

### Compliance Details

| Domain | Requirement | Compliance | Evidence |
|--------|-------------|-----------|----------|
| CameraPermissionGate | Decision mapping exhaustive & deterministic | PASS | 4 unit tests all green |
| CameraPermissionGate | No Android imports | PASS | CameraPermissionGate.kt zero imports |
| MainActivity Startup | Permission checked without blocking init | PASS | checkCameraOnStart() called after all manager inits |
| MainActivity Startup | Already granted → no dialog | PASS | S5: MainActivity.kt:198 `if (granted) return` |
| MainActivity Startup | First ask → silent request | PASS | S6: MainActivity.kt:201-203 |
| MainActivity Startup | Permanently denied → no nag | PASS | S7: MainActivity.kt:204 logic + comment |
| Fotos Tile | Granted → launch MapSelectorActivity | PASS | S8: MainActivity.kt:296-303 |
| Fotos Tile | Not granted → request + set flag | PASS | S9: MainActivity.kt:304-308 |
| Fotos Tile | Permanently denied → Settings dialog | PASS | S10: MainActivity.kt:309-321 |
| Callback | Granted + pending → navigate + reset flag | PASS | S11: MainActivity.kt:75-83 |
| Callback | Denied + pending → Toast + reset flag | PASS | S12: MainActivity.kt:84-87 |
| PartyActivity | Backstop unchanged | PASS | N1: No diff in PartyActivity.kt |
| Manifest | No new permissions added | PASS | N2: CAMERA pre-existing, no additions |

---

## Issues Summary

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**: 1
- `Decision.Deny` sealed variant is never produced by `decide()` (dead code). Handled as fallback Toast in tileFotos but unreachable. Benign — document or remove in future refactor.

---

## Files by Location

**New Core Logic**:
- `app/src/main/java/com/spatium/deamon/db/temi/core/CameraPermissionGate.kt`
- `app/src/test/java/com/spatium/deamon/db/temi/core/CameraPermissionGateTest.kt`

**Modified Gateway**:
- `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt`

**Defensive Backstop** (unchanged):
- `app/src/main/java/com/spatium/temibridge/core/PartyActivity.kt`

---

## State Transition (Decision Matrix)

```
decide(granted: Boolean, shouldShowRationale: Boolean, previouslyAsked: Boolean): Decision

granted=true  →  Allow (navigate immediately)
granted=false:
  ├─ shouldShowRationale=true  →  Request (show OS dialog)
  ├─ shouldShowRationale=false:
  │  ├─ previouslyAsked=false  →  Request (first-time ask)
  │  └─ previouslyAsked=true   →  ShowSettings (permanent denial → Settings intent)
```

---

## Design Decisions Locked

1. **Pure Decision Logic**: All permission state logic extracted to `CameraPermissionGate` (zero Android imports) for testability.
2. **Double Gate**: App-startup check (UX) + tile-click check (correctness) + PartyActivity backstop (defense).
3. **SharedPrefs Flag**: `asked_once` flag in `"camera_perm"` prefs disambiguates first-ask from permanent-denial (both return `shouldShowRationale=false` on Android).
4. **Pending Flag**: `pendingFotosLaunch` boolean separates app-startup requests from user-initiated Fotos requests in shared callback.
5. **No Blocking**: Manager initialization (AnnouncementManager, RatingManager, TemiController) proceeds in parallel with permission request — not blocked.

---

## Traceability

To recover full detail on any aspect of this change:

- **Why this approach**: Read Proposal (#728) Intent and Approach sections
- **Exact contracts**: Read Spec (#729) Requirements
- **Implementation decisions**: Read Design (#730) Architecture section
- **Task breakdown**: Read Tasks (#731) all phases
- **Verification evidence**: Read Verify Report (#733) Spec Compliance Matrix

All observations are permanent in Engram. Use `mem_get_observation(id: {ID})` to retrieve full content.

---

## Next Steps

None — the change is complete and closed. All verification passed. The implementation is ready for production deployment on Temi devices running Android 13+ with targetSdk=36.

**For future enhancement**: Consider removing the dead-code `Decision.Deny` branch in a follow-up housekeeping commit if purity is desired.

---

**Archived by**: sdd-archive executor  
**Date**: 2026-05-01  
**Status**: CLOSED
