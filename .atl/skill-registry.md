# Skill Registry — TemiBridge (Temi Deamon DB)

**Generated**: 2026-04-17
**Updated**: 2026-05-07 (sdd-init pass)
**Project**: TemiBridge (Temi Deamon DB)

## User Skills

User-level skills are available in `~/.claude/skills/` and accessible via the Skill tool. Triggers relevant to this project:

| Skill | When it triggers in this codebase |
|-------|-----------------------------------|
| `boosty-framework` | User asks for an app change in the 3-block (Ubicación / Contexto / Objetivo) format. |
| `branch-pr` | Opening a PR for review against `master`. |
| `issue-creation` | Creating a GitHub issue prior to a branch. |
| `judgment-day` | User requests adversarial dual review of a change. |
| `simplify` | Post-implementation review for reuse / efficiency. |
| `sdd-*` (explore / propose / spec / design / tasks / apply / verify / archive / init / new / continue / ff / onboard / archive) | Full SDD workflow. |
| `skill-creator`, `skill-registry`, `find-skills` | Meta-tooling for managing this registry. |
| `update-config`, `keybindings-help`, `fewer-permission-prompts` | Harness configuration. |

NOT applicable here: `motion`, `framer-motion-animations`, `playwright-testing`, `remotion-best-practices`, `dokploy-deploy`, `claude-api`, `ui-ux-pro-max` (web-only stacks).

## Project Conventions

- `agent.md` (project root) — **Project-specific agent configuration**. Covers Supabase MCP integration (`supabase-temi`, project ref `mkakxmjkwcymwosfrwkl`), Announcement Patrol mode architecture, Rating/Evaluation mode architecture, and Guia (Tour) mode architecture. **ALWAYS read before touching Supabase, patrol mode, rating flow, guia flow, or robot control.**

No other project-level convention files (`CLAUDE.md`, `AGENTS.md`, `.cursorrules`, `GEMINI.md`, `copilot-instructions.md`) are present. The user-level `~/.claude/CLAUDE.md` applies (Spanish Rioplatense voseo when user writes Spanish, conventional commits without AI attribution, never auto-build, short answers, ask one question at a time).

## Compact Rules

### Supabase access (from agent.md)
- ALWAYS use the `supabase-temi` MCP server (`mcp9_*` tools) for any DB communication with this project's Supabase.
- Use `mcp9_apply_migration` for DDL, `mcp9_execute_sql` for queries, `mcp9_list_tables` to inspect schema, `mcp9_get_logs` for diagnostics, `mcp9_deploy_edge_function` / `mcp9_list_edge_functions` for Edge Function ops.
- Project ref: `mkakxmjkwcymwosfrwkl`.
- Runtime credentials live in `local.properties` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) and are exposed via BuildConfig — never hardcode keys.
- Read clients via `core/SupabaseClientProvider`, not `createSupabaseClient` from arbitrary call sites.
- Feature flag: `BuildConfig.ENABLE_SUPABASE_WORKER` toggles the realtime worker.

### Announcement Patrol mode (from agent.md)
- Patrol mode shows fullscreen image + TTS loop every 15 s, volume locked at 6, kiosk mode active, robot patrols waypoints at SLOW.
- Activation: external POST to Edge Function `activar-anuncio` → row in `anuncios` table → app polls `anuncio-activo` every 30 s.
- App components: `core/AnnouncementManager.kt` (**now in `com.spatium.deamon.db.temi.core`**, NOT temibridge.core), `ui/AnnouncementActivity.kt`, `core/TemiController.kt` (`patrol()`, `setVolume()`, `setKioskModeOn()`).
- `AnnouncementManager` accepts a `SupabaseGateway` dependency for testability — use `FakeSupabaseGateway` in unit tests.
- Preserve: 30 s polling, 15 s TTS interval, volume 6, SLOW speed, 10 s waypoint dwell, save/restore of original volume + speed on exit.

### Rating / Evaluation mode (from agent.md)
- Robot navigates to a salon **3 minutes before** `hora_fin`, shows 5-star rating, waits **15 minutes**, TTS invitation every **60 s**, then sends rating to external API.
- Activation: external POST to Edge Function `programar-evaluacion` → row in `evaluaciones_programadas` → app polls `evaluacion-pendiente` every 30 s for rows where `hora_llegada <= now()`.
- App components: `core/RatingManager.kt` (polling, navigation, timeout, external POST), `com.spatium.deamon.db.temi.ui.RatingActivity.kt` (5-star UI), `rating.html` (WebView).
- External evaluation API (NOT supabase-temi — different project): `POST https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation` with `{ rating, customer_name, salon, feedback_text, category }`.
- Salon → waypoint mapping (Sala Duarte→`salonduarte`, Enriquillo→`salonenriquillo`, Multimedia→`salonmultimedia`, Quisqueya→`salonquisqueya`, Santo Domingo→`salonsantodomingo`).
- Rating → feedback text mapping: 1=Necesita mejorar, 2=Regular, 3=Bueno, 4=Muy bueno, 5=Excelente servicio.
- During rating mode, back / skip / home are intentionally disabled for the full 15 min window.

### SelfieHunter mode (new — 2026-05-07)
- Robot wanders between selected locations, detects people at 1.5 m, speaks a welcome phrase, enters photo mode, takes a photo via `FotosActivity`, then sends it via WhatsApp.
- State machine in `SelfieHunterActivity`: `WANDERING → SPEAKING → WAITING_TOUCH → PHOTO_MODE`.
- `WanderingController` is the modular controller for wandering logic (navigation, detection, TTS, state). It receives `Robot?` and callbacks; no Android `Context` needed — unit-testable.
- `WanderingController` constants: detection distance 1.5 m, ignore timeout 8 s, photo mode timeout 30 s, speed SLOW (ordinal 1).
- `FotosActivity` handles the actual camera capture flow; result returns to `SelfieHunterActivity` for WhatsApp delivery.
- `MapSelectorActivity` / `PrefixSelectorActivity` / `PhotoPreviewActivity` are support activities for the selfie-hunter flow (map selection, WhatsApp prefix, photo preview/confirm).
- Launch: `SelfieHunterActivity` receives `EXTRA_SELECTED_LOCATIONS` (list of location strings).

### WhatsApp Automation (new — 2026-05-07)
- `WhatsAppAccessibilityService` (package `com.spatium.deamon.db.temi.ui`) automates photo sending via Android Accessibility API.
- Uses `GestureDescription` + multiple UI node-finding strategies for robustness across WhatsApp versions.
- Companion helpers: `WhatsAppAccessibilityLogger`, `WhatsAppClickPerformer`, `WhatsAppNodeFinder`, `WhatsAppConstants`, `WhatsAppSendResult`.
- Service must be declared in `AndroidManifest.xml` with `<accessibility-service>` config. Requires user to enable it in Android Accessibility Settings.
- `WhatsAppConstants` holds UI element IDs for both WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).
- Do NOT call this service directly — it listens to `AccessibilityEvent` broadcasts from the OS.

### Robot control / SDK (from codebase)
- All Temi SDK calls go through `core/TemiController` and the `skills/base/TemiSkill` contract. Do NOT call `Robot.getInstance()` from activities or new code.
- New behaviors → implement as a `TemiSkill` under `skills/impl/`, register in `SkillRegistry`, configure via `SkillConfiguration`, execute through `SkillManager`.
- `BuildConfig.USE_FAKE_ROBOT` exists but is hardcoded `false` in both build types. Lift it before TDD-ing SDK callers.
- Kiosk Mode requires the app to be set as the Kiosk app in Temi Settings.

### Activity packages
- New activities go under `com.spatium.deamon.db.temi.ui.*`.
- Legacy `com.spatium.temibridge.ui.*` is extend-only when modifying existing screens (MainActivity, MenuActivity, PedidosActivity, AnnouncementActivity, etc.).

### Pedidos / orchestration
- Foreground work: `RobotPedidosForegroundService` + `RobotPedidosOrchestrator` + `RobotPedidosWorker`.
- Atomic claim pattern is in place to prevent duplicate processing — preserve it (commit `84914c5`).
- Webhooks via OkHttp from a coroutine scope; payloads serialized with `kotlinx-serialization`.

### Build / project layout
- `buildDir` is intentionally moved to `%USERPROFILE%/TemiDeamonDBBuild/app` to avoid OneDrive file-lock issues on Windows. Do NOT revert to default.
- Lint: `MissingClass` disabled, `abortOnError = false`. Don't add lint suppressions silently — surface them in PR description.

### Package migration (in progress — 2026-05-07)
- `SupabaseGateway` and `OkHttpSupabaseGateway` have been package-renamed to `com.spatium.deamon.db.temi.net` but the physical files still live under `app/src/main/java/com/spatium/temibridge/net/`. Files must eventually be physically moved to match the package declaration.
- All new code should import from `com.spatium.deamon.db.temi.net.*`.

### Git / Workflow
- Conventional commits in Spanish: `feat(scope): ...`, `fix(scope): ...`. Recent style: `feat(rating): ...`, `fix(pedidos): ...`. NO AI attribution / Co-Authored-By.
- Never `git push --force` to `master`.
- Never run `./gradlew assemble*` automatically — user explicitly forbids auto-builds.

### Guia flow / domain layer (from codebase)
- New package `com.spatium.deamon.db.temi.core` holds domain-pure classes: `GuiaState` (sealed), `GuiaPayload`, `GuiaStateMachine`, `GuiaManager`, `RobotGateway` (interface), `DefaultRobotGateway`, `RobotStateSnapshot`, `AnnouncementManager`, `CheckinHandler`.
- `ExclusiveModeArbiter` — process-wide singleton that serializes GUIA / ANNOUNCEMENT / RATING modes. Must be released on every exit path.
- `RobotGateway` is the hexagonal-arch seam for Temi SDK. New robot-coupled features should inject through this interface, not `Robot.getInstance()`.
- `FakeRobotGateway` lives in `test/` and is the approved seam for unit-testing anything that touches robot navigation.
- Supabase functions: `activar-guia`, `finalizar-guia`, `guia-pendiente` — Edge Functions for the guia lifecycle. Migration: `20260427_create_guias.sql`.

### CameraPermissionGate
- `CameraPermissionGate` (`core/`) is a domain-pure `object` (no Android imports) that decides the correct permission action from 3 booleans: `granted`, `shouldShowRationale`, `previouslyAsked`.
- Returns a sealed `Decision`: `Allow | Request | ShowSettings | Deny`. Activities call `decide()` and react; no permission logic lives in Activities.
- `CameraPermissionGateTest` covers all 4 branches with JUnit 4.

### Backwards walk (discovery 2026-04-29)
- Temi SDK 1.136.0 exposes `Robot.goTo(location, backwards = true)` — robot navigates in reverse, keeping screen facing the user.
- `TemiController.goToBackwards(place)` wraps it via reflection with fallback to normal `goTo`.
- Used in `GuiaManager.onUserTappedStart` for the guided navigation to `waypoint_final`.

### MainActivity Kiosk re-assertion
- `MainActivity.onResume()` calls `TemiController.setKioskModeOn(true)` every time it returns to foreground — defensive layer to re-assert Kiosk Mode after any overlay Activity exits.

### Testing (Strict TDD enabled)
- Test runner: `./gradlew test` (unit, JVM) / `./gradlew connectedAndroidTest` (device).
- Coverage: `./gradlew jacocoTestReport` (configured in build.gradle.kts).
- Real domain tests: `GuiaStateMachineTest`, `GuiaPayloadParserTest`, `RobotStateSnapshotTest`, `ExclusiveModeArbiterTest`, `ArbiterIntegrationTest`, `CameraPermissionGateTest`, `AnnouncementManagerTest`, `EvaluacionManagerTest`, `GuiaManagerTest`, `CheckinHandlerTest`, `RobotPedidosWorkerTest`, `UrlConstantTest`.
- Test seams: `FakeRobotGateway` (robot navigation), `FakeSupabaseGateway` (Supabase HTTP calls).
- `FakeSupabaseGateway` implements `SupabaseGateway` with a response queue (`enqueue(JsonElement)`) and call log — use for any manager that takes a `SupabaseGateway` parameter.
- New domain logic ships with JUnit 4 unit tests. Template tests (`ExampleUnitTest`) are legacy noise.
- Robot-coupled code requires a fake/seam — inject via `RobotGateway` interface, not `TemiController` directly.

## Default Standards

When no project-specific rule applies:
- Follow Android/Kotlin idioms (data classes, sealed result types like `SkillResult`, scoped coroutines).
- Use existing patterns: `BaseTemiSkill` → concrete skill → registered in `SkillRegistry`.
- Composition over inheritance.
- Pure logic stays free of `Context` / `Robot` references for testability.
- Small, single-responsibility functions.
- Follow Temi SDK lifecycle patterns (subscribe in `onStart`, unsubscribe in `onStop`).
