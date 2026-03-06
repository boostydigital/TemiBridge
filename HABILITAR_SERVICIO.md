# Habilitar Servicio de Accesibilidad - INSTRUCCIONES CRÍTICAS

## ⚠️ IMPORTANTE: El servicio de accesibilidad DEBE estar habilitado manualmente

El servicio de accesibilidad **no se inicia automáticamente**. Debe habilitarse manualmente en el robot.

## Pasos para Habilitar en TEMI

### Opción 1: Habilitar Manualmente en el Robot (RECOMENDADO)

1. **En el robot TEMI**:
   - Presionar el botón de Configuración (engranaje)
   - Ir a: **Configuración > Accesibilidad**
   - Buscar: **"Deamon DB TEMI"** o **"WhatsApp"**
   - **Presionar el toggle para HABILITAR**
   - Confirmar los permisos si aparece un diálogo

2. **Verificar que está habilitado**:
   - Debe aparecer en la lista de servicios activos
   - Debe haber un ícono de accesibilidad en la barra de estado

### Opción 2: Habilitar desde Terminal (ALTERNATIVA)

```bash
adb -s 192.168.191.67:5555 shell settings put secure enabled_accessibility_services com.spatium.deamon.db.temi/.ui.WhatsAppAccessibilityService
```

### Verificar que está habilitado

```bash
adb -s 192.168.191.67:5555 shell settings get secure enabled_accessibility_services
```

**Resultado esperado:**
```
com.spatium.deamon.db.temi/.ui.WhatsAppAccessibilityService
```

## Flujo de Prueba COMPLETO

1. ✅ **Habilitar servicio de accesibilidad** (CRÍTICO)
2. ✅ Abrir app "Deamon DB TEMI"
3. ✅ Presionar botón "Me encanta" (corazón)
4. ✅ Seleccionar foto de la galería
5. ✅ Presionar "Compartir a WhatsApp"
6. ✅ **Observar**: WhatsApp debe abrir y enviar automáticamente

## Logs para Debugging

Si el servicio está habilitado, deberías ver estos logs:

```
✓ Servicio de accesibilidad conectado
✓ Paquetes monitoreados: com.whatsapp, com.whatsapp.w4b
✓ Esperando eventos de accesibilidad...
✓ Configuración del servicio aplicada programáticamente
```

## Si Aún No Funciona

1. **Verificar que el servicio está habilitado**:
   ```bash
   adb -s 192.168.191.67:5555 shell settings get secure enabled_accessibility_services
   ```

2. **Ver logs en tiempo real**:
   ```bash
   adb -s 192.168.191.67:5555 logcat WhatsAppA11y:D -v threadtime
   ```

3. **Si no hay logs del servicio**:
   - El servicio NO está habilitado
   - Habilitar manualmente en Configuración > Accesibilidad

## Estado Actual

- ✅ App compilada e instalada
- ✅ Servicio de accesibilidad declarado en Manifest
- ✅ Archivo de configuración creado
- ⏳ **PENDIENTE**: Habilitar servicio en Configuración > Accesibilidad
