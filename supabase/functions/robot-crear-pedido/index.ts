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

// robot-crear-pedido — called by camera system (APP CAM V5) or Make.com
// POST { sequence_id, place? }
// Inserts a row in robot_pedidos; RobotPedidosWorker claims it atomically via CAS
serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const { sequence_id, place } = body;

    if (!sequence_id || typeof sequence_id !== "string" || sequence_id.trim() === "") {
      return json({ error: "INVALID_PAYLOAD", message: "sequence_id is required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { data, error } = await supabase
      .from("robot_pedidos")
      .insert({
        secuencia: sequence_id.trim(),
        place: place?.toString().trim() ?? null,
        realizado: false,
      })
      .select("id, secuencia, place, created_at")
      .single();

    if (error) {
      console.error("Error inserting robot_pedidos:", error);
      return json({ error: "DB_ERROR", message: error.message }, 500);
    }

    console.log(`robot_pedidos created: id=${data.id} secuencia=${data.secuencia} place=${data.place}`);
    return json({ success: true, pedido: data });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
