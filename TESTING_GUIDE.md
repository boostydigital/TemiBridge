# Guía de Testing - Sistema de Envío Automático WhatsApp

## Estado Actual
- ✅ Compilación: Exitosa
- ✅ Instalación: Completada en robot TEMI (192.168.191.67)
- ⏳ Testing: En progreso

## Requisitos Previos
1. **Accesibilidad Habilitada**: Ir a Configuración > Accesibilidad > Servicios > Habilitar "Deamon DB TEMI"
2. **WhatsApp Instalado**: Versión estándar o Business
3. **Contacto Configurado**: Número +1 (849) 282-5765 en WhatsApp
4. **Conexión de Red**: Robot conectado a la misma red

## Flujo de Testing

### Paso 1: Verificar Servicio de Accesibilidad
```bash
adb -s 192.168.191.67:5555 shell settings get secure enabled_accessibility_services
```
Debe mostrar: `com.spatium.deamon.db.temi/.ui.WhatsAppAccessibilityService`

### Paso 2: Limpiar Logs
```bash
adb -s 192.168.191.67:5555 logcat -c
```

### Paso 3: Monitorear Logs en Tiempo Real
```bash
adb -s 192.168.191.67:5555 logcat WhatsAppA11y:D SharedData:D *:E -v threadtime
```

### Paso 4: Ejecutar Test en el Robot
1. Abrir app "Deamon DB TEMI"
2. Presionar botón "Me encanta" (heart icon)
3. Seleccionar foto de la galería
4. Presionar "Compartir a WhatsApp"
5. **Observar**: La app debe:
   - Abrir WhatsApp automáticamente
   - Detectar el botón de envío
   - Hacer click automáticamente
   - Retornar a MainActivity

### Paso 5: Validar Resultados

#### Logs Esperados
```
[WhatsAppA11y] ✓ WhatsAppAccessibilityService conectado
[WhatsAppA11y] [EVENT] Evento en WhatsApp: 2048
[WhatsAppA11y] [STATE] WhatsApp abierto, esperando UI...
[WhatsAppA11y] [SEND] ✓ Botón encontrado por ID: com.whatsapp:id/send
[WhatsAppA11y] [SEND] ✓ Click realizado
[SharedData] Estado: WAITING_FOR_SEND_BUTTON → COMPLETED
```

#### Comportamiento Esperado
- ✅ Foto se envía automáticamente
- ✅ No requiere intervención manual
- ✅ App retorna a MainActivity
- ✅ Mensaje aparece en chat de WhatsApp

## Troubleshooting

### Problema: "Botón no encontrado"
**Solución**: Verificar que WhatsApp está actualizado
```bash
adb -s 192.168.191.67:5555 shell dumpsys package com.whatsapp | grep versionName
```

### Problema: "Servicio no habilitado"
**Solución**: Habilitar manualmente en Configuración > Accesibilidad

### Problema: "Click no se realiza"
**Solución**: Revisar logs para ver qué estrategia se está usando
- Si usa ID: Verificar que el ID es correcto
- Si usa Texto: Verificar idioma de WhatsApp
- Si usa Heurística: Verificar posición del botón

### Problema: "App no retorna"
**Solución**: Verificar que SharedData.setCompleted() se llamó
```bash
adb -s 192.168.191.67:5555 logcat | grep "COMPLETED"
```

## Métricas de Éxito

| Métrica | Objetivo | Cómo Validar |
|---------|----------|--------------|
| Detección del botón | <2s | Timestamp en logs |
| Click exitoso | 100% | "Click realizado" en logs |
| Retorno a MainActivity | Automático | Verificar visualmente |
| Tasa de éxito | >95% | 19/20 intentos exitosos |

## Comandos Útiles

### Ver estado actual de la app
```bash
adb -s 192.168.191.67:5555 shell dumpsys activity | grep -A 5 "MainActivity"
```

### Ver permisos otorgados
```bash
adb -s 192.168.191.67:5555 shell pm list permissions -g | grep -A 20 "com.spatium"
```

### Reiniciar servicio de accesibilidad
```bash
adb -s 192.168.191.67:5555 shell am force-stop com.spatium.deamon.db.temi
adb -s 192.168.191.67:5555 shell am start -n com.spatium.deamon.db.temi/.ui.MainActivity
```

### Capturar screenshot
```bash
adb -s 192.168.191.67:5555 shell screencap -p /sdcard/screenshot.png
adb -s 192.168.191.67:5555 pull /sdcard/screenshot.png
```

## Próximos Pasos Después del Testing

1. **Si es exitoso**: Documentar resultados y cerrar issue
2. **Si hay problemas**: 
   - Revisar logs detallados
   - Ajustar constantes en `WhatsAppConstants.kt`
   - Probar con diferentes versiones de WhatsApp
   - Implementar estrategias adicionales si es necesario

---

**Última actualización**: Marzo 2026
**Estado**: Sistema compilado e instalado, listo para testing
