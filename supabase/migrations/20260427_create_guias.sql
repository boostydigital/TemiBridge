-- Migration: create_guias_table
-- Guia Mode (Modo Guia) — guided-tour scheduling table
-- Spec: sdd/guia-mode/spec §2

CREATE TABLE IF NOT EXISTS public.guias (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_evento       TEXT NOT NULL CHECK (char_length(nombre_evento) <= 200),
    descripcion         TEXT,
    waypoint_inicial    TEXT NOT NULL,
    waypoint_final      TEXT NOT NULL,
    hora_inicio         TIMESTAMPTZ NOT NULL,
    duracion_horas      NUMERIC(5,2) NOT NULL CHECK (duracion_horas BETWEEN 0.5 AND 24),
    imagen_fondo_url    TEXT NOT NULL DEFAULT '',
    video_loop_url      TEXT NOT NULL DEFAULT '',
    bienvenida_tts      TEXT NOT NULL,
    llegada_tts         TEXT DEFAULT '',
    etiqueta_boton      TEXT NOT NULL CHECK (char_length(etiqueta_boton) <= 60),
    estado              TEXT NOT NULL DEFAULT 'programada'
                            CHECK (estado IN ('programada','esperando_usuario','guiando',
                                              'completada','expirada','cancelada')),
    expires_at          TIMESTAMPTZ NOT NULL DEFAULT now(),  -- overwritten by trigger immediately
    finalizado_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guias_waypoints_distinct CHECK (waypoint_inicial <> waypoint_final)
);

-- Trigger function: auto-compute expires_at = hora_inicio + duracion_horas
CREATE OR REPLACE FUNCTION public.guias_set_expires_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.expires_at := NEW.hora_inicio + (NEW.duracion_horas || ' hours')::interval;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guias_expires_at
    BEFORE INSERT OR UPDATE OF hora_inicio, duracion_horas ON public.guias
    FOR EACH ROW EXECUTE FUNCTION public.guias_set_expires_at();

-- Index: fast lookup for guia-pendiente query (active states + hora_inicio)
CREATE INDEX IF NOT EXISTS idx_guias_pendientes
    ON public.guias (estado, hora_inicio)
    WHERE estado IN ('programada','esperando_usuario','guiando');

-- Index: fast lookup for crash-recovery sweep (stale active rows)
CREATE INDEX IF NOT EXISTS idx_guias_expires
    ON public.guias (expires_at)
    WHERE estado IN ('esperando_usuario','guiando');

-- Stored proc: crash-recovery sweep — marks stale active rows as expirada
CREATE OR REPLACE FUNCTION public.sweep_stale_guias()
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    n INT;
BEGIN
    UPDATE public.guias
       SET estado = 'expirada',
           finalizado_at = now()
     WHERE estado IN ('esperando_usuario','guiando')
       AND expires_at < now();
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

-- RLS
ALTER TABLE public.guias ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon select guias"
    ON public.guias FOR SELECT
    TO anon
    USING (true);

CREATE POLICY "anon insert guias"
    ON public.guias FOR INSERT
    TO anon
    WITH CHECK (true);

CREATE POLICY "anon update guias"
    ON public.guias FOR UPDATE
    TO anon
    USING (true);
