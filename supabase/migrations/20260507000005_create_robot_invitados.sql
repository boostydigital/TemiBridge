-- robot_invitados: guest check-in records linked to the guests table
-- All mutations via robot-invitado-checkin Edge Function (service-role only)
-- UNIQUE on guest_id prevents duplicate INSERT race — ON CONFLICT handles it

CREATE TABLE IF NOT EXISTS robot_invitados (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guest_id              UUID NOT NULL,
  status                TEXT NOT NULL DEFAULT 'pendiente'
                          CHECK (status IN ('pendiente', 'bienvenido', 'menu_abierto', 'notificado')),
  check_in_at           TIMESTAMPTZ,
  contact_notified_at   TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_robot_invitados_guest UNIQUE (guest_id)
);

-- NOTE: FK to guests(id) omitted intentionally — guests table lives in spatium-hub
-- and may not have been created yet at migration time. Add FK separately:
--   ALTER TABLE robot_invitados ADD CONSTRAINT fk_guest FOREIGN KEY (guest_id) REFERENCES guests(id);

CREATE INDEX IF NOT EXISTS idx_robot_invitados_guest
  ON robot_invitados (guest_id);

-- RLS: service-role only — the APK never reads/writes this table directly
ALTER TABLE robot_invitados ENABLE ROW LEVEL SECURITY;

-- No anon policies: only robot-invitado-checkin (service-role) can touch this table
