# Tasks: permisos-camara-fotos

## Phase 1: Infrastructure (new files)

- [ ] 1.1 Create `CameraPermissionGate.kt` at `app/src/main/java/com/spatium/deamon/db/temi/core/CameraPermissionGate.kt` — define sealed `Decision` (Allow, Request, ShowSettings, Deny) and the `decide(granted, shouldShowRationale, previouslyAsked): Decision` stub returning `Allow` (makes tests compile).
- [ ] 1.2 Create `CameraPermissionGateTest.kt` at `app/src/test/java/com/spatium/deamon/db/temi/core/CameraPermissionGateTest.kt` — write all 4 failing RED tests (one per decision branch) using JUnit 4, no Android context.

## Phase 2: Core Logic

- [ ] 2.1 Implement `CameraPermissionGate.decide()` decision matrix in `CameraPermissionGate.kt` — (true,_,_)→Allow; (false,true,_)→Request; (false,false,false)→Request; (false,false,true)→ShowSettings. All 4 unit tests must go GREEN.

## Phase 3: MainActivity Wiring

- [ ] 3.1 Add `pendingFotosLaunch: Boolean = false` private flag and `SharedPreferences("camera_perm")` accessor to `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt`.
- [ ] 3.2 Add `checkCameraOnStart()` private fun in `MainActivity.kt` — reads `checkSelfPermission(CAMERA)`; if DENIED and `asked_once=false` → launch request silently; if DENIED and `asked_once=true` → no-op (no Settings nag at startup). Call from `onCreate` after manager init.
- [ ] 3.3 Replace the `tileFotos` click handler (lines 254-263) in `MainActivity.kt` — call `CameraPermissionGate.decide(...)`, branch: Allow→start MapSelectorActivity; Request→set `pendingFotosLaunch=true` + launch permission request; ShowSettings→show AlertDialog with `ACTION_APPLICATION_DETAILS_SETTINGS`.
- [ ] 3.4 Extend `requestCameraPermission` callback (lines 71-78) in `MainActivity.kt` — persist `asked_once=true` unconditionally; if granted AND `pendingFotosLaunch` → start MapSelectorActivity + clear flag; if denied AND `pendingFotosLaunch` → show Toast + clear flag.

## Phase 4: Verification

- [ ] 4.1 Run `./gradlew test` — all 4 `CameraPermissionGateTest` cases must pass with no new failures.
- [ ] 4.2 Manual smoke (happy path): first install → permission dialog appears at startup; tap Fotos while denied → system dialog fires; tap Fotos after granting → MapSelectorActivity launches.
- [ ] 4.3 Manual smoke (permanent-denial path): revoke + "Don't ask again" → reopen app → no dialog; tap Fotos → AlertDialog with Settings button appears.
- [ ] 4.4 Confirm `PartyActivity.checkCameraPermission()` is untouched — no diff in that file.
