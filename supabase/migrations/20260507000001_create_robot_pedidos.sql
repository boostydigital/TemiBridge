-- robot_pedidos: orders triggered by the camera system (APP CAM V5 / Make.com)
-- INSERT via robot-crear-pedido Edge Function (service-role only)
-- Polled + claimed atomically by RobotPedidosWorker every 2s

CREATE TABLE IF NOT EXISTS robot_pedidos (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  secuencia       TEXT,
  comida          TEXT,
  say             TEXT,
  place           TEXT,
  orden_action    TEXT,
  realizado       BOOLEAN NOT NULL DEFAULT false,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Efficient poll: worker only reads unprocessed rows
CREATE INDEX IF NOT EXISTS idx_robot_pedidos_pendientes
  ON robot_pedidos (created_at)
  WHERE realizado = false;

-- RLS: anon can SELECT + UPDATE (needed for CAS claim by the APK)
-- INSERT is intentionally blocked for anon — only service-role via robot-crear-pedido
ALTER TABLE robot_pedidos ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon_select_pedidos" ON robot_pedidos
  FOR SELECT TO anon USING (true);

CREATE POLICY "anon_update_pedidos" ON robot_pedidos
  FOR UPDATE TO anon USING (true) WITH CHECK (true);
