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

// evaluacion-pendiente — polled by RatingManager every 30s
// GET — returns oldest programada evaluation due for arrival; CAS flips programada→en_proceso
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const now = new Date().toISOString();

    const { data, error } = await supabase
      .from("robot_evaluaciones")
      .select("*")
      .eq("estado", "programada")
      .lte("hora_llegada", now)
      .order("hora_llegada", { ascending: true })
      .limit(1)
      .maybeSingle();

    if (error) {
      console.error("Error consultando robot_evaluaciones:", error);
      return json({ error: "DB_ERROR", message: error.message }, 500);
    }

    if (!data) {
      return json({ pendiente: false, evaluacion: null });
    }

    // CAS flip: programada → en_proceso
    const { data: claimed, error: updateError } = await supabase
      .from("robot_evaluaciones")
      .update({ estado: "en_proceso" })
      .eq("id", data.id)
      .eq("estado", "programada")
      .select()
      .single();

    if (updateError || !claimed) {
      console.warn("Lost CAS race for evaluacion", data.id);
      return json({ pendiente: false, evaluacion: null });
    }

    console.log(`Evaluacion claimed: id=${claimed.id} salon=${claimed.salon}`);
    return json({ pendiente: true, evaluacion: claimed });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
