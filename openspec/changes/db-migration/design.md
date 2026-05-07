# Technical Design: db-migration

## 1. Architecture Overview

**Pattern**: Layered + Gateway/Adapter + CAS-on-DB. The Android app stays as a thin client that polls/commits via two transports:

1. **supabase-kt 2.4.2** for direct table reads/writes against `robot_*` tables (anon key, RLS-restricted).
2. **OkHttp + JSON** for Edge Function calls (privileged work, side-effects, fanout).

The hub side (Deno/TypeScript Edge Functions in spatium-hub) owns:
- All inserts that must run as service-role.
- Side-effects (Resend, Telegram, Make.com webhooks, QR generation).
- Idempotency and CAS guards that must NOT depend on the client's good behavior.

```
┌────────────────────────────┐    polling SELECT/UPDATE (anon)     ┌────────────────────────────┐
│  Android (TemiBridge APK)  │ ──────────────────────────────────► │  spatium-hub Supabase      │
│  - Managers + Workers      │                                     │  - robot_* tables (RLS)    │
│  - Gateways (Fake-able)    │ ── HTTPS POST/GET (anon|svc role) ─►│  - Edge Functions          │
└────────────────────────────┘                                     │  - guests/events/waypoints │
        ▲                                                          └─────────────┬──────────────┘
        │ deep link mytemi://                                                    │
        │                                                            send-guest-notification
   QR scan                                                            (Resend + QR PNG)
```

The robot is a **dumb consumer**: it polls, claims via CAS, executes, and acknowledges. All authority lives in Edge Functions and DB constraints — the device never trusts itself with state machine transitions that could double-fire.

## 2. Component Map

### 2.1 Android (`app/src/main/java/com/spatium/temibridge/`)

| Component | Responsibility | Transport |
|---|---|---|
| `SupabaseClientProvider` | Lazy singleton holding supabase-kt client. Reads URL+anon key from `BuildConfig`. | supabase-kt |
| `RobotPedidosWorker` | Polls `robot_pedidos` every 2s, atomic claim via CAS UPDATE, plays sequence, opens `MenuActivity`. | supabase-kt |
| `AnnouncementManager` | Polls `anuncio-activo` Edge Function. Drives patrol mode + TTS loop. | OkHttp |
| `RatingManager` | Calls `programar-evaluacion`, `evaluacion-pendiente`. | OkHttp |
| `GuiaManager` | Calls `activar-guia` (web), `guia-pendiente`, `finalizar-guia`, `sweep_stale_guias` (via wrapper). | OkHttp |
| `CheckinHandler` (new, in `MainActivity` deep link path) | Parses `mytemi://guest?id=…`, calls `robot-invitado-checkin`, drives welcome TTS → `MenuActivity`. | OkHttp |
| **`SupabaseGateway` (new interface)** | All Edge Function HTTP calls flow through this seam. Real impl uses OkHttp; `FakeSupabaseGateway` impl drives unit tests. | — |

### 2.2 spatium-hub Edge Functions (`supabase/functions/`)

| Function | Method | Auth | Migrated / New |
|---|---|---|---|
| `activar-guia` | POST | anon | migrated verbatim |
| `guia-pendiente` | GET | anon | migrated, CAS preserved |
| `finalizar-guia` | POST | anon | migrated |
| `programar-evaluacion` | POST | anon | migrated |
| `evaluacion-pendiente` | GET | anon | migrated |
| `robot-sweep-guias` | POST | anon | **new wrapper** around `sweep_stale_guias()` RPC |
| `activar-anuncio` | POST | anon | **new** |
| `anuncio-activo` | GET | anon | **new**, CAS flip pendiente→activo |
| `robot-crear-pedido` | POST | service-role | **new** (used by Make.com webhook) |
| `robot-invitado-checkin` | POST | service-role | **new**, idempotent, fans out to `send-guest-notification` |
| `send-guest-notification` | POST | service-role | **modified** to embed QR PNG |

### 2.3 spatium-hub Frontend

| File | Change |
|---|---|
| `src/repositories/TemiBridgeRepository.ts` | Base URL → spatium-hub project URL constant |
| `src/hooks/useEvents.ts` | Calls newly-existing `activar-anuncio` |

## 3. Data Flow & Sequence Diagrams

### 3.1 Camera-detected order → menu → Telegram

```
Camera (Make.com) ──► robot-crear-pedido ──► INSERT robot_pedidos (realizado=false)
                                                       │
RobotPedidosWorker (poll 2s) ◄─── SELECT realizado=false
                │
                └── UPDATE SET realizado=true WHERE id=X AND realizado=false  ◄── CAS
                       │
                       ├─ rows=1 → claim wins → play sequence → open MenuActivity
                       └─ rows=0 → already claimed → skip

MenuActivity (user interacts) ──► Make.com webhook ──► Telegram
```

### 3.2 QR scan → robot-invitado-checkin → greet → menu → contact email

```
Guest creation (web) ──► send-guest-notification ──► Resend email w/ embedded QR PNG
                                                     (qrcode lib encodes mytemi://guest?id=UUID)

Guest arrives → Temi scans QR → MainActivity onNewIntent("mytemi://guest?id=UUID")
                                       │
                                       └─► CheckinHandler ──► robot-invitado-checkin POST {guest_id}
                                                                       │
                                                                       ├─ no row OR status=pendiente
                                                                       │     ├─ INSERT/UPDATE robot_invitados (status=bienvenido)
                                                                       │     ├─ invoke send-guest-notification (type=checked_in)
                                                                       │     └─ return {guest_name, contact_name}
                                                                       └─ status≥bienvenido → return existing row, NO re-notify
                                                                                  │
                          robot speaks welcome TTS ◄─────────────────────────────┘
                                       │
                                       └─► open MenuActivity
```

### 3.3 Event announcement → activar-anuncio → patrol mode

```
spatium-hub web (event create) ──► activar-anuncio POST ──► INSERT robot_anuncios (estado=pendiente)

AnnouncementManager (poll) ──► anuncio-activo GET
                                       │
                                       └── UPDATE estado='activo' WHERE id=oldest AND estado IN ('pendiente','activo')
                                              │
                                              ├─ row → return announcement → robot enters patrol+TTS loop
                                              └─ none → {activo:false} → idle
```

### 3.4 URL build-time injection

```
local.properties (gitignored, per-device)
   ├─ supabase.url=https://<hub>.supabase.co
   ├─ supabase.anonKey=ey…
   └─ temi.edgeBaseUrl=https://<hub>.supabase.co/functions/v1
                                       │
                                       ▼
build.gradle.kts (defaultConfig)
   buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
   buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
   buildConfigField("String", "TEMI_EDGE_BASE_URL", "\"$temiEdgeBaseUrl\"")
                                       │
                                       ▼
SupabaseClientProvider (URL + key)
AnnouncementManager / RatingManager / GuiaManager / CheckinHandler (TEMI_EDGE_BASE_URL)
```

## 4. ADRs (Architecture Decision Records)

### ADR-1: Per-table RLS posture

**Decision**:

| Table | anon SELECT | anon UPDATE | anon INSERT | service-role |
|---|---|---|---|---|
| `robot_pedidos` | yes | yes | **no** | INSERT only (via `robot-crear-pedido`) |
| `robot_guias` | yes | yes | yes (web `activar-guia`) | unrestricted |
| `robot_evaluaciones` | yes | yes | yes | unrestricted |
| `robot_anuncios` | yes (robot poll) | yes (CAS flip) | yes (web) | unrestricted |
| `robot_invitados` | **no** | **no** | **no** | unrestricted (only `robot-invitado-checkin`) |

**Why**: Pedidos must be tamper-proof at the source — only Make.com via service-role can create them, otherwise anyone with the anon key (the APK ships with one) could spam orders. Invitados is sensitive PII linked to `guests`, locked behind the Edge Function so the only way to create a check-in is via signed deep link → service-role function. Everything else mirrors the existing standalone posture (anon polling + CAS) — this is a relocation, not a security redesign.

**Rejected**:
- "anon INSERT on robot_pedidos" — fails the tamper requirement.
- "service-role for everything" — would require the APK to ship a service-role key (catastrophic if extracted from the device).
- "Per-device JWT auth" — proper solution but out of scope per proposal; tracked separately.

### ADR-2: Single `BuildConfig.TEMI_EDGE_BASE_URL`

**Decision**: Three managers (`AnnouncementManager`, `RatingManager`, `GuiaManager`) and the new `CheckinHandler` read their Edge Function base URL from `BuildConfig.TEMI_EDGE_BASE_URL`, injected from `local.properties` at build time. supabase-kt URL+key remain in `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY` as today.

**Why**: Today every manager hardcodes the project URL — a single environment swap touches 4 files. With the constant, only `local.properties` changes per device/env. `local.properties` is already gitignored, so no secret leaks.

**Rejected**:
- "Hardcode in code, use Gradle product flavors per env" — overkill for a single-device-per-sede deployment.
- "Read URL at runtime from a remote config" — circular dependency (need URL to fetch URL).

### ADR-3: CAS-on-DB for all single-claim transitions

**Decision**: Every "exactly-once" transition is implemented as `UPDATE … WHERE id=? AND estado=<expected>` with `.select()` to return affected rows. Empty result = lost the race.

| Transition | SQL shape |
|---|---|
| Pedido claim | `UPDATE robot_pedidos SET realizado=true WHERE id=? AND realizado=false RETURNING *` |
| Anuncio activate | `UPDATE robot_anuncios SET estado='activo' WHERE id=? AND estado IN ('pendiente','activo') RETURNING *` |
| Guia waiting→guiando | `UPDATE robot_guias SET estado='guiando' WHERE id=? AND estado='esperando_usuario' RETURNING *` |
| Evaluacion claim | `UPDATE robot_evaluaciones SET estado='en_proceso' WHERE id=? AND estado='programada' RETURNING *` |

**Why**: Postgres row-level locking via conditional UPDATE is atomic and lock-free for the client. No advisory locks, no SELECT FOR UPDATE round-trips, no race window between read and write. Works identically from supabase-kt and from Edge Functions.

**Rejected**:
- "SELECT then UPDATE" — classic TOCTOU race; the standalone implementation already uses CAS.
- "DB triggers for state transitions" — pushes business logic into SQL, harder to test, harder to evolve.

### ADR-4: `robot-invitado-checkin` idempotency

**Decision**: On POST `{guest_id}`:

```
SELECT * FROM robot_invitados WHERE guest_id = $1
  ├─ no row → INSERT (status='bienvenido', check_in_at=now()) → invoke send-guest-notification → return names
  ├─ row.status = 'pendiente' → UPDATE (status='bienvenido', check_in_at=now()) → invoke send-guest-notification → return names
  └─ row.status IN ('bienvenido','menu_abierto','notificado') → return existing row, DO NOT invoke send-guest-notification
```

**Why**: Guests will scan the QR multiple times (curiosity, accidental re-scans, multiple greeters). Telegram/email blasts on every scan would be a disaster. The terminal status is the dedupe key.

**Rejected**:
- "Time-window dedupe (e.g. 10 min)" — fragile; what about a guest who comes back after lunch? `status` is the truth.
- "Client-side dedupe in the APK" — server is the only authoritative source; never trust the client.

### ADR-5: Wrap `sweep_stale_guias` RPC in an Edge Function

**Decision**: Create `robot-sweep-guias` Edge Function that calls the `sweep_stale_guias()` RPC internally. `GuiaManager` calls the Edge Function, not the REST RPC endpoint directly.

**Why**: With URL consolidation, exposing `…/rest/v1/rpc/sweep_stale_guias` directly to the robot would force the manager to know about both the Edge Functions base URL and the REST base URL — two different paths off the same project. Wrapping it makes every manager call the same `$TEMI_EDGE_BASE_URL/<function>` shape. Also gives us a place to add auth/logging later without touching the APK.

**Rejected**:
- "Direct REST RPC call" — leaks the REST surface to the device; inconsistent with the rest of the manager calls.
- "Move sweep logic into TypeScript" — needless rewrite; the SQL is correct as-is.

### ADR-6: QR PNG generation in `send-guest-notification`

**Decision**: Use `https://deno.land/x/qrcode` (or the `qrcode` npm package via `npm:` import) to render `mytemi://guest?id={guest_id}` as a PNG, base64-embed it in the Resend HTML email as `<img src="data:image/png;base64,…">`.

**Why**: Email clients render data-URI images reliably (Gmail, Outlook, Apple Mail tested patterns). Avoids needing a public URL for the QR (no Storage bucket, no CDN, no signed URL expiration to manage). One self-contained email.

**Rejected**:
- "Generate QR client-side in spatium-hub web and POST it" — couples web app to email contents; harder to retry.
- "Public Supabase Storage URL" — requires bucket + access policy + lifecycle; overengineered for an inline image.

### ADR-7: Deployment ordering (zero-downtime cutover)

**Sequence** (must run in this order):

1. **Hub migrations**: create `robot_*` tables + RLS + triggers + `sweep_stale_guias()` in spatium-hub.
2. **Hub Edge Functions**: deploy all migrated + new functions.
3. **spatium-hub web**: ship `TemiBridgeRepository.ts` URL update + `useEvents.ts` `activar-anuncio` wiring.
4. **Android APK**: bump `local.properties` on the device, build + install new APK.
5. **Standalone Supabase**: keep warm one full release cycle for rollback. Decommission via separate ticket.

**Why this order**: Backend must be ready before any client can call it. Web can flip before the APK because the web only writes (creates announcements/guides); robot reads them. APK flip is the last reversible step. Standalone-warm gives us a 30-second rollback (revert `local.properties`, re-flash) for the entire release window.

**Rejected**:
- "Dual-write from web during transition" — write volume is tiny, so the complexity buys nothing.
- "Feature-flag the URL in the APK" — robot device fleet is single-digit; redeploying is faster than building a flag system.

### ADR-8: Test architecture — `SupabaseGateway` seam

**Decision**: Introduce a `SupabaseGateway` interface that all OkHttp Edge Function calls flow through. Real impl: `OkHttpSupabaseGateway`. Test impl: `FakeSupabaseGateway` (records calls, returns canned responses). Sits next to existing `FakeRobotGateway`.

```kotlin
interface SupabaseGateway {
    suspend fun post(path: String, body: JsonObject): JsonElement
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement
}
```

Managers receive a `SupabaseGateway` via constructor (not a hardcoded OkHttp client). In production, `Application.onCreate` wires `OkHttpSupabaseGateway($TEMI_EDGE_BASE_URL)`. In tests, `FakeSupabaseGateway` is injected directly.

**Why**: Strict TDD mode is active. Hitting real Supabase in unit tests is not viable (network, auth, side-effects, flake). The seam matches the existing `FakeRobotGateway` pattern — same idiom, same place in the codebase. Tests for CAS behavior, idempotency, URL construction, polling logic all run against the fake.

**Rejected**:
- "MockWebServer" — heavier than needed; we don't care about HTTP wire format, we care about request/response semantics.
- "Mock OkHttp directly with Mockito" — couples tests to OkHttp internals; future swap to ktor-client breaks every test.

## 5. Integration Points

| Boundary | Direction | Protocol | Auth |
|---|---|---|---|
| Android → hub Postgres | bidirectional | supabase-kt (PostgREST) | anon key + RLS |
| Android → hub Edge Functions | unidirectional out | OkHttp + JSON | anon (most) / service-role (pedidos+invitados) |
| hub web → hub Edge Functions | unidirectional out | fetch | anon |
| hub Edge Functions → Resend | unidirectional out | fetch | API key (env) |
| hub Edge Functions → Telegram (Make.com) | via existing webhook | unchanged | unchanged |
| Android (MainActivity) ← Camera | deep link `mytemi://guest?id=…` | Android Intent | — |

## 6. Open Risks & Validation Required

1. **`useEvents.ts` current state unknown**: proposal flags it may already be calling a non-existent `activar-anuncio`. If prod-broken today, this change repairs it; if feature-flagged, we light it up. **Action**: verify in spatium-hub repo before merging frontend changes.
2. **anon key extraction from APK**: stated as accepted risk in proposal; revisit if attack surface grows. Per-device JWT is the documented follow-up.
3. **QR rendering across email clients**: data-URI is broadly supported but Outlook desktop has known quirks. **Action**: smoke-test at least Gmail web, Gmail iOS, Outlook desktop, Apple Mail before release.
4. **`send-guest-notification` modification**: changes a function the rest of the hub relies on. **Action**: verify all callers tolerate the additional QR payload (it's additive, but assert).
5. **CAS UPDATE under PostgREST**: requires `Prefer: return=representation` and `.select()` on supabase-kt to confirm affected rows. **Action**: tests must verify the empty-result path explicitly, not just the happy path.
6. **`robot-invitado-checkin` race on first scan**: two near-simultaneous scans could both see "no row" and both INSERT. **Mitigation**: UNIQUE index on `robot_invitados.guest_id` + `ON CONFLICT (guest_id) DO NOTHING RETURNING *` so only one INSERT wins; the loser re-reads the existing row and skips notification.

## 7. Out of Scope (re-affirmed from proposal)

- ALTER on existing hub tables.
- Auth/RBAC redesign beyond per-table RLS toggles above.
- External evaluation upload to `fojrqrkbzsgcefsnwldk`.
- Make.com webhook surface.
- Standalone Supabase decommissioning (separate ticket post-cycle).
