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

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const now = new Date().toISOString();

    // Find the oldest due and not-yet-expired pending guia
    const { data: candidate, error: selectError } = await supabase
      .from("guias")
      .select("*")
      .in("estado", ["programada", "esperando_usuario"])
      .lte("hora_inicio", now)
      .gt("expires_at", now)
      .order("hora_inicio", { ascending: true })
      .limit(1)
      .maybeSingle();

    if (selectError) {
      console.error("Error querying guias:", selectError);
      return json({ error: "DB_ERROR", message: selectError.message }, 500);
    }

    if (!candidate) {
      return json({ pendiente: false });
    }

    // If still 'programada', atomically flip to 'esperando_usuario' (CAS)
    if (candidate.estado === "programada") {
      const { data: claimed, error: updateError } = await supabase
        .from("guias")
        .update({ estado: "esperando_usuario" })
        .eq("id", candidate.id)
        .eq("estado", "programada")   // compare-and-set: only wins if still programada
        .select()
        .single();

      if (updateError || !claimed) {
        // Lost the race to another concurrent poll — tell client to retry
        console.warn("Lost CAS race for guia", candidate.id, updateError?.message);
        return json({ pendiente: false });
      }

      return json({
        pendiente: true,
        guia: {
          id: claimed.id,
          nombre_evento: claimed.nombre_evento,
          descripcion: claimed.descripcion,
          waypoint_inicial: claimed.waypoint_inicial,
          waypoint_final: claimed.waypoint_final,
          hora_inicio: claimed.hora_inicio,
          expires_at: claimed.expires_at,
          imagen_fondo_url: claimed.imagen_fondo_url,
          video_loop_url: claimed.video_loop_url,
          bienvenida_tts: claimed.bienvenida_tts,
          llegada_tts: claimed.llegada_tts,
          etiqueta_boton: claimed.etiqueta_boton,
        },
      });
    }

    // Already 'esperando_usuario' — return as-is (idempotent re-read)
    return json({
      pendiente: true,
      guia: {
        id: candidate.id,
        nombre_evento: candidate.nombre_evento,
        descripcion: candidate.descripcion,
        waypoint_inicial: candidate.waypoint_inicial,
        waypoint_final: candidate.waypoint_final,
        hora_inicio: candidate.hora_inicio,
        expires_at: candidate.expires_at,
        imagen_fondo_url: candidate.imagen_fondo_url,
        video_loop_url: candidate.video_loop_url,
        bienvenida_tts: candidate.bienvenida_tts,
        llegada_tts: candidate.llegada_tts,
        etiqueta_boton: candidate.etiqueta_boton,
      },
    });
  } catch (err) {
    console.error("Error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
