-- robot_anuncios: patrol announcements triggered by spatium-hub web
-- INSERT + flip via Edge Functions activar-anuncio / anuncio-activo

CREATE TABLE IF NOT EXISTS robot_anuncios (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  texto               TEXT NOT NULL,
  imagen_url          TEXT,
  duracion_minutos    INTEGER NOT NULL CHECK (duracion_minutos BETWEEN 1 AND 120),
  waypoints           JSONB NOT NULL DEFAULT '[]',
  estado              TEXT NOT NULL DEFAULT 'pendiente'
                        CHECK (estado IN ('pendiente', 'activo', 'completado', 'cancelado')),
  expires_at          TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Poll index: AnnouncementManager reads only active/pending rows
CREATE INDEX IF NOT EXISTS idx_robot_anuncios_activos
  ON robot_anuncios (created_at)
  WHERE estado IN ('pendiente', 'activo');

ALTER TABLE robot_anuncios ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon_all_anuncios" ON robot_anuncios
  FOR ALL TO anon USING (true) WITH CHECK (true);
