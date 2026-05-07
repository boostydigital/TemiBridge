package com.spatium.deamon.db.temi.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Payload returned by the `guia-pendiente` Edge Function.
 * Maps 1:1 to the `public.guias` table columns relevant to the Android client.
 *
 * Parsing: [fromJson] uses [ignoreUnknownKeys] = true so future Supabase columns
 * do not break existing app versions.
 *
 * Required fields (will throw [kotlinx.serialization.SerializationException] if missing):
 * - [id], [nombreEvento], [waypointInicial], [waypointFinal], [horaInicio], [bienvenidaTts], [expiresAt]
 *
 * Optional fields default to null (or the explicit default shown):
 * - [descripcion], [imagenFondoUrl], [videoLoopUrl], [llegadaTts]
 * - [etiquetaBoton] defaults to "Iniciar" (matches DB column default)
 */
@Serializable
data class GuiaPayload(
    val id: String,
    @SerialName("nombre_evento") val nombreEvento: String,
    val descripcion: String? = null,
    @SerialName("waypoint_inicial") val waypointInicial: String,
    @SerialName("waypoint_final") val waypointFinal: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("imagen_fondo_url") val imagenFondoUrl: String? = null,
    @SerialName("video_loop_url") val videoLoopUrl: String? = null,
    @SerialName("bienvenida_tts") val bienvenidaTts: String,
    @SerialName("llegada_tts") val llegadaTts: String? = null,
    @SerialName("etiqueta_boton") val etiquetaBoton: String = "Iniciar",
    @SerialName("expires_at") val expiresAt: String,
) {

    /**
     * Parsed expiration as a JVM [Instant].
     * Accessing this property will throw if [expiresAt] is not a valid ISO-8601 string.
     * Lazily evaluated so parsing only happens when needed.
     */
    val expiresAtInstant: Instant
        get() = OffsetDateTime.parse(expiresAt).toInstant()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Deserializes a JSON string into a [GuiaPayload].
         * Throws [kotlinx.serialization.SerializationException] if a required field is absent
         * or the JSON structure is invalid.
         */
        fun fromJson(raw: String): GuiaPayload = json.decodeFromString(raw)
    }
}
