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

// activar-anuncio — creates a patrol announcement
// POST { texto, imagen_url?, duracion_minutos, waypoints }
serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const { texto, imagen_url, duracion_minutos, waypoints } = body;

    if (!texto || typeof texto !== "string" || texto.trim() === "") {
      return json({ error: "INVALID_PAYLOAD", message: "texto is required" }, 400);
    }
    if (!duracion_minutos || typeof duracion_minutos !== "number" || duracion_minutos < 1 || duracion_minutos > 120) {
      return json({ error: "INVALID_PAYLOAD", message: "duracion_minutos must be between 1 and 120" }, 400);
    }
    if (!Array.isArray(waypoints) || waypoints.length < 1) {
      return json({ error: "INVALID_PAYLOAD", message: "waypoints must be a non-empty array" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const expiresAt = new Date(Date.now() + duracion_minutos * 60 * 1000).toISOString();

    const { data, error } = await supabase
      .from("robot_anuncios")
      .insert({
        texto: texto.trim(),
        imagen_url: imagen_url ?? null,
        duracion_minutos,
        waypoints,
        estado: "pendiente",
        expires_at: expiresAt,
      })
      .select("id, texto, duracion_minutos, estado, created_at")
      .single();

    if (error) {
      console.error("Error inserting robot_anuncios:", error);
      return json({ error: "DB_ERROR", message: error.message }, 500);
    }

    console.log(`robot_anuncios created: id=${data.id}`);
    return json({ success: true, anuncio: data });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
