package com.spatium.deamon.db.temi.core

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GuiaPayloadParserTest {

    private val fullJson = """
        {
            "id": "abc-123",
            "nombre_evento": "Expo Tech 2026",
            "descripcion": "Tour por la sala principal",
            "waypoint_inicial": "recepcion",
            "waypoint_final": "sala_b",
            "hora_inicio": "2026-04-27T18:00:00Z",
            "imagen_fondo_url": "https://example.com/bg.jpg",
            "video_loop_url": "https://example.com/loop.mp4",
            "bienvenida_tts": "Bienvenido al tour",
            "llegada_tts": "Hemos llegado",
            "etiqueta_boton": "Iniciar Tour",
            "expires_at": "2026-04-27T20:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `happy path - full JSON decodes correctly`() {
        val payload = GuiaPayload.fromJson(fullJson)

        assertEquals("abc-123", payload.id)
        assertEquals("Expo Tech 2026", payload.nombreEvento)
        assertEquals("Tour por la sala principal", payload.descripcion)
        assertEquals("recepcion", payload.waypointInicial)
        assertEquals("sala_b", payload.waypointFinal)
        assertEquals("2026-04-27T18:00:00Z", payload.horaInicio)
        assertEquals("https://example.com/bg.jpg", payload.imagenFondoUrl)
        assertEquals("https://example.com/loop.mp4", payload.videoLoopUrl)
        assertEquals("Bienvenido al tour", payload.bienvenidaTts)
        assertEquals("Hemos llegado", payload.llegadaTts)
        assertEquals("Iniciar Tour", payload.etiquetaBoton)
        assertEquals("2026-04-27T20:00:00Z", payload.expiresAt)
    }

    @Test
    fun `missing optional fields default to null`() {
        val json = """
            {
                "id": "abc-456",
                "nombre_evento": "Evento Minimo",
                "waypoint_inicial": "entrada",
                "waypoint_final": "sala_a",
                "hora_inicio": "2026-04-27T18:00:00Z",
                "bienvenida_tts": "Bienvenido",
                "expires_at": "2026-04-27T20:00:00Z"
            }
        """.trimIndent()

        val payload = GuiaPayload.fromJson(json)

        assertNull(payload.descripcion)
        assertNull(payload.imagenFondoUrl)
        assertNull(payload.videoLoopUrl)
        assertNull(payload.llegadaTts)
        // etiqueta_boton has default value "Iniciar"
        assertEquals("Iniciar", payload.etiquetaBoton)
    }

    @Test
    fun `missing required field bienvenida_tts throws SerializationException`() {
        val json = """
            {
                "id": "abc-789",
                "nombre_evento": "Sin TTS",
                "waypoint_inicial": "entrada",
                "waypoint_final": "sala_a",
                "hora_inicio": "2026-04-27T18:00:00Z",
                "expires_at": "2026-04-27T20:00:00Z"
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            GuiaPayload.fromJson(json)
        }
    }

    @Test
    fun `unknown extra fields are silently ignored`() {
        val json = """
            {
                "id": "abc-000",
                "nombre_evento": "Con Extras",
                "waypoint_inicial": "a",
                "waypoint_final": "b",
                "hora_inicio": "2026-04-27T18:00:00Z",
                "bienvenida_tts": "Hola",
                "expires_at": "2026-04-27T20:00:00Z",
                "campo_desconocido": "valor_ignorado",
                "otro_campo": 42
            }
        """.trimIndent()

        // Should not throw - ignoreUnknownKeys = true
        val payload = GuiaPayload.fromJson(json)
        assertEquals("Con Extras", payload.nombreEvento)
    }

    @Test
    fun `bad ISO-8601 in expires_at propagates when expiresAtInstant is accessed`() {
        val json = """
            {
                "id": "bad-ts",
                "nombre_evento": "Timestamp Malo",
                "waypoint_inicial": "a",
                "waypoint_final": "b",
                "hora_inicio": "2026-04-27T18:00:00Z",
                "bienvenida_tts": "Hola",
                "expires_at": "not-a-valid-timestamp"
            }
        """.trimIndent()

        val payload = GuiaPayload.fromJson(json)

        // The field parses fine as a string, but accessing the computed Instant property throws
        assertThrows(Exception::class.java) {
            payload.expiresAtInstant
        }
    }

    @Test
    fun `missing required field id throws SerializationException`() {
        val json = """
            {
                "nombre_evento": "Sin ID",
                "waypoint_inicial": "entrada",
                "waypoint_final": "sala_a",
                "hora_inicio": "2026-04-27T18:00:00Z",
                "bienvenida_tts": "Bienvenido",
                "expires_at": "2026-04-27T20:00:00Z"
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            GuiaPayload.fromJson(json)
        }
    }
}
