package com.spatium.deamon.db.temi.skills.registry

import android.util.Log
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.impl.EscortSkill
import com.spatium.deamon.db.temi.skills.impl.HeadTiltSkill
import com.spatium.deamon.db.temi.skills.impl.HtmlToNativeSkill
import com.spatium.deamon.db.temi.skills.impl.NativeTtsSkill
import com.spatium.deamon.db.temi.skills.impl.NavigationSkill
import com.spatium.deamon.db.temi.skills.impl.OrderSkill
import com.spatium.deamon.db.temi.skills.impl.SequenceSkill
import com.spatium.deamon.db.temi.skills.impl.SpeechSkill
import com.spatium.deamon.db.temi.skills.impl.TourSkill
import com.spatium.deamon.db.temi.skills.impl.VolumeSkill
import com.spatium.deamon.db.temi.skills.impl.WebViewSkill
import com.spatium.deamon.db.temi.skills.impl.WelcomeWorkflowSkill

/**
 * Inicializador central del sistema de skills.
 * Registra todos los skills disponibles en SkillRegistry y su metadata en SkillConfiguration.
 * Debe llamarse desde TemiDaemonApplication.onCreate() o equivalente.
 */
object SkillInitializer {

    private const val TAG = "SkillInitializer"

    fun initialize() {
        Log.d(TAG, "Inicializando sistema de skills...")
        registerSkills()
        registerMetadata()
        SkillRegistry.logAllSkills()
        Log.d(TAG, "Sistema de skills inicializado: ${SkillRegistry.getAllSkills().size} skills registrados")
    }

    private fun registerSkills() {
        SkillRegistry.registerAll(
            NavigationSkill(),
            SpeechSkill(),
            NativeTtsSkill(),
            SequenceSkill(),
            TourSkill(),
            HeadTiltSkill(),
            VolumeSkill(),
            WebViewSkill(),
            OrderSkill(),
            EscortSkill(),
            WelcomeWorkflowSkill(),
            HtmlToNativeSkill(),
        )
    }

    private fun registerMetadata() {
        // Navigation
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "navigation",
                name = "Navigation",
                description = "Navega a waypoints guardados en el mapa del robot Temi",
                category = SkillCategory.NAVIGATION,
                parameters = listOf(
                    ParameterSpec("place", ParameterType.STRING, true, "Nombre del waypoint destino"),
                    ParameterSpec("returnTo", ParameterType.STRING, false, "Waypoint de retorno tras llegar"),
                    ParameterSpec("returnDelayMs", ParameterType.NUMBER, false, "Milisegundos antes de regresar", 10_000),
                    ParameterSpec("onArrival", ParameterType.MAP, false, "Callback al llegar"),
                ),
                timeoutMillis = 60_000L,
                retryable = false,
            ),
        )

        // Speech
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "speech",
                name = "Speech",
                description = "Síntesis de voz usando TTS nativo del robot Temi",
                category = SkillCategory.SPEECH,
                parameters = listOf(
                    ParameterSpec("text", ParameterType.STRING, true, "Texto a sintetizar"),
                ),
                timeoutMillis = 30_000L,
                retryable = true,
            ),
        )

        // Native TTS
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "native_tts",
                name = "Native TTS",
                description = "Síntesis de voz avanzada usando TTS nativo del robot Temi con parámetros",
                category = SkillCategory.SPEECH,
                parameters = listOf(
                    ParameterSpec("text", ParameterType.STRING, true, "Texto a sintetizar"),
                    ParameterSpec("language", ParameterType.STRING, false, "Código de idioma (ej: es, en, fr)"),
                    ParameterSpec("speed", ParameterType.NUMBER, false, "Velocidad de habla (0.5-2.0)", 1.0f),
                    ParameterSpec("pitch", ParameterType.NUMBER, false, "Tono de voz (0.5-2.0)", 1.0f),
                ),
                timeoutMillis = 30_000L,
                retryable = true,
            ),
        )

        // Sequence
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "sequence",
                name = "Sequence",
                description = "Ejecuta secuencias predefinidas del robot Temi",
                category = SkillCategory.SEQUENCE,
                parameters = listOf(
                    ParameterSpec("sequenceId", ParameterType.STRING, false, "ID de la secuencia"),
                    ParameterSpec("sequenceName", ParameterType.STRING, false, "Nombre de la secuencia"),
                    ParameterSpec("withPlayer", ParameterType.BOOLEAN, false, "Mostrar reproductor", true),
                    ParameterSpec("repeat", ParameterType.NUMBER, false, "Número de repeticiones", 1),
                    ParameterSpec("startFromStep", ParameterType.NUMBER, false, "Paso inicial", 1),
                ),
                requiredPermissions = listOf("com.robotemi.sdk.permission.SEQUENCE"),
                timeoutMillis = 30_000L,
                retryable = false,
            ),
        )

        // Tour
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "tour",
                name = "Tour",
                description = "Inicia tours predefinidos del robot Temi",
                category = SkillCategory.TOUR,
                parameters = listOf(
                    ParameterSpec("tourId", ParameterType.STRING, false, "ID del tour"),
                    ParameterSpec("tourName", ParameterType.STRING, false, "Nombre del tour"),
                ),
                timeoutMillis = 30_000L,
                retryable = false,
            ),
        )

        // Head Tilt
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "head_tilt",
                name = "Head Tilt",
                description = "Controla el ángulo de inclinación de la cabeza del robot",
                category = SkillCategory.SYSTEM,
                parameters = listOf(
                    ParameterSpec("angle", ParameterType.NUMBER, true, "Ángulo en grados (-25 a 55)"),
                ),
                timeoutMillis = 5_000L,
                retryable = false,
            ),
        )

        // Volume
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "volume",
                name = "Volume",
                description = "Controla el nivel de volumen del robot Temi (0-10)",
                category = SkillCategory.SYSTEM,
                parameters = listOf(
                    ParameterSpec("level", ParameterType.NUMBER, true, "Nivel de volumen (0-10)"),
                ),
                timeoutMillis = 5_000L,
                retryable = false,
            ),
        )

        // WebView
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "webview",
                name = "WebView",
                description = "Muestra contenido web en pantalla completa",
                category = SkillCategory.INTERACTION,
                parameters = listOf(
                    ParameterSpec("url", ParameterType.STRING, true, "URL a mostrar (http/https)"),
                ),
                timeoutMillis = 10_000L,
                retryable = true,
            ),
        )

        // Order
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "order",
                name = "Order",
                description = "Abre la pantalla de pedidos interactiva",
                category = SkillCategory.INTERACTION,
                parameters = listOf(
                    ParameterSpec("place", ParameterType.STRING, false, "Ubicación del pedido", ""),
                    ParameterSpec("comidaUrl", ParameterType.STRING, false, "URL de imagen del menú"),
                ),
                timeoutMillis = 10_000L,
                retryable = true,
            ),
        )

        // Escort
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "escort",
                name = "Escort",
                description = "Escolta visitantes a su destino con saludo inicial, mensaje de llegada y retorno automático",
                category = SkillCategory.COMPOSITE,
                parameters = listOf(
                    ParameterSpec("greeting", ParameterType.STRING, true, "Mensaje de bienvenida"),
                    ParameterSpec("place", ParameterType.STRING, false, "Waypoint destino"),
                    ParameterSpec("arrivalGreeting", ParameterType.STRING, false, "Mensaje al llegar"),
                    ParameterSpec("waitTimeMs", ParameterType.NUMBER, false, "Espera en destino (ms)", 10_000),
                    ParameterSpec("returnTo", ParameterType.STRING, false, "Waypoint de retorno"),
                    ParameterSpec("greetingDelayMs", ParameterType.NUMBER, false, "Delay antes de navegar (ms)", 3_000),
                ),
                timeoutMillis = 120_000L,
                retryable = false,
            ),
        )

        // Welcome Workflow
        SkillConfiguration.register(
            SkillMetadata(
                skillId = "welcome_workflow",
                name = "Welcome Workflow",
                description = "Flujo completo de bienvenida: saludo + info web + navegación",
                category = SkillCategory.COMPOSITE,
                parameters = listOf(
                    ParameterSpec("text", ParameterType.STRING, false, "Texto de bienvenida", "¡Bienvenido!"),
                    ParameterSpec("place", ParameterType.STRING, false, "Waypoint destino"),
                    ParameterSpec("infoUrl", ParameterType.STRING, false, "URL a mostrar tras el saludo"),
                    ParameterSpec("infoDelayMs", ParameterType.NUMBER, false, "Delay antes de abrir URL (ms)", 5_000),
                    ParameterSpec("navDelayMs", ParameterType.NUMBER, false, "Delay antes de navegar (ms)", 3_000),
                ),
                timeoutMillis = 120_000L,
                retryable = false,
            ),
        )
    }
}
