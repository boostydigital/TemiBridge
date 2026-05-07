# Spec: permisos-camara-fotos

**Change**: permisos-camara-fotos | **Project**: temi-deamon-db | **Date**: 2026-05-01

---

## Domain A — CameraPermissionGate (pure logic)

### Purpose

Pure decision function that maps permission state to a typed action. Contains no Android side-effects and is fully unit-testable with JUnit 4.

---

### Requirement: Decision mapping is exhaustive and deterministic

`CameraPermissionGate.decide(granted: Boolean, shouldShowRationale: Boolean): Decision` MUST return exactly one of four sealed values: `Proceed`, `RequestPermission`, `ShowRationale`, `OpenSettings`. The same inputs MUST always produce the same output.

#### Scenario: Permission already granted

- GIVEN `granted = true` (any value of `shouldShowRationale`)
- WHEN `decide` is called
- THEN result is `Decision.Proceed`

#### Scenario: Not granted, rationale applicable (first ask or soft denial)

- GIVEN `granted = false` AND `shouldShowRationale = false` AND this is the first-time or second-time ask context
- WHEN `decide` is called with `granted = false, shouldShowRationale = false`
- THEN result is `Decision.RequestPermission`

#### Scenario: Not granted, OS says show rationale (user denied once)

- GIVEN `granted = false` AND `shouldShowRationale = true`
- WHEN `decide` is called
- THEN result is `Decision.ShowRationale`

#### Scenario: Permanently denied (never show rationale again)

- GIVEN `granted = false` AND `shouldShowRationale = false` AND the caller signals permanent denial
- WHEN `decide` is called with the permanent-denial combination
- THEN result is `Decision.OpenSettings`

> Note for design: the distinction between first-ask (`RequestPermission`) and permanent denial (`OpenSettings`) requires a third input. The design phase SHALL define whether this is a boolean flag, a separate method, or a different parameter signature. The spec requires both outcomes exist and be distinguishable.

---

## Domain B — Camera Permission UX (MainActivity)

### Purpose

Rules governing when and how `MainActivity` requests the camera permission and gates navigation to the Fotos flow.

---

### Requirement: Permission checked at app start without blocking initialization

On every `MainActivity.onCreate`, the app MUST check the camera permission status. If not granted, it MUST request permission. This check MUST be conditional — if already granted it MUST NOT re-request. Manager initialization (AnnouncementManager, RatingManager, TemiController) SHALL NOT wait for permission resolution.

#### Scenario: App start — camera already granted

- GIVEN the camera permission is already granted
- WHEN `MainActivity.onCreate` runs
- THEN no permission dialog is shown
- AND manager initialization proceeds normally

#### Scenario: App start — camera not yet granted (first install)

- GIVEN the camera permission has never been requested
- WHEN `MainActivity.onCreate` runs
- THEN the system permission dialog is shown to the user
- AND manager initialization proceeds in parallel (not blocked)

#### Scenario: App start — camera permanently denied

- GIVEN the camera permission was previously permanently denied
- WHEN `MainActivity.onCreate` runs
- THEN the system permission dialog is NOT shown (OS would silently deny)
- AND an `AlertDialog` with a "Go to Settings" action MUST be shown
- AND tapping "Go to Settings" opens `ACTION_APPLICATION_DETAILS_SETTINGS` for the app

---

### Requirement: tileFotos click gates on permission before launching MapSelectorActivity

When the user taps `tileFotos`, `MainActivity` MUST evaluate the current camera permission state before navigating. Navigation to `MapSelectorActivity` MUST only proceed when permission is granted.

#### Scenario: Fotos tap — permission already granted

- GIVEN the camera permission is granted
- WHEN the user taps `tileFotos`
- THEN `MapSelectorActivity` is launched immediately

#### Scenario: Fotos tap — permission not yet granted

- GIVEN the camera permission is not granted AND the OS will show the dialog
- WHEN the user taps `tileFotos`
- THEN the permission request is triggered
- AND navigation to `MapSelectorActivity` does NOT happen yet
- AND a pending-navigation flag is set so the result callback can complete the flow

#### Scenario: Fotos tap — camera permanently denied

- GIVEN the camera permission is permanently denied
- WHEN the user taps `tileFotos`
- THEN an `AlertDialog` with a "Go to Settings" action MUST be shown
- AND `MapSelectorActivity` is NOT launched

---

### Requirement: Permission result callback completes or blocks the pending Fotos flow

When the OS returns a permission result via the existing `requestCameraPermission` launcher, `MainActivity` MUST check if a Fotos navigation was pending and act accordingly.

#### Scenario: Callback — permission granted, Fotos was pending

- GIVEN the user tapped `tileFotos` which triggered a permission request
- WHEN the OS callback returns `granted = true`
- THEN `MapSelectorActivity` is launched
- AND the pending-navigation flag is cleared

#### Scenario: Callback — permission denied, Fotos was pending

- GIVEN the user tapped `tileFotos` which triggered a permission request
- WHEN the OS callback returns `granted = false`
- THEN a Toast MUST be shown informing the user that camera permission is required
- AND `MapSelectorActivity` is NOT launched
- AND the pending-navigation flag is cleared

#### Scenario: Callback — permission granted, no pending navigation (startup request)

- GIVEN `MainActivity.onCreate` triggered the permission request (no tile tap)
- WHEN the OS callback returns `granted = true`
- THEN no navigation is triggered
- AND the app continues normally

---

### Requirement: PartyActivity retains its existing camera check as a defensive backstop

`PartyActivity.checkCameraPermission()` MUST remain unchanged. It acts as a last-resort guard if the upstream gates are bypassed.

#### Scenario: Backstop is not removed

- GIVEN any future code path that reaches `PartyActivity`
- WHEN `onCreate` runs
- THEN `checkCameraPermission()` still executes
- AND its behavior is identical to the behavior before this change
