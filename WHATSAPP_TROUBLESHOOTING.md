# Guía de Troubleshooting - WhatsApp Automation

## 🚨 Problema: El botón de enviar no se presiona automáticamente

### Síntomas
- WhatsApp se abre con la foto adjunta ✓
- La foto se ve correctamente ✓
- El botón de enviar NO se presiona automáticamente ✗

---

## 🔍 Pasos de Diagnóstico

### Paso 1: Verificar que el servicio está habilitado

1. Ve a **Configuración** > **Accesibilidad**
2. Busca **"WhatsAppAccessibilityService"**
3. Asegúrate de que esté **ACTIVADO**
4. Si no está, actívalo y vuelve a intentar

### Paso 2: Verificar los logs

```bash
# En una terminal
adb logcat | grep WhatsAppA11y
```

**Deberías ver:**
```
✓ Servicio de accesibilidad conectado
✓ Paquetes monitoreados: com.whatsapp, com.whatsapp.w4b
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Evento recibido: WINDOW_STATE_CHANGED
Estado actual: WAITING_FOR_WHATSAPP_OPEN
⏳ WhatsApp detectado, esperando 1500ms...
🔍 Buscando botón... (intento 1/10)
```

### Paso 3: Usar el script de diagnóstico

```bash
chmod +x diagnose_whatsapp.sh
./diagnose_whatsapp.sh
```

---

## 🐛 Problemas Comunes y Soluciones

### Problema 1: Servicio no detecta eventos

**Síntomas en logs:**
- No aparece "Evento recibido"
- Solo aparece "Servicio conectado" pero nada más

**Solución:**
1. Reinicia el servicio de accesibilidad:
   - Desactívalo en Configuración
   - Vuelve a activarlo
   - Reinicia la app

2. Verifica la configuración del servicio en `accessibility_service_config.xml`:
   ```xml
   android:accessibilityEventTypes="typeAllMask"
   android:notificationTimeout="100"
   ```

### Problema 2: El botón no se encuentra

**Síntomas en logs:**
```
🔍 Buscando botón... (intento 1/10)
📌 Estrategia 1: Buscando por ID...
📝 Estrategia 2: Buscando por texto...
📝 Estrategia 3: Buscando por contentDescription...
📍 Estrategia 4: Buscando por posición...
❌ No se encontró el botón con ninguna estrategia
```

**Solución:**
1. Revisa el dump de jerarquía que aparece en los logs (primeros 2 intentos)
2. Busca elementos con `click=✓` en la esquina inferior derecha
3. Si encuentras el botón pero con un ID diferente, agrégalo a `SEND_BUTTON_IDS`

**Ejemplo de dump:**
```
│  ├�─ [ImageButton] id=send text=null desc=Send click=✓ bounds=[1234,567,1345,678]
```

### Problema 3: GestureClick falla

**Síntomas en logs:**
```
✅ Botón encontrado por ID: com.whatsapp:id/send
🖱️ Intentando click (método: ID:com.whatsapp:id/send)
❌ ACTION_CLICK falló
🎯 dispatchGesture: false
```

**Solución:**
1. Verifica que la app tiene permiso `SYSTEM_ALERT_WINDOW`
2. Prueba aumentar `GESTURE_DURATION_MS` a 250L
3. Verifica que el dispositivo tenga la pantalla encendida

### Problema 4: Timeout demasiado corto

**Síntomas en logs:**
```
⏳ WhatsApp detectado, esperando 1500ms...
[Se muestran los logs pero el botón aparece después]
```

**Solución:**
Aumenta `INITIAL_DELAY_MS` en el servicio:
```kotlin
private const val INITIAL_DELAY_MS = 3000L  // Aumentar a 3 segundos
```

---

## 📊 Checklist de Verificación

Antes de reportar un problema, verifica:

- [ ] El servicio de accesibilidad está HABILITADO
- [ ] La app está instalada en el dispositivo
- [ ] WhatsApp está instalado (versión normal o Business)
- [ ] Los logs muestran "Servicio conectado"
- [ ] Los logs muestran "Evento recibido" cuando abres WhatsApp
- [ ] Los logs muestran el dump de jerarquía (primeros 2 intentos)
- [ ] El número de teléfono en `PhotoPreviewActivity` es correcto
- [ ] La foto se está adjuntando correctamente en WhatsApp

---

## 🔧 Soluciones Avanzadas

### Solución 1: Usar UI Automator para encontrar IDs correctos

```kotlin
// Ejecutar en dispositivo
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml
```

Luego busca los IDs de los elementos en el XML generado.

### Solución 2: Habilitar logs verbose

En `WhatsAppAccessibilityService.kt`, agrega:
```kotlin
Log.v(TAG, "Package: ${event.packageName}")
Log.v(TAG, "Class: ${event.className}")
```

### Solución 3: Verificar permisos

```bash
adb shell dumpsys package com.spatium.deamon.db.temi | grep -A 20 "declared permissions"
```

---

## 📞 Cómo Reportar Problemas

Cuando reports un problema, incluye:

1. **Logs completos** de `adb logcat | grep WhatsAppA11y`
2. **Versión de WhatsApp** (normal o Business)
3. **Versión de Android** del dispositivo
4. **Modelo del dispositivo**
5. **Screenshot** de la pantalla de WhatsApp con la foto adjunta

---

## 🎯 Prueba Rápida

Para verificar si el servicio funciona:

1. Abre WhatsApp manualmente
2. Ve a cualquier chat
3. Presiona el botón de adjuntar (+)
4. Selecciona una foto
5. Verifica que el botón de enviar se encuentre

Si el botón se encuentra en el dump, el problema es de timing. Si no se encuentra, el problema es de detección.
