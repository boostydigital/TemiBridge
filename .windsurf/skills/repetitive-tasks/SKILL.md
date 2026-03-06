---
name: repetitive-tasks-automation
description: Automatiza tareas repetitivas en TemiBridge como reintentos con backoff, manejo de permisos, logging estructurado y ejecución de secuencias
---

# Skill: Automatización de Tareas Repetitivas en TemiBridge

## Descripción General

Este skill proporciona utilidades reutilizables para automatizar patrones repetitivos en TemiBridge:

- **Retry Logic**: Reintentos con exponential backoff para operaciones de red
- **Permission Management**: Solicitud y validación de permisos con reintentos
- **Structured Logging**: Logging consistente con tags y niveles
- **Sequence Execution**: Ejecución de secuencias con validación de permisos

## Patrones Identificados

### 1. Retry con Exponential Backoff

**Ubicación actual**: `MenuActivity.kt` línea 383-419 (`sendWebhookWithRetry`)

**Problema**: Código duplicado en múltiples lugares

**Solución**: Crear utilidad genérica `RetryHelper`

```kotlin
// Uso:
RetryHelper.executeWithRetry(
    maxRetries = 3,
    operation = { /* tu operación */ },
    onFailure = { exception -> /* manejo de error */ }
)
```

### 2. Permission Handling

**Ubicación actual**: `MenuActivity.kt` línea 439-476 (`executeFarewellSequence`)

**Problema**: 
- Verificación de permiso duplicada en múltiples Activities
- Sin reintentos si el permiso falla
- Sin timeout para solicitud de permiso

**Solución**: Crear utilidad `PermissionHelper`

```kotlin
// Uso:
PermissionHelper.ensureSequencePermission(
    activity = this,
    onGranted = { /* ejecutar acción */ },
    onDenied = { /* manejar rechazo */ }
)
```

### 3. Structured Logging

**Ubicación actual**: Disperso en todo el código

**Problema**:
- Inconsistencia en formato de logs
- Difícil de filtrar en logcat
- Sin niveles de severidad claros

**Solución**: Crear utilidad `LogHelper`

```kotlin
// Uso:
LogHelper.info(TAG, "Operación completada")
LogHelper.error(TAG, "Error crítico", exception)
LogHelper.debug(TAG, "=== SECCIÓN IMPORTANTE ===")
```

### 4. Sequence Execution

**Ubicación actual**: `MenuActivity.kt` línea 439-476

**Problema**:
- Lógica acoplada a Activity
- Sin timeout
- Sin reintentos

**Solución**: Crear utilidad `SequenceHelper`

```kotlin
// Uso:
SequenceHelper.executeSequence(
    sequenceId = "694063d5bd16eddf28b772d8",
    activity = this,
    timeout = 30_000,
    onSuccess = { /* callback */ },
    onFailure = { /* callback */ }
)
```

## Implementación Recomendada

### Paso 1: Crear RetryHelper

```kotlin
// app/src/main/java/com/spatium/temibridge/utils/RetryHelper.kt

object RetryHelper {
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        backoffMultiplier: Double = 2.0,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delay = (delayMs * Math.pow(backoffMultiplier, (attempt - 1).toDouble())).toLong()
                    delay(delay)
                }
            }
        }
        
        throw lastException ?: Exception("Operación falló después de $maxRetries intentos")
    }
}
```

### Paso 2: Crear PermissionHelper

```kotlin
// app/src/main/java/com/spatium/temibridge/utils/PermissionHelper.kt

object PermissionHelper {
    fun ensureSequencePermission(
        activity: Activity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (TemiController.isSequencePermissionGranted()) {
            onGranted()
            return
        }
        
        val granted = TemiController.requestSequencePermission(activity)
        Thread.sleep(500) // Esperar procesamiento
        
        val hasPermission = TemiController.isSequencePermissionGranted()
        if (hasPermission) {
            onGranted()
        } else {
            onDenied()
        }
    }
}
```

### Paso 3: Crear LogHelper

```kotlin
// app/src/main/java/com/spatium/temibridge/utils/LogHelper.kt

object LogHelper {
    fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    fun warn(tag: String, message: String, exception: Throwable? = null) {
        if (exception != null) {
            Log.w(tag, message, exception)
        } else {
            Log.w(tag, message)
        }
    }
    
    fun error(tag: String, message: String, exception: Throwable? = null) {
        if (exception != null) {
            Log.e(tag, message, exception)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun section(tag: String, title: String) {
        Log.d(tag, "=== $title ===")
    }
}
```

### Paso 4: Crear SequenceHelper

```kotlin
// app/src/main/java/com/spatium/temibridge/utils/SequenceHelper.kt

object SequenceHelper {
    fun executeSequence(
        sequenceId: String,
        activity: Activity,
        timeout: Long = 30_000,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        PermissionHelper.ensureSequencePermission(
            activity = activity,
            onGranted = {
                val success = TemiController.playSequenceById(sequenceId)
                if (success) {
                    onSuccess()
                } else {
                    onFailure("No se pudo ejecutar la secuencia")
                }
            },
            onDenied = {
                onFailure("Permiso de secuencias denegado")
            }
        )
    }
}
```

## Refactorización de MenuActivity

Después de crear los helpers, refactorizar `MenuActivity.kt`:

```kotlin
// Antes:
private suspend fun sendWebhookWithRetry(webhookUrl: String, maxRetries: Int = 3) {
    // 20+ líneas de código
}

// Después:
private suspend fun sendWebhookWithRetry(webhookUrl: String, maxRetries: Int = 3) {
    return RetryHelper.executeWithRetry(maxRetries = maxRetries) {
        val connection = java.net.URL(webhookUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        connection.disconnect()
        
        if (responseCode !in 200..299) {
            throw Exception("HTTP $responseCode")
        }
    }
}
```

## Beneficios

✅ **Reutilización**: Mismo código en múltiples Activities
✅ **Mantenibilidad**: Cambios centralizados
✅ **Consistencia**: Mismo patrón en toda la app
✅ **Testing**: Fácil de testear en aislamiento
✅ **Escalabilidad**: Agregar nuevas operaciones sin duplicar lógica

## Próximos Pasos

1. Crear los 4 helpers en `app/src/main/java/com/spatium/temibridge/utils/`
2. Refactorizar `MenuActivity.kt` para usar los helpers
3. Refactorizar `OrderSuccessActivity.kt` para usar `SequenceHelper`
4. Crear tests unitarios para cada helper
5. Documentar en `README.md`

## Referencias

- `MenuActivity.kt`: Líneas 383-419 (sendWebhookWithRetry)
- `MenuActivity.kt`: Líneas 439-476 (executeFarewellSequence)
- `TemiController.kt`: Líneas 268-395 (Permission handling)
