---
name: temi-permissions
description: Gestión completa de permisos del SDK de Temi en Android/Kotlin. Usar cuando se necesite declarar, verificar o solicitar permisos de Temi (FACE_RECOGNITION, MAP, SETTINGS, SEQUENCE), corregir permisos en AndroidManifest.xml, o cuando el robot no muestre la app en la configuración de permisos.
---

# Temi SDK - Gestión de Permisos

## Fuente oficial
https://github.com/robotemi/sdk/wiki/Permission

---

## 1. PERMISOS DISPONIBLES

```kotlin
package com.robotemi.sdk.permission

enum Permission {
    FACE_RECOGNITION,  // Reconocimiento facial y face tracking
    MAP,               // Acceso al mapa y navegación
    SETTINGS,          // Modificar configuración del sistema del robot
    SEQUENCE           // Ejecutar secuencias programadas
}
```

> ⚠️ Solo skills en **Kiosk Mode** pueden solicitar permisos kiosk.
> Desde versión 0.10.72, los permisos NO necesitan solicitarse en Kiosk mode.

---

## 2. DECLARAR PERMISOS EN AndroidManifest.xml

### Regla crítica: formato correcto del nombre
```
✅ CORRECTO:   com.robotemi.permission.face_recognition
❌ INCORRECTO: com.robotemi.sdk.permission.FACE_RECOGNITION
❌ INCORRECTO: com.robotemi.permission.FACE_RECOGNITION
```

### Declarar UN permiso
```xml
<application>
    ...
    <meta-data
        android:name="@string/metadata_permissions"
        android:value="com.robotemi.permission.settings" />
    ...
</application>
```

### Declarar MÚLTIPLES permisos (separados por coma)
```xml
<application>
    ...
    <meta-data
        android:name="@string/metadata_permissions"
        android:value="com.robotemi.permission.settings,com.robotemi.permission.face_recognition,com.robotemi.permission.map,com.robotemi.permission.sequence" />
    ...
</application>
```

### También declarar `<uses-permission>` para cada uno
```xml
<manifest>
    <!-- Permisos Android estándar -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <!-- Permisos del SDK de Temi (formato minúsculas, sin "sdk") -->
    <uses-permission android:name="com.robotemi.permission.sequence" />
    <uses-permission android:name="com.robotemi.permission.face_recognition" />
    <uses-permission android:name="com.robotemi.permission.map" />
    <uses-permission android:name="com.robotemi.permission.settings" />
</manifest>
```

---

## 3. VERIFICAR PERMISOS EN RUNTIME (Kotlin)

```kotlin
import com.robotemi.sdk.Robot
import com.robotemi.sdk.permission.Permission

// Siempre verificar ANTES de usar cualquier feature que lo requiera
fun hasPermission(permission: Permission): Boolean {
    val robot = Robot.getInstance() ?: return false
    return robot.checkSelfPermission(permission) == Permission.GRANTED
}

// Ejemplo de uso
if (hasPermission(Permission.FACE_RECOGNITION)) {
    // Activar face tracking
} else {
    // Solicitar permiso o mostrar aviso
}
```

---

## 4. SOLICITAR PERMISOS (Kotlin)

```kotlin
import com.robotemi.sdk.Robot
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.listeners.OnRequestPermissionResultListener

class MainActivity : AppCompatActivity(), OnRequestPermissionResultListener {

    companion object {
        const val REQUEST_CODE_FACE = 1001
        const val REQUEST_CODE_MAP  = 1002
    }

    private val robot by lazy { Robot.getInstance() }

    override fun onStart() {
        super.onStart()
        robot?.addOnRequestPermissionResultListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot?.removeOnRequestPermissionResultListener(this)
    }

    // Solicitar un permiso individual
    fun requestFacePermission() {
        robot?.requestPermissions(
            listOf(Permission.FACE_RECOGNITION),
            REQUEST_CODE_FACE
        )
    }

    // Solicitar múltiples permisos a la vez
    fun requestAllPermissions() {
        robot?.requestPermissions(
            listOf(Permission.FACE_RECOGNITION, Permission.MAP, Permission.SETTINGS),
            REQUEST_CODE_FACE
        )
    }

    // Callback resultado de solicitud
    override fun onRequestPermissionResult(
        permission: Permission,
        grantResult: Int,
        requestCode: Int
    ) {
        when (grantResult) {
            Permission.GRANTED -> {
                Log.d("Permisos", "✅ Permiso concedido: $permission (code=$requestCode)")
                // Continuar con la acción que requería el permiso
            }
            Permission.DENIED -> {
                Log.w("Permisos", "❌ Permiso denegado: $permission")
            }
        }
    }
}
```

---

## 5. PATRÓN RECOMENDADO CON REFLEXIÓN (sin importar el SDK directamente)

Para proyectos que usan reflexión para compatibilidad:

```kotlin
fun checkTemiPermission(permissionName: String): Boolean {
    return try {
        val robot = Robot.getInstance() ?: return false
        val permissionClass = Class.forName("com.robotemi.sdk.permission.Permission")
        val permission = java.lang.Enum.valueOf(
            permissionClass as Class<Enum<*>>,
            permissionName  // "FACE_RECOGNITION", "MAP", "SETTINGS", "SEQUENCE"
        )
        val checkMethod = robot.javaClass.getMethod("checkSelfPermission", permissionClass)
        val result = checkMethod.invoke(robot, permission) as Int
        result == 0 // 0 = GRANTED
    } catch (e: Exception) {
        Log.e("TemiPerms", "Error verificando permiso $permissionName: ${e.message}")
        false
    }
}
```

---

## 6. DIAGNÓSTICO: La app no aparece en la UI de permisos del robot

Checklist de verificación:

1. **¿El `<meta-data>` está dentro de `<application>`?** (NO dentro de `<activity>`)
2. **¿El `android:name` es exactamente `@string/metadata_permissions`?**
3. **¿Los nombres de permiso están en minúsculas?** (`face_recognition`, no `FACE_RECOGNITION`)
4. **¿No tienen prefijo `sdk`?** (`com.robotemi.permission.X`, no `com.robotemi.sdk.permission.X`)
5. **¿La app está configurada como Kiosk Mode skill?**
6. **¿Se reinstalo la app después de cambiar el manifest?**

---

## 7. TABLA COMPLETA DE PERMISOS

| Permiso SDK (enum) | Nombre en manifest | Descripción | Requiere Kiosk |
|---|---|---|---|
| `FACE_RECOGNITION` | `com.robotemi.permission.face_recognition` | Face tracking, reconocimiento facial | No |
| `MAP` | `com.robotemi.permission.map` | Acceso y edición del mapa | No |
| `SETTINGS` | `com.robotemi.permission.settings` | Modificar configuración del sistema | No |
| `SEQUENCE` | `com.robotemi.permission.sequence` | Ejecutar secuencias | No |

---

## 8. MÉTODOS SDK DISPONIBLES

| Método | Firma | Descripción |
|---|---|---|
| `checkSelfPermission` | `checkSelfPermission(Permission): Int` | Retorna `GRANTED(0)` o `DENIED(1)` |
| `requestPermissions` | `requestPermissions(List<Permission>, Int)` | Muestra diálogo de solicitud |

**Listener:** `OnRequestPermissionResultListener` → `onRequestPermissionResult(Permission, Int, Int)`
