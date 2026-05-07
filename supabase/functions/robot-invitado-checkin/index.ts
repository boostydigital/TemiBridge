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

// Terminal statuses — guest already processed, skip re-notification
const TERMINAL_STATUSES = ["bienvenido", "menu_abierto", "notificado"];

// robot-invitado-checkin — idempotent guest check-in via QR scan
// POST { guest_id }
// Called by CheckinHandler (Android) when MainActivity decodes mytemi://guest?id=UUID
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const { guest_id } = body;

    if (!guest_id || typeof guest_id !== "string" || guest_id.trim() === "") {
      return json({ error: "INVALID_PAYLOAD", message: "guest_id is required" }, 400);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // 1. Look up guest info from spatium-hub guests table
    const { data: guest, error: guestError } = await supabase
      .from("guests")
      .select("id, name, contact_name, contact_email")
      .eq("id", guest_id.trim())
      .maybeSingle();

    if (guestError) {
      console.error("Error fetching guest:", guestError);
      return json({ error: "DB_ERROR", message: guestError.message }, 500);
    }

    if (!guest) {
      return json({ error: "NOT_FOUND", message: `Guest ${guest_id} not found` }, 404);
    }

    // 2. Check for existing check-in record
    const { data: existing, error: existingError } = await supabase
      .from("robot_invitados")
      .select("*")
      .eq("guest_id", guest_id.trim())
      .maybeSingle();

    if (existingError) {
      console.error("Error querying robot_invitados:", existingError);
      return json({ error: "DB_ERROR", message: existingError.message }, 500);
    }

    // 3a. Already processed — return existing data, DO NOT re-notify (idempotent)
    if (existing && TERMINAL_STATUSES.includes(existing.status)) {
      console.log(`Duplicate scan for guest ${guest_id} (status=${existing.status}) — skipping notification`);
      return json({
        success: true,
        already_checked_in: true,
        guest_name: guest.name,
        contact_name: guest.contact_name ?? "",
        message_to_speak: `Bienvenido/a de nuevo, ${guest.name}. Estamos contentos de verte.`,
      });
    }

    // 3b. First check-in (or status=pendiente) — upsert + notify
    const now = new Date().toISOString();

    const { error: upsertError } = await supabase
      .from("robot_invitados")
      .upsert(
        {
          guest_id: guest_id.trim(),
          status: "bienvenido",
          check_in_at: now,
        },
        { onConflict: "guest_id" }
      );

    if (upsertError) {
      console.error("Error upserting robot_invitados:", upsertError);
      return json({ error: "DB_ERROR", message: upsertError.message }, 500);
    }

    // 4. Fire notification (Telegram via send-guest-notification if available)
    try {
      const notifyUrl = `${Deno.env.get("SUPABASE_URL")}/functions/v1/send-guest-notification`;
      const notifyRes = await fetch(notifyUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
        },
        body: JSON.stringify({
          type: "checked_in",
          guest_id: guest_id.trim(),
          guest_name: guest.name,
          contact_name: guest.contact_name ?? "",
          contact_email: guest.contact_email ?? "",
        }),
      });

      if (!notifyRes.ok) {
        const err = await notifyRes.text();
        console.warn(`send-guest-notification failed (non-fatal): ${err}`);
      } else {
        // Update contact_notified_at
        await supabase
          .from("robot_invitados")
          .update({ contact_notified_at: new Date().toISOString() })
          .eq("guest_id", guest_id.trim());
      }
    } catch (notifyErr) {
      // Notification failure is non-fatal — robot still greets the guest
      console.warn("Notification call failed (non-fatal):", notifyErr);
    }

    const messageToSpeak = `Bienvenido/a ${guest.name}. Le hemos notificado a ${guest.contact_name ?? "su contacto"} que está en el lobby. Por favor tome asiento.`;

    console.log(`Check-in successful: guest=${guest.name} contact=${guest.contact_name}`);
    return json({
      success: true,
      already_checked_in: false,
      guest_name: guest.name,
      contact_name: guest.contact_name ?? "",
      message_to_speak: messageToSpeak,
    });
  } catch (err) {
    console.error("Unhandled error:", err);
    return json({ error: "INTERNAL_ERROR", message: String(err) }, 500);
  }
});
