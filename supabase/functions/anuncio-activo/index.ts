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

// anuncio-activo — polled by AnnouncementManager every 30s
// GET — returns oldest pending/active announcement; CAS flips pendiente→activo
serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const now = new Date().toISOString();

    // Find oldest non-expired announcement in pendiente or activo
    const { data: candidate, error: selectError } = await supabase
      .from("robot_anuncios")
      .select("*")
      .in("estado", ["pendiente", "activo"])
      .gt("expires_at", now)
      .order("created_at", { ascending: true })
      .limit(1)
      .maybeSingle();

    if (selectError) {
      console.error("Error querying robot_anuncios:", selectError);
      return json({ error: "DB_ERROR", message: selectError.message }, 500);
    }

    if (!candidate) {
      return json({ activo: false });
    }

    // CAS flip: pendiente → activo
    if (candidate.estado === "pendiente") {
      const { data: claimed, error: updateError } = await supabase
        .from("robot_anuncios")
        .update({ estado: "activo" })
        .eq("id", candidate.id)
        .eq("estado", "pendiente")
        .select()
        .single();

      if (updateError || !claimed) {
        console.warn("Lost CAS race for anuncio", candidate.id);
        return json({ activo: false });
      }

      return json({ activo: true, anuncio: claimed });
    }

    // Already activo — return as-is (idempotent)
    return json({ activo: true, anuncio: candidate });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
