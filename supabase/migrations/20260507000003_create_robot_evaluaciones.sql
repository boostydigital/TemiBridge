-- robot_evaluaciones: salon rating sessions scheduled by external system
-- Mirrors evaluaciones_programadas schema, renamed with robot_ prefix

CREATE TABLE IF NOT EXISTS robot_evaluaciones (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  salon           TEXT NOT NULL,
  waypoint        TEXT NOT NULL,
  hora_fin        TIMESTAMPTZ NOT NULL,
  hora_llegada    TIMESTAMPTZ NOT NULL,
  nombre_reserva  TEXT NOT NULL,
  estado          TEXT NOT NULL DEFAULT 'programada'
                    CHECK (estado IN ('programada', 'en_proceso', 'completada', 'cancelada', 'timeout')),
  rating          INTEGER CHECK (rating BETWEEN 1 AND 5),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Poll index: RatingManager reads rows due for arrival
CREATE INDEX IF NOT EXISTS idx_robot_evaluaciones_pendientes
  ON robot_evaluaciones (hora_llegada)
  WHERE estado = 'programada';

ALTER TABLE robot_evaluaciones ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon_all_evaluaciones" ON robot_evaluaciones
  FOR ALL TO anon USING (true) WITH CHECK (true);
