import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

const REQUIRED_FIELDS = [
  "nombre_evento",
  "waypoint_inicial",
  "waypoint_final",
  "hora_inicio",
  "duracion_horas",
  "imagen_fondo_url",
  "video_loop_url",
  "bienvenida_tts",
  "etiqueta_boton",
] as const;

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  try {
    const body = await req.json();

    // Validate required fields
    for (const field of REQUIRED_FIELDS) {
      const val = body[field];
      if (val === undefined || val === null || val === "") {
        return json(
          { error: "MISSING_FIELD", field, message: `El campo '${field}' es requerido y no puede estar vacío.` },
          400
        );
      }
    }

    // Validate nombre_evento max length
    if (typeof body.nombre_evento === "string" && body.nombre_evento.length > 200) {
      return json({ error: "VALIDATION_ERROR", field: "nombre_evento", message: "Máximo 200 caracteres." }, 400);
    }

    // Validate etiqueta_boton max length
    if (typeof body.etiqueta_boton === "string" && body.etiqueta_boton.length > 60) {
      return json({ error: "VALIDATION_ERROR", field: "etiqueta_boton", message: "Máximo 60 caracteres." }, 400);
    }

    // Validate waypoints are different
    if (body.waypoint_inicial === body.waypoint_final) {
      return json(
        {
          error: "VALIDATION_ERROR",
          field: "waypoint_final",
          message: "waypoint_inicial y waypoint_final deben ser diferentes.",
        },
        400
      );
    }

    // Validate duracion_horas range (0.5 to 24 inclusive)
    const duracion = Number(body.duracion_horas);
    if (isNaN(duracion) || duracion < 0.5 || duracion > 24) {
      return json(
        {
          error: "VALIDATION_ERROR",
          field: "duracion_horas",
          message: "duracion_horas debe estar entre 0.5 y 24.",
        },
        400
      );
    }

    // Validate hora_inicio is in the future
    const horaInicio = new Date(body.hora_inicio);
    if (isNaN(horaInicio.getTime()) || horaInicio <= new Date()) {
      return json(
        {
          error: "VALIDATION_ERROR",
          field: "hora_inicio",
          message: "hora_inicio debe ser un timestamp futuro válido (ISO-8601 con offset).",
        },
        400
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Conflict check: any row in active states?
    const { data: existing, error: checkError } = await supabase
      .from("guias")
      .select("id, estado")
      .in("estado", ["programada", "esperando_usuario", "guiando"])
      .limit(1)
      .maybeSingle();

    if (checkError) {
      console.error("Error checking conflict:", checkError);
      return json({ error: "DB_ERROR", message: checkError.message }, 500);
    }

    if (existing) {
      return json(
        {
          error: "CONFLICT",
          message: "Ya existe una guia activa o programada.",
          existing_id: existing.id,
        },
        409
      );
    }

    // Insert new guia
    const { data, error: insertError } = await supabase
      .from("guias")
      .insert({
        nombre_evento: body.nombre_evento,
        descripcion: body.descripcion ?? null,
        waypoint_inicial: body.waypoint_inicial,
        waypoint_final: body.waypoint_final,
        hora_inicio: body.hora_inicio,
        duracion_horas: duracion,
        imagen_fondo_url: body.imagen_fondo_url,
        video_loop_url: body.video_loop_url,
        bienvenida_tts: body.bienvenida_tts,
        llegada_tts: body.llegada_tts ?? "",
        etiqueta_boton: body.etiqueta_boton,
        estado: "programada",
      })
      .select()
      .single();

    if (insertError) {
      console.error("Error inserting:", insertError);
      return json({ error: "DB_ERROR", message: insertError.message }, 500);
    }

    return json({
      success: true,
      action: "programada",
      guia: {
        id: data.id,
        estado: data.estado,
        expires_at: data.expires_at,
      },
    });
  } catch (err) {
    console.error("Error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
