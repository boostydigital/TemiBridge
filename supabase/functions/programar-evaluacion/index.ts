import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// Mapeo de salones a waypoints
const SALON_WAYPOINT_MAP: Record<string, string> = {
  "Sala Duarte": "salonduarte",
  "Sala Enriquillo": "salonenriquillo",
  "Sala Multimedia": "salonmultimedia",
  "Sala Quisqueya": "salonquisqueya",
  "Sala Santo Domingo": "salonsantodomingo",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { salon, hora_fin, nombre_reserva, estado } = await req.json();

    // Validaciones básicas
    if (!salon || !hora_fin || !nombre_reserva) {
      return new Response(
        JSON.stringify({ error: "Faltan campos requeridos: salon, hora_fin, nombre_reserva" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const waypoint = SALON_WAYPOINT_MAP[salon];
    if (!waypoint) {
      return new Response(
        JSON.stringify({ 
          error: `Salón no válido: ${salon}`,
          salones_validos: Object.keys(SALON_WAYPOINT_MAP)
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, supabaseKey);

    // Si estado es "cancelado", cancelar evaluaciones pendientes para ese salón y hora
    if (estado === "cancelado") {
      const horaFinDate = new Date(hora_fin);
      
      const { data, error } = await supabase
        .from("evaluaciones_programadas")
        .update({ estado: "cancelada" })
        .eq("salon", salon)
        .eq("hora_fin", horaFinDate.toISOString())
        .in("estado", ["programada", "en_proceso"])
        .select();

      if (error) {
        console.error("Error cancelando:", error);
        return new Response(
          JSON.stringify({ error: "Error al cancelar evaluación", details: error.message }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({
          success: true,
          action: "cancelada",
          evaluaciones_canceladas: data?.length || 0,
          evaluaciones: data,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Flujo normal: programar nueva evaluación
    const horaFinDate = new Date(hora_fin);
    const horaLlegadaDate = new Date(horaFinDate.getTime() - 3 * 60 * 1000);

    // Validar que hora_llegada no sea en el pasado
    if (horaLlegadaDate < new Date()) {
      return new Response(
        JSON.stringify({ error: "La hora de fin ya pasó o es muy pronto" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Insertar evaluación programada
    const { data, error } = await supabase
      .from("evaluaciones_programadas")
      .insert({
        salon,
        waypoint,
        hora_fin: horaFinDate.toISOString(),
        hora_llegada: horaLlegadaDate.toISOString(),
        nombre_reserva,
        estado: "programada",
      })
      .select()
      .single();

    if (error) {
      console.error("Error insertando:", error);
      return new Response(
        JSON.stringify({ error: "Error al programar evaluación", details: error.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        action: "programada",
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
