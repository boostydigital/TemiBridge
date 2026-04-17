-- Migración: create_evaluaciones_programadas
-- Ejecutar en Supabase SQL Editor

CREATE TABLE IF NOT EXISTS evaluaciones_programadas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  salon TEXT NOT NULL,
  waypoint TEXT NOT NULL,
  hora_fin TIMESTAMPTZ NOT NULL,
  hora_llegada TIMESTAMPTZ NOT NULL,
  nombre_reserva TEXT NOT NULL,
  estado TEXT DEFAULT 'programada' CHECK (estado IN ('programada', 'en_proceso', 'completada', 'timeout')),
  rating INTEGER CHECK (rating BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_evaluaciones_pendientes 
ON evaluaciones_programadas (estado, hora_llegada) 
WHERE estado = 'programada';

ALTER TABLE evaluaciones_programadas ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Evaluaciones públicas para lectura" 
ON evaluaciones_programadas FOR SELECT USING (true);

CREATE POLICY "Evaluaciones públicas para inserción" 
ON evaluaciones_programadas FOR INSERT WITH CHECK (true);

CREATE POLICY "Evaluaciones públicas para actualización" 
ON evaluaciones_programadas FOR UPDATE USING (true);
