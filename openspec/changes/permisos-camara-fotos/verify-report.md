# Verify Report: permisos-camara-fotos

**Change**: permisos-camara-fotos
**Project**: temi-deamon-db
**Date**: 2026-05-01
**Mode**: Strict TDD (4 unit tests)
**Verdict**: PASS

---

## Build & Tests Execution

**Build**: PASSED — `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL in 2s
**Tests**: 4 passed / 0 failed / 0 skipped
**Coverage**: Not measured (Android project, JVM unit tests only for pure logic)

---

## Spec Compliance Matrix

| Scenario | Description | Test / Evidence | Result |
|----------|-------------|-----------------|--------|
| S1 | decide(true,*,*) → Allow | `CameraPermissionGateTest > decide when granted returns Allow` PASSED | ✅ COMPLIANT |
| S2 | decide(false,true,*) → Request | `CameraPermissionGateTest > decide when not granted and shouldShowRationale is true returns Request` PASSED | ✅ COMPLIANT |
| S3 | decide(false,false,false) → Request (first ask) | `CameraPermissionGateTest > decide when not granted shouldShowRationale false and not previouslyAsked returns Request` PASSED | ✅ COMPLIANT |
| S4 | decide(false,false,true) → ShowSettings | `CameraPermissionGateTest > decide when not granted shouldShowRationale false and previouslyAsked returns ShowSettings` PASSED | ✅ COMPLIANT |
| S5 | App start — camera already granted → no launcher call | `MainActivity.kt:198` — `if (granted) return` | ✅ COMPLIANT |
| S6 | App start — not granted, asked_once=false → silent launch + write asked_once=true | `MainActivity.kt:201-203` | ✅ COMPLIANT |
| S7 | App start — permanently denied (asked_once=true) → no Settings nag | `MainActivity.kt:204` — no code path after comment | ✅ COMPLIANT |
| S8 | tileFotos granted → disableFaceTracking + MapSelectorActivity | `MainActivity.kt:296-303` | ✅ COMPLIANT |
| S9 | tileFotos not granted → pendingFotosLaunch=true + launch request | `MainActivity.kt:304-308` | ✅ COMPLIANT |
| S10 | tileFotos permanently denied → AlertDialog with Settings intent | `MainActivity.kt:309-321` | ✅ COMPLIANT |
| S11 | Callback granted + pending → MapSelectorActivity + flag reset | `MainActivity.kt:75-83` | ✅ COMPLIANT |
| S12 | Callback denied + pending → Toast + flag reset | `MainActivity.kt:84-87` | ✅ COMPLIANT |
| N1 | PartyActivity unchanged | git diff — no changes | ✅ COMPLIANT |
| N2 | No new permissions in AndroidManifest | CAMERA pre-existing at line 7, no additions | ✅ COMPLIANT |
| N3 | CameraPermissionGate zero Android imports | CameraPermissionGate.kt — zero import statements | ✅ COMPLIANT |

**Compliance summary**: 15/15 scenarios compliant

---

## Design Constraints

| Constraint | Status | Evidence |
|------------|--------|----------|
| CameraPermissionGate pure object, no Android imports | ✅ PASSED | Zero import statements in file |
| pendingFotosLaunch reset in BOTH callback branches | ✅ PASSED | `MainActivity.kt:82` and `:87` |
| checkCameraOnStart() called after all manager inits | ✅ PASSED | Line 129, after guiaManager.startPolling() at line 126 |
| SharedPreferences key "asked_once" in prefs "camera_perm" | ✅ PASSED | Lines 199, 292 |
| QR-scan logic in requestCameraPermission callback intact | ✅ PASSED | Lines 88-93 unchanged |

---

## Issues Found

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**:
- `Decision.Deny` is defined in `CameraPermissionGate` but `decide()` never returns it — dead code. It's handled in `tileFotos` when-block with a Toast fallback (`MainActivity.kt:322-324`). Benign, but creates an unreachable branch. Consider removing or documenting its intended use case.

---

## Verdict: PASS

All 15 spec scenarios compliant. 4/4 unit tests pass. Build successful. All design constraints met.
Zero CRITICAL — zero WARNING — 1 SUGGESTION (dead `Deny` variant).
