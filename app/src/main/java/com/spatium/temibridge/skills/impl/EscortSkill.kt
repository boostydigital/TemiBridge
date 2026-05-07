package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Skill compuesto para escolta de visitantes a un destino con saludo y retorno.
 *
 * Parámetros:
 *  - greeting (String, requerido): mensaje de bienvenida antes de navegar
 *  - place (String, opcional): waypoint destino
 *  - arrivalGreeting (String, opcional): mensaje al llegar al destino
 *  - waitTimeMs (Long, opcional): ms de espera en destino antes de regresar (default 10_000)
 *  - returnTo (String, opcional): waypoint de retorno después de llegar
 *  - greetingDelayMs (Long, opcional): ms de espera entre saludo y navegación (default 3_000)
 *
 * Flujo de ejecución:
 *  1. Habla el saludo de bienvenida
 *  2. (Espera greetingDelayMs ms)
 *  3. Configura callback de llegada (si aplica)
 *  4. Navega al destino
 *  5. Al llegar: habla arrivalGreeting (si aplica)
 *  6. (Espera waitTimeMs ms)
 *  7. Regresa a returnTo (si aplica)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "escort", mapOf(
 *     "greeting" to "Por favor sígame, le acompaño a la sala de reuniones",
 *     "place" to "Sala_Reuniones",
 *     "arrivalGreeting" to "Hemos llegado a la sala de reuniones, que tenga un excelente día",
 *     "returnTo" to "entrada",
 *     "waitTimeMs" to 10000
 *   ))
 */
class EscortSkill :
    BaseTemiSkill(
        skillId = "escort",
        skillName = "Escort",
        description = "Escolta visitantes a su destino con saludo inicial, mensaje de llegada y retorno automático",
        category = SkillCategory.COMPOSITE,
    ) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val greeting = params["greeting"] as? String
        if (greeting.isNullOrBlank()) {
            return SkillResult.Error("Parámetro requerido ausente: 'greeting'")
        }

        val place = params["place"] as? String
        val arrivalGreeting = params["arrivalGreeting"] as? String
        val waitTimeMs = (params["waitTimeMs"] as? Number)?.toLong() ?: 10_000L
        val returnTo = params["returnTo"] as? String
        val greetingDelayMs = (params["greetingDelayMs"] as? Number)?.toLong() ?: 3_000L

        logInfo("Escolta iniciada: place=$place returnTo=$returnTo waitTimeMs=$waitTimeMs")

        return withContext(Dispatchers.Main) {
            // Paso 1: Saludo de bienvenida
            logInfo("Paso 1: Hablando saludo: '$greeting'")
            TemiController.speak(greeting)

            // Paso 2: Configurar callback de llegada si hay destino
            if (!place.isNullOrBlank()) {
                TemiController.setArrivalCallbackOnce {
                    logInfo("Llegada a '$place' detectada, ejecutando acciones de llegada")
                    if (!arrivalGreeting.isNullOrBlank()) {
                        TemiController.speak(arrivalGreeting)
                    }
                    if (!returnTo.isNullOrBlank()) {
                        logInfo("Programando retorno a '$returnTo' en ${waitTimeMs}ms")
                        Handler(Looper.getMainLooper()).postDelayed({
                            logInfo("Regresando a '$returnTo'")
                            TemiController.goTo(returnTo)
                        }, waitTimeMs)
                    }
                }

                // Paso 3: Navegar al destino después del delay del saludo
                logInfo("Paso 3: Navegando a '$place' en ${greetingDelayMs}ms")
                withContext(Dispatchers.IO) { delay(greetingDelayMs) }
                withContext(Dispatchers.Main) {
                    TemiController.goTo(place)
                    logInfo("Comando goTo('$place') enviado")
                }
            }

            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
