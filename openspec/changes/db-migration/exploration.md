# Exploration: Database Migration — Full Dependency Map

## Current State

The app connects to a single Supabase project (`mkakxmjkwcymwosfrwkl.supabase.co`) via two distinct access patterns:

1. **supabase-kt SDK** — typed Kotlin client for direct table access
2. **Raw OkHttp → Edge Functions** — HTTP calls to Deno functions deployed on the same project

Additionally there are three external services that are **NOT part of the DB migration scope**: Google TTS, two Make.com webhooks, and a second external Supabase project for rating results.

---

## Affected Areas

### Android — Direct SDK (supabase-kt)
- `app/src/main/java/com/spatium/temibridge/core/SupabaseClientProvider.kt` — singleton client, reads URL+key from `BuildConfig`
- `app/src/main/java/com/spatium/temibridge/core/RobotPedidosWorker.kt` — only file using `.from("robot_pedidos")` directly

### Android — OkHttp → Edge Functions
- `app/src/main/java/com/spatium/temibridge/core/AnnouncementManager.kt` — calls `activar-anuncio`, `anuncio-activo`
- `app/src/main/java/com/spatium/temibridge/core/RatingManager.kt` — calls `programar-evaluacion`, `evaluacion-pendiente`; also POSTs to external Supabase `fojrqrkbzsgcefsnwldk.supabase.co`
- `app/src/main/java/com/spatium/temibridge/core/GuiaManager.kt` — calls `activar-guia`, `guia-pendiente`, `finalizar-guia`, `/rest/v1/rpc/sweep_stale_guias`

### Android — Make.com Webhooks (NOT Supabase, out of scope)
- `app/src/main/java/com/spatium/temibridge/ui/PedidosActivity.kt` — `hook.us1.make.com/ei3fb5lpstgw8s8sygvyvnda9klzq0y3`
- `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt` — `hook.us1.make.com/rpr19yvr51pufln58pwln4rdgz0dl6hq`

### Edge Functions (in-repo, `supabase/functions/`)
- `activar-guia/index.ts` — INSERT into `guias`
- `guia-pendiente/index.ts` — SELECT + CAS UPDATE on `guias`
- `finalizar-guia/index.ts` — UPDATE `guias` to terminal state
- `programar-evaluacion/index.ts` — INSERT / UPDATE cancel on `evaluaciones_programadas`
- `evaluacion-pendiente/index.ts` — SELECT + claim UPDATE on `evaluaciones_programadas`

### Edge Functions (deployed but NOT in-repo — risk)
- `activar-anuncio` — table and schema unknown
- `anuncio-activo` — table and schema unknown

### DB Schema / Migrations
- `supabase/migrations/20260427_create_guias.sql` — `guias` table + trigger + RPC + indexes
- `supabase/migrations/create_evaluaciones_programadas.sql` — `evaluaciones_programadas` table

---

## Tables & Operations Map

| Table | Access method | SELECT | INSERT | UPDATE | RPC |
|-------|--------------|--------|--------|--------|-----|
| `robot_pedidos` | supabase-kt SDK | `realizado=false` | — | claim: `realizado=true` | — |
| `guias` | Edge Functions | estado filter, time window | new guia | CAS state, terminal state | `sweep_stale_guias()` |
| `evaluaciones_programadas` | Edge Functions | estado+time filter | schedule | claim, cancel | — |
| `anuncios` (inferred) | Edge Functions | unknown | unknown | unknown | — |

## Edge Function → Table Map

| Function | Table | Operations |
|----------|-------|-----------|
| `activar-guia` | `guias` | INSERT, SELECT (conflict check) |
| `guia-pendiente` | `guias` | SELECT + CAS UPDATE (`programada` → `esperando_usuario`) |
| `finalizar-guia` | `guias` | SELECT, UPDATE (`→ completada/expirada/cancelada`) |
| `programar-evaluacion` | `evaluaciones_programadas` | INSERT, UPDATE (cancel) |
| `evaluacion-pendiente` | `evaluaciones_programadas` | SELECT, UPDATE (`→ en_proceso`) |
| `activar-anuncio` | unknown | unknown |
| `anuncio-activo` | unknown | unknown |

## Stored Procedures / Triggers

| Object | Type | What |
|--------|------|------|
| `sweep_stale_guias()` | RPC (stored proc) | Marks expired `guias` rows as `expirada` |
| `guias_set_expires_at` | BEFORE INSERT/UPDATE trigger | Auto-computes `expires_at = hora_inicio + duracion_horas` |

---

## Approaches

### 1. Lift-and-shift (same DB provider, new project)
Export schema + data, recreate in new Supabase project, update URL/key in `local.properties`.
- Pros: minimal code changes (only `SupabaseClientProvider` URL + `BuildConfig`). Edge Functions redeploy as-is.
- Cons: downtime window; must recreate triggers, RPCs, RLS policies, and indexes.
- Effort: Low

### 2. Migrate to a different DB provider (e.g., PlanetScale, Neon, Firebase, custom)
Replace supabase-kt SDK with a different client; rewrite all 5 Edge Functions for new provider; replace direct RPC with equivalent.
- Pros: provider flexibility.
- Cons: HIGH effort. Two access patterns must be rewritten. Edge Functions may need a different runtime. RPC equivalent needed.
- Effort: High

### 3. Hybrid — keep Edge Functions, swap DB behind them
Change the DB that Edge Functions connect to (e.g., Postgres on Neon), keep function URLs identical. Only `robot_pedidos` SDK access needs client change.
- Pros: Android app is almost untouched (only `SupabaseClientProvider` URL change for SDK calls).
- Cons: Edge Functions must be rewritten to use a new DB client. `anuncio-*` functions are unknown — risky.
- Effort: Medium

---

## Recommendation

**Option 1 (lift-and-shift to new Supabase project)** unless the user has a specific reason to change provider. It minimizes risk because:
- supabase-kt SDK only needs a URL+key change in `local.properties`
- All 5 in-repo Edge Functions redeploy unchanged
- Triggers, RPCs, and RLS are already captured in migration files

**Blocker before any migration**: The two missing Edge Functions (`activar-anuncio`, `anuncio-activo`) must be recovered or their source obtained. Their table (`anuncios`?) schema is unknown.

---

## Risks

1. **`activar-anuncio` / `anuncio-activo` source is missing** — can't migrate what you can't see. Must dump from Supabase dashboard before migration.
2. **`robot_pedidos` has no migration file** — schema must be exported from current DB.
3. **External Supabase project** (`fojrqrkbzsgcefsnwldk.supabase.co`) used by `RatingManager` — this is a different project, NOT migrated here. Must stay pointing to same project or coordinate separately.
4. **Trigger `guias_set_expires_at` is invisible at runtime** — if forgotten, `expires_at` is never set and `guia-pendiente` / `sweep_stale_guias` break silently.
5. **`robot_pedidos` polling is 1s** — any DNS/connectivity cut during migration will flood logs and may trigger retry storms.

---

## Ready for Proposal

Yes — with one prerequisite: obtain source for `activar-anuncio` and `anuncio-activo` Edge Functions from the Supabase dashboard before writing the proposal.
