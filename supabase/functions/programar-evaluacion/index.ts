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

const SALON_WAYPOINT_MAP: Record<string, string> = {
  "Sala Duarte": "salonduarte",
  "Sala Enriquillo": "salonenriquillo",
  "Sala Multimedia": "salonmultimedia",
  "Sala Quisqueya": "salonquisqueya",
  "Sala Santo Domingo": "salonsantodomingo",
};

// programar-evaluacion
// POST { id, estado, rating? }          → update robot_evaluaciones by id
// POST { salon, hora_fin, nombre_reserva, estado?: "cancelado" } → create or cancel
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const body = await req.json();
    const { id, salon, hora_fin, nombre_reserva, estado, rating } = body;

    // ── Path A: update by id (called by RatingManager on completion/timeout/cancel) ──
    if (id && estado) {
      const updates: Record<string, unknown> = { estado };
      if (rating != null) updates.rating = rating;

      const { error } = await supabase
        .from("robot_evaluaciones")
        .update(updates)
        .eq("id", id);

      if (error) {
        console.error("Error updating robot_evaluaciones:", error);
        return json({ error: "DB_ERROR", message: error.message }, 500);
      }

      console.log(`robot_evaluaciones id=${id} updated to estado=${estado}`);
      return json({ success: true, action: "updated" });
    }

    // ── Path B: cancel by salon + hora_fin ──
    if (estado === "cancelado" && salon && hora_fin) {
      const { data, error } = await supabase
        .from("robot_evaluaciones")
        .update({ estado: "cancelada" })
        .eq("salon", salon)
        .eq("hora_fin", new Date(hora_fin).toISOString())
        .in("estado", ["programada", "en_proceso"])
        .select();

      if (error) {
        console.error("Error cancelling:", error);
        return json({ error: "DB_ERROR", message: error.message }, 500);
      }

      return json({ success: true, action: "cancelada", canceladas: data?.length ?? 0 });
    }

    // ── Path C: create new evaluation ──
    if (!salon || !hora_fin || !nombre_reserva) {
      return json({ error: "INVALID_PAYLOAD", message: "Required: salon, hora_fin, nombre_reserva" }, 400);
    }

    const waypoint = SALON_WAYPOINT_MAP[salon];
    if (!waypoint) {
      return json({ error: "INVALID_SALON", message: `Salon not mapped: ${salon}`, valid: Object.keys(SALON_WAYPOINT_MAP) }, 400);
    }

    const horaFin = new Date(hora_fin);
    const horaLlegada = new Date(horaFin.getTime() - 5 * 60 * 1000);

    if (horaLlegada < new Date()) {
      return json({ error: "PAST_TIME", message: "hora_llegada is already in the past" }, 400);
    }

    const { data, error } = await supabase
      .from("robot_evaluaciones")
      .insert({ salon, waypoint, hora_fin: horaFin.toISOString(), hora_llegada: horaLlegada.toISOString(), nombre_reserva, estado: "programada" })
      .select()
      .single();

    if (error) {
      console.error("Error inserting robot_evaluaciones:", error);
      return json({ error: "DB_ERROR", message: error.message }, 500);
    }

    console.log(`robot_evaluaciones created: id=${data.id} salon=${salon}`);
    return json({ success: true, action: "programada", evaluacion: data });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
