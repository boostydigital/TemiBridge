# Tasks: db-migration

Generated: 2026-05-06  
Delivery strategy: **single-pr** (all robot_* infrastructure in one coherent PR)  
TDD mode: **Strict** (JUnit 4, `./gradlew test`)

Legend:  
`[P]` = parallel-eligible within its phase  
`[S]` = must be sequential (blocks or is blocked)  
`[TEST]` = paired test task

---

## Phase 1 — DB Migrations (spatium-hub)

> All tasks here are parallel-eligible among themselves; they share no dependency on each other.  
> Prerequisite for all subsequent phases.

### 1.1 [P] Create `robot_pedidos` migration
**File**: `supabase/migrations/YYYYMMDDHHMMSS_create_robot_pedidos.sql`  
**Spec ref**: Module 1 — Table Schema  
- Define columns: `id uuid PK`, `secuencia`, `comida`, `say`, `place`, `orden_action`, `realizado BOOLEAN DEFAULT false`, `created_at TIMESTAMPTZ DEFAULT now()`  
- RLS: anon SELECT + UPDATE; service-role INSERT only  
- Unique index on `id` (implicit PK)

### 1.2 [P] Create `robot_guias` migration
**File**: `supabase/migrations/YYYYMMDDHHMMSS_create_robot_guias.sql`  
**Spec ref**: Module 2 — Table Schema and Triggers  
- Define all columns including `estado`, `expires_at`, `finalizado_at`  
- DB trigger: `expires_at = hora_inicio + duracion_horas * interval '1 hour'`  
- RPC `sweep_stale_guias()` that flips expired rows to `expirada`  
- RLS: anon SELECT + UPDATE + INSERT

### 1.3 [P] Create `robot_evaluaciones` migration
**File**: `supabase/migrations/YYYYMMDDHHMMSS_create_robot_evaluaciones.sql`  
**Spec ref**: Module 3 — Table Schema  
- Columns: `id, salon, waypoint, hora_fin, hora_llegada, nombre_reserva, estado, rating, created_at`  
- `estado` CHECK constraint: `programada / en_proceso / completada / cancelada / timeout`  
- RLS: anon all

### 1.4 [P] Create `robot_anuncios` migration
**File**: `supabase/migrations/YYYYMMDDHHMMSS_create_robot_anuncios.sql`  
**Spec ref**: Module 4 — Table Schema  
- Columns: `id, texto TEXT, imagen_url TEXT, duracion_minutos INTEGER, waypoints JSONB, estado TEXT, created_at`  
- `estado` CHECK constraint: `pendiente / activo / completado / cancelado`  
- RLS: anon SELECT + UPDATE + INSERT

### 1.5 [P] Create `robot_invitados` migration
**File**: `supabase/migrations/YYYYMMDDHHMMSS_create_robot_invitados.sql`  
**Spec ref**: Module 5 — Table Schema  
- Columns: `id, guest_id UUID NOT NULL REFERENCES guests(id), status TEXT, check_in_at TIMESTAMPTZ, contact_notified_at TIMESTAMPTZ, created_at`  
- `status` CHECK constraint: `pendiente / bienvenido / menu_abierto / notificado`  
- UNIQUE index on `guest_id`  
- RLS: service-role only (no anon access)

---

## Phase 2 — Edge Functions (spatium-hub)

> Depends on: Phase 1 (tables must exist for SQL calls to resolve).  
> Tasks within this phase are parallel-eligible unless noted.

### 2.1 [P] Migrate `activar-guia` Edge Function
**File**: `supabase/functions/activar-guia/index.ts`  
**Spec ref**: Module 2, Scenario: Guide activation  
- POST, anon auth  
- Validates row is `programada`, flips to `esperando_usuario`, returns guide data  
- Preserve existing CAS logic

### 2.2 [P] Migrate `guia-pendiente` Edge Function
**File**: `supabase/functions/guia-pendiente/index.ts`  
**Spec ref**: Module 2, Scenario: Guide pending claim (CAS)  
- GET, anon auth  
- CAS: `UPDATE estado='guiando' WHERE estado='esperando_usuario'` with `.select()` to verify rows affected  
- Returns row or empty

### 2.3 [P] Migrate `finalizar-guia` Edge Function
**File**: `supabase/functions/finalizar-guia/index.ts`  
**Spec ref**: Module 2, Scenario: Guide finalized  
- POST, anon auth  
- Transitions `guiando` → `completada`, sets `finalizado_at = now()`

### 2.4 [P] Migrate `programar-evaluacion` Edge Function
**File**: `supabase/functions/programar-evaluacion/index.ts`  
**Spec ref**: Module 3, Scenarios: Schedule + Cancel evaluation  
- POST, anon auth  
- `cancel: true` in body → flip `programada` → `cancelada`  
- Otherwise: INSERT new row, return `{ id }`

### 2.5 [P] Migrate `evaluacion-pendiente` Edge Function
**File**: `supabase/functions/evaluacion-pendiente/index.ts`  
**Spec ref**: Module 3, Scenario: Claim evaluation  
- GET, anon auth  
- CAS: `UPDATE estado='en_proceso' WHERE estado='programada' AND hora_fin <= now()` + `.select()`  
- Returns row or empty

### 2.6 [P] Create `robot-sweep-guias` Edge Function (new wrapper)
**File**: `supabase/functions/robot-sweep-guias/index.ts`  
**Spec ref**: Design ADR-5  
- POST, anon auth  
- Calls `sweep_stale_guias()` RPC, returns result

### 2.7 [P] Create `activar-anuncio` Edge Function (new)
**File**: `supabase/functions/activar-anuncio/index.ts`  
**Spec ref**: Module 4, Requirement: activar-anuncio + Scenario: Announcement created  
- POST, anon auth  
- Body: `{ texto, imagen_url, duracion_minutos, waypoints }`  
- INSERT `robot_anuncios` with `estado=pendiente`, return `{ id }`

### 2.8 [P] Create `anuncio-activo` Edge Function (new, CAS)
**File**: `supabase/functions/anuncio-activo/index.ts`  
**Spec ref**: Module 4, Requirement: anuncio-activo + Scenarios  
- GET, anon auth  
- CAS: `UPDATE estado='activo' WHERE id=(oldest pendiente or activo)` + `.select()`  
- Returns row or `{ activo: false }`

### 2.9 [P] Create `robot-crear-pedido` Edge Function (new)
**File**: `supabase/functions/robot-crear-pedido/index.ts`  
**Spec ref**: Module 1, Scenario: Happy path — order created via Edge Function  
- POST, **service-role** auth  
- Body: `{ sequence_id, place? }`  
- INSERT `robot_pedidos` with `realizado=false`, return `{ id }`

### 2.10 [P] Create `robot-invitado-checkin` Edge Function (new, idempotent)
**File**: `supabase/functions/robot-invitado-checkin/index.ts`  
**Spec ref**: Module 5, Scenarios: First check-in + Duplicate scan  
- POST, **service-role** auth  
- Body: `{ guest_id }`  
- No row or `status=pendiente` → INSERT with `ON CONFLICT DO NOTHING` + invoke `send-guest-notification(checked_in)` → return guest + contact names  
- `status >= bienvenido` → return existing row, NO re-notify

### 2.11 [S] Modify `send-guest-notification` — embed QR PNG
**File**: `supabase/functions/send-guest-notification/index.ts`  
**Spec ref**: Module 7, Scenario: Email includes QR  
**Depends on**: 2.10 must be readable to understand call sites  
- Add QR code generation (`npm:qrcode` or `deno.land/x/qrcode`)  
- Encode `mytemi://guest?id={guest_id}` as PNG, base64 embed in Resend HTML  
- Additive change — existing callers must remain unaffected

---

## Phase 3 — Android: URL Consolidation

> Depends on: Phase 1 + 2 (target URLs must be stable).  
> Internal tasks are sequential (each feeds the next).

### 3.1 [S] Wire `local.properties` → `build.gradle.kts` BuildConfig fields
**File**: `app/build.gradle.kts`  
**Spec ref**: Module 6 — Requirement: Single Base URL Constant; Design ADR-2, 3.4  
- Read `supabase.url`, `supabase.anonKey`, `temi.edgeBaseUrl` from `local.properties`  
- Emit `buildConfigField` for `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `TEMI_EDGE_BASE_URL`  
- Ensure `local.properties` is gitignored (verify, don't add if already there)

### 3.2 [S] Update `SupabaseClientProvider` to use `BuildConfig`
**File**: `app/src/main/java/com/spatium/temibridge/core/SupabaseClientProvider.kt`  
**Spec ref**: Module 6 — Scenario: URL constant applied; Design Component Map  
**Depends on**: 3.1  
- Replace hardcoded URL/key strings with `BuildConfig.SUPABASE_URL` + `BuildConfig.SUPABASE_ANON_KEY`

### 3.3 [S][TEST] Test: `UrlConstantTest` — all managers resolve from `BuildConfig`
**File**: `app/src/test/java/com/spatium/UrlConstantTest.kt`  
**Spec ref**: Test Definitions table — UrlConstantTest  
**Depends on**: 3.1, 3.2 (and 4.1 interface must exist)  
- Verify manager constructors accept injected base URL  
- Assert no manager contains a hardcoded `supabase.co` literal

---

## Phase 4 — Android: SupabaseGateway Interface

> Depends on: Phase 3 (BuildConfig fields needed for real impl).  
> Internal tasks are sequential.

### 4.1 [S] Define `SupabaseGateway` interface
**File**: `app/src/main/java/com/spatium/temibridge/net/SupabaseGateway.kt`  
**Spec ref**: Design ADR-8  
```kotlin
interface SupabaseGateway {
    suspend fun post(path: String, body: JsonObject): JsonElement
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement
}
```

### 4.2 [S] Implement `OkHttpSupabaseGateway`
**File**: `app/src/main/java/com/spatium/temibridge/net/OkHttpSupabaseGateway.kt`  
**Spec ref**: Design ADR-8  
**Depends on**: 4.1  
- Constructor: `(baseUrl: String)`, reads from `BuildConfig.TEMI_EDGE_BASE_URL` at call site  
- Wraps existing OkHttp calls from managers (extract, don't duplicate)

### 4.3 [S] Implement `FakeSupabaseGateway` for tests
**File**: `app/src/test/java/com/spatium/FakeSupabaseGateway.kt`  
**Spec ref**: Design ADR-8; Spec Test Definitions  
**Depends on**: 4.1  
- In-memory response queue: `enqueue(path, responseJson)`  
- Records calls for assertion  
- Parallel to `FakeRobotGateway` pattern

---

## Phase 5 — Android: AnnouncementManager

> Depends on: Phase 4 (gateway interface).

### 5.1 [S] Refactor `AnnouncementManager` — inject `SupabaseGateway`
**File**: `app/src/main/java/com/spatium/temibridge/core/AnnouncementManager.kt`  
**Spec ref**: Module 4; Module 6 — URL migration; Design Component Map  
**Depends on**: 4.1, 4.2  
- Replace hardcoded OkHttp calls with `gateway.get("anuncio-activo")`  
- Replace base URL literal with `BuildConfig.TEMI_EDGE_BASE_URL` (via gateway constructor)  
- Preserve patrol + TTS logic exactly

### 5.2 [S][TEST] Test: `AnnouncementManagerTest`
**File**: `app/src/test/java/com/spatium/AnnouncementManagerTest.kt`  
**Spec ref**: Spec Test Definitions — AnnouncementManagerTest; Scenarios: active announcement + no announcement  
**Depends on**: 4.3, 5.1  
- `anuncio-activo` returns row → manager drives patrol  
- `anuncio-activo` returns `{ activo: false }` → manager idles  
- Uses `FakeSupabaseGateway`

---

## Phase 6 — Android: RatingManager

> Depends on: Phase 4 (gateway interface). Parallel with Phase 5.

### 6.1 [S] Refactor `RatingManager` — inject `SupabaseGateway`
**File**: `app/src/main/java/com/spatium/temibridge/core/RatingManager.kt`  
**Spec ref**: Module 3; Module 6 — URL migration  
**Depends on**: 4.1, 4.2  
- Replace OkHttp calls with `gateway.post("programar-evaluacion", ...)` and `gateway.get("evaluacion-pendiente")`  
- Remove base URL literal

### 6.2 [S][TEST] Test: `EvaluacionManagerTest`
**File**: `app/src/test/java/com/spatium/EvaluacionManagerTest.kt`  
**Spec ref**: Spec Test Definitions — EvaluacionManagerTest; Scenarios: Claim + Cancel  
**Depends on**: 4.3, 6.1  
- Claim: `evaluacion-pendiente` returns row → manager flips to `en_proceso`  
- Cancel: `programar-evaluacion` with `cancel:true` → sets `cancelada`  
- Uses `FakeSupabaseGateway`

---

## Phase 7 — Android: GuiaManager

> Depends on: Phase 4 (gateway interface). Parallel with Phases 5 + 6.

### 7.1 [S] Refactor `GuiaManager` — inject `SupabaseGateway`
**File**: `app/src/main/java/com/spatium/temibridge/core/GuiaManager.kt`  
**Spec ref**: Module 2; Module 6 — URL migration  
**Depends on**: 4.1, 4.2  
- Wire `gateway.post("activar-guia")`, `gateway.get("guia-pendiente")`, `gateway.post("finalizar-guia")`, `gateway.post("robot-sweep-guias")`  
- Remove base URL literal

### 7.2 [S][TEST] Test: `GuiaManagerTest`
**File**: `app/src/test/java/com/spatium/GuiaManagerTest.kt`  
**Spec ref**: Spec Test Definitions — GuiaManagerTest; Scenarios: CAS guiando + finalizar  
**Depends on**: 4.3, 7.1  
- CAS flip to `guiando`: concurrent call returns empty → manager backs off  
- `finalizar-guia`: response includes `finalizado_at` → manager records completion  
- Uses `FakeSupabaseGateway`

---

## Phase 8 — Android: QR Guest Check-in Flow

> Depends on: Phase 4 (gateway interface), Phase 3 (URL constant).

### 8.1 [S] Create `CheckinHandler`
**File**: `app/src/main/java/com/spatium/temibridge/core/CheckinHandler.kt`  
**Spec ref**: Module 5, Scenario: Android QR flow  
**Depends on**: 4.1, 4.2  
- Parses `mytemi://guest?id={guest_id}` URI  
- Calls `gateway.post("robot-invitado-checkin", { guest_id })`  
- Returns `CheckinResult(guestName, contactName)` — caller drives TTS + `MenuActivity`

### 8.2 [S] Wire `CheckinHandler` into `MainActivity` deep-link path
**File**: `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt`  
**Spec ref**: Module 5, Scenario: Android QR flow  
**Depends on**: 8.1  
- `onNewIntent` or equivalent: detect `mytemi://` scheme → delegate to `CheckinHandler`  
- On result: speak welcome TTS → open `MenuActivity`  
- No business logic in `MainActivity` — handler owns it

### 8.3 [S][TEST] Test: `CheckinHandlerTest`
**File**: `app/src/test/java/com/spatium/CheckinHandlerTest.kt`  
**Spec ref**: Spec Test Definitions — CheckinHandlerTest; Scenarios: First check-in + Duplicate scan  
**Depends on**: 4.3, 8.1  
- First check-in: no prior row → gateway receives POST, returns names  
- Duplicate scan: `status >= bienvenido` → gateway returns existing, assert notification NOT re-sent  
- Uses `FakeSupabaseGateway`

---

## Phase 9 — spatium-hub: URL Updates

> Depends on: Phase 2 (functions deployed on hub). Parallel with Phases 5–8.

### 9.1 [P] Update `TemiBridgeRepository.ts` base URL
**File**: `src/repositories/TemiBridgeRepository.ts` (spatium-hub)  
**Spec ref**: Module 6 — Requirement: spatium-hub Frontend Update  
- Point base URL at spatium-hub Supabase project  
- No logic change

### 9.2 [P] Update `useEvents.ts` — call `activar-anuncio`
**File**: `src/hooks/useEvents.ts` (spatium-hub)  
**Spec ref**: Module 6 — Requirement: spatium-hub Frontend Update  
- Replace any standalone project call with `activar-anuncio` on spatium-hub  
- Verify existing callers are unaffected (additive only)

---

## Phase 10 — Tests: `RobotPedidosWorkerTest`

> Depends on: Phase 4 (FakeSupabaseGateway available). Parallel with Phases 5–8.

### 10.1 [S][TEST] Test: `RobotPedidosWorkerTest`
**File**: `app/src/test/java/com/spatium/RobotPedidosWorkerTest.kt`  
**Spec ref**: Spec Test Definitions — RobotPedidosWorkerTest; Scenarios: Atomic claim + realizado set  
- Concurrent claim: only one worker processes a row (simulate two workers, assert one gets rows=1)  
- Sequence plays → `realizado=true` before next poll cycle  
- Uses `FakeSupabaseGateway`

---

## Phase 11 — Verification

> Depends on: all previous phases complete.  
> Sequential by nature.

### 11.1 [S] Run `./gradlew spotlessApply`
- Auto-format all changed Kotlin files  
- Fix any style violations before test run

### 11.2 [S] Run `./gradlew test`
**Depends on**: 11.1  
- All 6 test classes must pass  
- Zero failures tolerated before PR

### 11.3 [S] Run `./gradlew jacocoTestReport`
**Depends on**: 11.2  
- Review coverage report for `net/`, `core/` packages  
- Flag any uncovered CAS path for follow-up

### 11.4 [S] Run `./gradlew spotlessCheck`
**Depends on**: 11.1  
- Confirm no lingering style violations after spotlessApply

---

## Dependency Graph Summary

```
Phase 1 (migrations)
  └─► Phase 2 (edge functions)
        ├─► Phase 9 (hub URL updates)       [parallel with Android]
        └─► Phase 3 (Android URL/BuildConfig)
              ├─► Phase 4 (SupabaseGateway interface)
              │     ├─► Phase 5 (AnnouncementManager)  ─┐
              │     ├─► Phase 6 (RatingManager)         ├─► Phase 11 (Verification)
              │     ├─► Phase 7 (GuiaManager)           │
              │     ├─► Phase 8 (CheckinFlow)           │
              │     └─► Phase 10 (PedidosWorkerTest)   ─┘
              └─► Phase 3.3 (UrlConstantTest)
```

---

## Review Workload Forecast

| Metric | Estimate |
|--------|----------|
| SQL migration files | 5 |
| Edge Function files (new/modified) | 11 |
| Android Kotlin files (new/modified) | ~9 |
| Test files | 6 |
| spatium-hub TS files | 2 |
| **Total estimated changed files** | ~33 |
| **400-line budget risk** | **High** |
| Chained PRs recommended | No (single-pr strategy locked by user) |
| Decision needed before apply | No (single-pr accepted, size:exception implied) |

**Delivery**: single-pr with `size:exception` — all robot_* infrastructure ships together for atomic rollback (revert `local.properties` + reflash per ADR-7).
