# Change Proposal: db-migration

## Intent

### Problem
The Temi robot Android app currently talks to a standalone Supabase project (`mkakxmjkwcymwosfrwkl.supabase.co`) for orders, guided tours, scheduled evaluations, and announcements. The rest of the product — guests, events, waypoints, contacts, sedes — lives in the **spatium-hub** Supabase project. This split means:

- Two Supabase projects to operate, secure, and pay for.
- Robot features cannot natively reference real domain data (guests, events, waypoints) without HTTP hops or duplicated state.
- New features (event-driven announcements, QR-based guest check-in) need to JOIN robot state with hub data — currently impossible cross-project.
- spatium-hub already has half-built robot integration pointing at the OLD URL (`TemiBridgeRepository.ts`, `useEvents.ts` calling a non-existent `activar-anuncio`).

### Why now
Two new features (event patrol announcements + QR guest check-in via Resend email) require FK references from robot tables to `guests`, `events`, and `waypoints`. Building them on the standalone project would either duplicate hub data or force more cross-project HTTP. Migrating now is cheaper than building two more features wrong.

### Success criteria
- All Android robot DB traffic terminates at spatium-hub Supabase.
- The standalone Supabase project can be decommissioned (no Android code references its URL/key).
- All robot-owned tables carry the `robot_` prefix; zero ALTER on existing hub tables.
- Existing flows (orders polling, guided tours, evaluations) keep working with no behavior regression.
- Two new flows work end-to-end: event patrol announcement (hub → robot) and QR guest check-in (robot → hub → contact email).

---

## Scope

### In scope
- **Android client rewiring**: update `SupabaseClientProvider`, `AnnouncementManager`, `RatingManager`, `GuiaManager`, `MainActivity` (QR handler) to point at spatium-hub URL/key and new endpoints.
- **New tables in spatium-hub** (all `robot_` prefixed):
  - `robot_pedidos` — food orders (migrated)
  - `robot_guias` — guided tours (migrated)
  - `robot_evaluaciones` — scheduled evaluations (migrated)
  - `robot_anuncios` — event patrol announcements (new)
  - `robot_invitados` — QR guest check-ins (new, FK → `guests.id`)
- **Edge Functions in spatium-hub**:
  - Migrated: `activar-guia`, `guia-pendiente`, `finalizar-guia`, `programar-evaluacion`, `evaluacion-pendiente`, `sweep_stale_guias` RPC
  - New: `activar-anuncio`, `anuncio-activo`, `robot-crear-pedido`, `robot-invitado-checkin`
- **spatium-hub frontend**: update `TemiBridgeRepository.ts` and `useEvents.ts` to hit the unified project and the new `activar-anuncio` endpoint.
- **QR/email integration**: spatium-hub generates QR with `guest_id` and embeds it in Resend email; robot QR scan calls `robot-invitado-checkin` which reuses existing `send-guest-notification`.

### Out of scope
- ALTER on any existing hub table (`guests`, `events`, `event_announcements`, `waypoints`, `clientes`, `contacts`, `sedes`). Robot tables reference them via FK only — read-only relationship.
- Make.com webhooks (Telegram for orders, QR reception fanout) — not Supabase, untouched.
- External evaluation upload to `fojrqrkbzsgcefsnwldk.supabase.co` — out of this migration's blast radius.
- Auth/RBAC redesign — reuse existing service-role key pattern for the robot client.
- Schema redesign of migrated tables — same shape as today, just relocated and prefixed.
- Decommissioning the standalone project (separate cleanup ticket once the migration is verified in production).

---

## Approach

### Strategy: parallel cutover, table by table

Bring up every `robot_*` table and Edge Function on spatium-hub first, then flip the Android client and the hub frontend in a single coordinated commit. The standalone project keeps running until cutover is confirmed; rollback = revert the URL/key constants.

### Step outline

1. **spatium-hub migrations** — five new `supabase/migrations/*_create_robot_*.sql` files. Each one defines its `robot_*` table with the same columns as the standalone version (for migrated tables) or the schema in the exploration (for new ones). FKs to `guests.id`, `events.id`, `waypoints.id` use `ON DELETE SET NULL` to keep the read-only contract honest.
2. **Edge Functions on spatium-hub** — port the five existing functions verbatim (only the Supabase client init changes), then add the four new ones. New functions:
   - `activar-anuncio` (POST `{ texto, imagen_url, duracion_minutos, waypoints }`) — inserts into `robot_anuncios` with `estado='pendiente'`.
   - `anuncio-activo` (GET) — returns the oldest `pendiente` row, marks it `activo`.
   - `robot-crear-pedido` (POST `{ sequence_id, place? }`) — inserts a `realizado=false` row into `robot_pedidos`.
   - `robot-invitado-checkin` (POST `{ guest_id }`) — inserts into `robot_invitados`, then invokes the existing `send-guest-notification` function with the joined contact.
3. **Android cutover** — single PR that updates `SupabaseClientProvider.kt` (URL + anon key) and the four manager classes' Edge Function base URLs. `MainActivity` QR handler swaps from Make.com-only to also calling `robot-invitado-checkin` when payload carries a `guest_id`.
4. **spatium-hub frontend cutover** — same PR or follow-up: `TemiBridgeRepository.ts` URL update, `useEvents.ts` wired to the now-existing `activar-anuncio`.
5. **Verification** — manually trigger each flow in staging: order via `robot-crear-pedido`, guided tour via `activar-guia`, evaluation via `programar-evaluacion`, announcement via `useEvents.ts`, QR check-in end-to-end including the email notification.

### Rationale

- **Why `robot_` prefix and FK-only**: the hub schema is shared with non-robot products. Prefixing makes ownership obvious in `\dt`, and refusing to ALTER hub tables protects that contract. FKs give us referential integrity without coupling lifecycle.
- **Why parallel cutover instead of dual-write**: dual-write would require app-side feature flags and a reconciliation job for two days of value. The robot is a single device per sede with low write volume — a 30-second URL flip in a deploy is the cheapest correct option.
- **Why keep the same schema for migrated tables**: this change is a *relocation*, not a redesign. Schema improvements (e.g., normalizing `waypoints` JSONB into a join table) are a separate decision that shouldn't ride along.
- **Why reuse `send-guest-notification`**: the function already knows how to resolve `guest → contact → email`. `robot-invitado-checkin` becomes a thin orchestrator: write the check-in row, delegate the notification.
- **Why service-role key on the robot**: matches the current pattern. A proper per-device JWT is a separate security work item; not regressing here, not improving here.

### Risks / open questions

- **Anon vs service-role key on Android**: current code uses anon + RLS-permissive policies. Confirm spatium-hub policy stance before copying that posture; may need explicit RLS for `robot_*` tables.
- **`robot-invitado-checkin` idempotency**: a guest can scan the QR multiple times. Need to decide: dedupe on `guest_id` + same-day, or accept duplicates and let the hub UI collapse them. Default to dedupe; flag for spec phase.
- **Migration order vs deploy order**: tables and Edge Functions must be live on spatium-hub *before* the Android APK ships. Coordinate with release cadence.
- **`useEvents.ts` already calls `activar-anuncio`** that doesn't exist — confirm whether this is currently broken in production or gated behind a feature flag, so we know if this change *fixes* a latent bug or merely lights up a new path.
- **Standalone Supabase decommission timing**: keep it warm for at least one full release cycle in case rollback is needed.
