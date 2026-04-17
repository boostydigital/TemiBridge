import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, supabaseKey);

    const now = new Date().toISOString();

    // Buscar evaluación programada donde hora_llegada <= now
    const { data, error } = await supabase
      .from("evaluaciones_programadas")
      .select("*")
      .eq("estado", "programada")
      .lte("hora_llegada", now)
      .order("hora_llegada", { ascending: true })
      .limit(1)
      .single();

    if (error && error.code !== "PGRST116") {
      // PGRST116 = no rows returned
      console.error("Error consultando:", error);
      return new Response(
        JSON.stringify({ error: "Error al consultar", details: error.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!data) {
      return new Response(
        JSON.stringify({ pendiente: false, evaluacion: null }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Marcar como en_proceso para que no se vuelva a tomar
    await supabase
      .from("evaluaciones_programadas")
      .update({ estado: "en_proceso" })
      .eq("id", data.id);

    return new Response(
      JSON.stringify({
        pendiente: true,
        evaluacion: data,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("Error:", err);
    return new Response(
      JSON.stringify({ error: "Error interno", details: String(err) }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
