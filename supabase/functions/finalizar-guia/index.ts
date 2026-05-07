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

const TERMINAL_STATES = new Set(["completada", "expirada", "cancelada"]);
const VALID_FINAL_STATES = TERMINAL_STATES;

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  try {
    const { id, estado_final } = await req.json();

    if (!id) {
      return json({ error: "MISSING_FIELD", field: "id", message: "El campo 'id' es requerido." }, 400);
    }

    if (!estado_final || !VALID_FINAL_STATES.has(estado_final)) {
      return json(
        {
          error: "VALIDATION_ERROR",
          field: "estado_final",
          message: "estado_final debe ser uno de: completada, expirada, cancelada.",
        },
        400
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Fetch the row first to check existence and current state
    const { data: row, error: fetchError } = await supabase
      .from("guias")
      .select("id, estado")
      .eq("id", id)
      .maybeSingle();

    if (fetchError) {
      console.error("Error fetching guia:", fetchError);
      return json({ error: "DB_ERROR", message: fetchError.message }, 500);
    }

    if (!row) {
      return json({ error: "NOT_FOUND", message: `No se encontró una guia con id '${id}'.` }, 404);
    }

    // Idempotent: already in a terminal state — return success without modifying
    if (TERMINAL_STATES.has(row.estado)) {
      return json({ success: true, guia: row });
    }

    // Transition to terminal state
    const { data: updated, error: updateError } = await supabase
      .from("guias")
      .update({
        estado: estado_final,
        finalizado_at: new Date().toISOString(),
      })
      .eq("id", id)
      .select()
      .single();

    if (updateError) {
      console.error("Error updating guia:", updateError);
      return json({ error: "DB_ERROR", message: updateError.message }, 500);
    }

    return json({ success: true, guia: updated });
  } catch (err) {
    console.error("Error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
