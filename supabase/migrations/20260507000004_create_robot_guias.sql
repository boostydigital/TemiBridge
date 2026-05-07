-- robot_guias: guided tour sessions (mirrors guias table, robot_ prefix)

CREATE OR REPLACE FUNCTION set_robot_guia_expires_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  NEW.expires_at := NEW.hora_inicio + (NEW.duracion_horas * interval '1 hour');
  RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS robot_guias (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nombre_evento       TEXT NOT NULL CHECK (char_length(nombre_evento) <= 200),
  descripcion         TEXT,
  waypoint_inicial    TEXT NOT NULL,
  waypoint_final      TEXT NOT NULL,
  hora_inicio         TIMESTAMPTZ NOT NULL,
  duracion_horas      NUMERIC(3,1) NOT NULL CHECK (duracion_horas BETWEEN 0.5 AND 8),
  imagen_fondo_url    TEXT,
  video_loop_url      TEXT,
  bienvenida_tts      TEXT NOT NULL,
  llegada_tts         TEXT,
  etiqueta_boton      TEXT NOT NULL,
  estado              TEXT NOT NULL DEFAULT 'programada'
                        CHECK (estado IN ('programada','esperando_usuario','guiando','completada','expirada','cancelada')),
  expires_at          TIMESTAMPTZ,
  finalizado_at       TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_robot_guias_expires_at
  BEFORE INSERT OR UPDATE OF hora_inicio, duracion_horas ON robot_guias
  FOR EACH ROW EXECUTE FUNCTION set_robot_guia_expires_at();

CREATE INDEX IF NOT EXISTS idx_robot_guias_pendientes
  ON robot_guias (estado, hora_inicio);

CREATE INDEX IF NOT EXISTS idx_robot_guias_expires
  ON robot_guias (expires_at, estado);

-- RPC to sweep stale guias (called by robot-sweep-guias Edge Function)
CREATE OR REPLACE FUNCTION sweep_stale_robot_guias()
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
  updated_count INTEGER;
BEGIN
  UPDATE robot_guias
  SET estado = 'expirada', finalizado_at = now()
  WHERE estado IN ('esperando_usuario', 'guiando')
    AND expires_at < now();
  GET DIAGNOSTICS updated_count = ROW_COUNT;
  RETURN updated_count;
END;
$$;

ALTER TABLE robot_guias ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon_all_guias" ON robot_guias
  FOR ALL TO anon USING (true) WITH CHECK (true);
