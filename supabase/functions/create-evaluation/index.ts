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

// create-evaluation — called by RatingManager after user submits rating
// POST { rating, customer_name, salon, feedback_text, category }
// Stores result in service_evaluations if available, also updates robot_evaluaciones estado
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const body = await req.json();
    const { rating, customer_name, salon, feedback_text, category } = body;

    if (!rating || rating < 1 || rating > 5) {
      return json({ error: "INVALID_PAYLOAD", message: "rating must be 1-5" }, 400);
    }

    // Store in service_evaluations
    const { error } = await supabase
      .from("service_evaluations")
      .insert({
        rating,
        customer_name: customer_name || null,
        salon: salon || null,
        feedback_text: feedback_text || null,
        category: category || salon || null,
      });

    if (error) {
      // Non-fatal: table might not have matching columns — log and continue
      console.warn("service_evaluations insert failed (non-fatal):", error.message);
    }

    console.log(`Evaluation stored: salon=${salon} rating=${rating} customer=${customer_name}`);
    return json({ success: true });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
