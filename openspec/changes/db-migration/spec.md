# Delta Spec: db-migration

All modules below are NEW — no existing `robot_*` specs exist on spatium-hub.

---

## Module 1: robot_pedidos

### Requirement: Table Schema

The `robot_pedidos` table MUST contain columns: `id`, `secuencia`, `comida`, `say`, `place`, `orden_action`, `realizado BOOLEAN DEFAULT false`, `created_at`.  
RLS MUST allow anon `SELECT` and `UPDATE`; inserts MUST be service-role only.

#### Scenario: Happy path — order created via Edge Function

- GIVEN the Edge Function `robot-crear-pedido` receives `POST { sequence_id, place? }`
- WHEN the request carries a valid service-role key
- THEN a row is inserted with `realizado=false` and `{ id }` is returned

#### Scenario: Worker claims an order atomically

- GIVEN at least one row with `realizado=false` exists
- WHEN `RobotPedidosWorker` polls SELECT and issues UPDATE claim
- THEN exactly one worker processes the row; no duplicate claim is possible

#### Scenario: Order processed — activity launched

- GIVEN `RobotPedidosWorker` claims a row
- WHEN the sequence plays and `MenuActivity` opens
- THEN `realizado` is set to `true` before the next poll cycle

---

## Module 2: robot_guias

### Requirement: Table Schema and Triggers

`robot_guias` MUST include: `id, nombre_evento, descripcion, waypoint_inicial, waypoint_final, hora_inicio, duracion_horas, imagen_fondo_url, video_loop_url, bienvenida_tts, llegada_tts, etiqueta_boton, estado, expires_at, finalizado_at, created_at`.  
A DB trigger MUST set `expires_at = hora_inicio + duracion_horas * interval '1 hour'`.  
`sweep_stale_guias()` RPC MUST set expired rows to `expirada`.

#### Scenario: Guide activation

- GIVEN a `programada` row exists and `activar-guia` is called
- WHEN `estado` allows the transition
- THEN row flips to `esperando_usuario` and the function returns the guide data

#### Scenario: Guide pending claim (CAS)

- GIVEN `guia-pendiente` GET is called
- WHEN a row in `esperando_usuario` exists
- THEN it atomically flips to `guiando` and returns the row; concurrent call returns nothing

#### Scenario: Guide finalized

- GIVEN a row in `guiando`
- WHEN `finalizar-guia` POST is called
- THEN row transitions to `completada` and `finalizado_at` is set

---

## Module 3: robot_evaluaciones

### Requirement: Table Schema and Edge Functions

`robot_evaluaciones` MUST include: `id, salon, waypoint, hora_fin, hora_llegada, nombre_reserva, estado, rating, created_at`.  
`estado` MUST be one of: `programada / en_proceso / completada / cancelada / timeout`.

#### Scenario: Schedule evaluation

- GIVEN `programar-evaluacion` POST receives valid payload
- WHEN the row does not yet exist for that salon + hora_fin
- THEN a new `programada` row is created and `{ id }` is returned

#### Scenario: Cancel evaluation

- GIVEN `programar-evaluacion` POST contains `cancel: true` for an existing id
- WHEN the row is in `programada`
- THEN row transitions to `cancelada`

#### Scenario: Claim evaluation

- GIVEN `evaluacion-pendiente` GET is called
- WHEN a `programada` row whose `hora_fin` is due exists
- THEN it flips to `en_proceso` and returns the row; no double claim

---

## Module 4: robot_anuncios

### Requirement: Table Schema

`robot_anuncios` MUST include: `id, texto TEXT, imagen_url TEXT, duracion_minutos INTEGER, waypoints JSONB, estado TEXT, created_at`.  
`estado` MUST be one of `pendiente / activo / completado / cancelado`.

### Requirement: activar-anuncio Edge Function

`activar-anuncio` (POST) MUST accept `{ texto, imagen_url, duracion_minutos, waypoints }`, insert with `estado=pendiente`, and return `{ id }`.

#### Scenario: Announcement created

- GIVEN valid POST body is sent to `activar-anuncio`
- WHEN the request carries service-role authorization
- THEN a row with `estado=pendiente` is created and `{ id }` is returned

### Requirement: anuncio-activo Edge Function

`anuncio-activo` (GET) MUST return the oldest `pendiente` or `activo` row and flip it to `activo`.  
If no row qualifies it MUST return `{ activo: false }`.

#### Scenario: Active announcement returned

- GIVEN one `pendiente` row exists
- WHEN `anuncio-activo` GET is called
- THEN the row is returned with `estado=activo`

#### Scenario: No active announcement

- GIVEN no `pendiente` or `activo` rows exist
- WHEN `anuncio-activo` GET is called
- THEN `{ activo: false }` is returned

---

## Module 5: robot_invitados

### Requirement: Table Schema

`robot_invitados` MUST include: `id, guest_id UUID NOT NULL REFERENCES guests(id), status TEXT, check_in_at TIMESTAMPTZ, contact_notified_at TIMESTAMPTZ, created_at`.  
`status` MUST be one of `pendiente / bienvenido / menu_abierto / notificado`.

### Requirement: robot-invitado-checkin Edge Function

`robot-invitado-checkin` (POST) MUST accept `{ guest_id }`, create a `robot_invitados` row, invoke `send-guest-notification` with `type=checked_in`, and return guest name + contact name for TTS.  
If a non-`pendiente` row already exists for `guest_id`, it MUST return the existing row without sending a duplicate notification.

#### Scenario: First check-in

- GIVEN no prior row for `guest_id`
- WHEN `robot-invitado-checkin` POST is called
- THEN a new row is created, notification is sent, and guest/contact names are returned

#### Scenario: Duplicate scan — idempotent

- GIVEN a row already exists for `guest_id` with `status != pendiente`
- WHEN `robot-invitado-checkin` POST is called again
- THEN existing row is returned and `send-guest-notification` is NOT called again

#### Scenario: Android QR flow

- GIVEN MainActivity decodes a QR containing `mytemi://guest?id={guest_id}`
- WHEN the QR is scanned
- THEN `robot-invitado-checkin` is called, robot speaks welcome TTS, and `MenuActivity` opens

---

## Module 6: URL Migration

### Requirement: Single Base URL Constant

All Android OkHttp base URLs (AnnouncementManager, RatingManager, GuiaManager) MUST read from a single `BuildConfig.TEMI_BASE_URL` constant.  
`SupabaseClientProvider.kt` MUST read `SUPABASE_URL` and `SUPABASE_ANON_KEY` from `BuildConfig` (values injected from `local.properties`).

#### Scenario: URL constant applied

- GIVEN `local.properties` contains `TEMI_BASE_URL=https://{new-project}.supabase.co`
- WHEN any manager constructs an OkHttp request
- THEN the request targets spatium-hub; no reference to the old standalone project remains

### Requirement: spatium-hub Frontend Update

`TemiBridgeRepository.ts` and `useEvents.ts` MUST reference the spatium-hub project URL.  
`useEvents.ts` MUST call `activar-anuncio` on spatium-hub.

---

## Module 7: QR Generation in spatium-hub

### Requirement: QR in Guest Invitation Email

When a guest is created, `send-guest-notification` MUST generate a QR code encoding `mytemi://guest?id={guest_id}` and embed it as a PNG in the Resend email.

#### Scenario: Email includes QR

- GIVEN a new guest is created and `send-guest-notification` is invoked
- WHEN the function generates the email
- THEN the email body contains an embedded PNG QR code encoding the `mytemi://` deep link

---

## Test Definitions (Strict TDD — JUnit 4 + FakeRobotGateway)

All test files MUST live under `app/src/test/java/com/spatium/`.

| Test class | Validates scenario |
|---|---|
| `RobotPedidosWorkerTest` | Atomic claim: only one worker processes a row; `realizado` set after sequence plays |
| `CheckinHandlerTest` | First check-in creates row + notifies; duplicate scan skips notification |
| `AnnouncementManagerTest` | `anuncio-activo` returns row when pending; returns `activo:false` when empty |
| `GuiaManagerTest` | CAS flip to `guiando`; `finalizar` sets `completada` + `finalizado_at` |
| `EvaluacionManagerTest` | Claim flips to `en_proceso`; cancel on `programada` sets `cancelada` |
| `UrlConstantTest` | All manager constructors resolve base URL from `BuildConfig.TEMI_BASE_URL` |

Each test MUST use `FakeRobotGateway` or a fake HTTP server to avoid hitting real Supabase in unit tests.
