package com.spatium.deamon.db.temi.core

import android.net.Uri
import com.spatium.deamon.db.temi.net.SupabaseGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class CheckinResult(
    val guestName: String,
    val contactName: String,
    val messageToSpeak: String,
    val alreadyCheckedIn: Boolean = false,
)

class CheckinHandler(private val gateway: SupabaseGateway) {

    suspend fun handle(uri: Uri): CheckinResult? {
        if (uri.scheme != "mytemi" || uri.host != "guest") return null
        val guestId = uri.getQueryParameter("id") ?: return null
        return handleById(guestId)
    }

    internal suspend fun handleById(guestId: String): CheckinResult? {
        if (guestId.isBlank()) return null
        val response = withContext(Dispatchers.IO) {
            gateway.post(
                "robot-invitado-checkin",
                buildJsonObject { put("guest_id", guestId) },
            )
        }

        val obj = response.jsonObject
        val alreadyCheckedIn = obj["already_checked_in"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val guestName = obj["guest_name"]?.jsonPrimitive?.content ?: "Invitado"
        val contactName = obj["contact_name"]?.jsonPrimitive?.content ?: ""
        val messageToSpeak = obj["message_to_speak"]?.jsonPrimitive?.content
            ?: "Bienvenido/a $guestName. Le hemos notificado a $contactName que está en el lobby. Por favor tome asiento."

        return CheckinResult(
            guestName = guestName,
            contactName = contactName,
            messageToSpeak = messageToSpeak,
            alreadyCheckedIn = alreadyCheckedIn,
        )
    }
}
