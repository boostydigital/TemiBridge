# Habilitar Servicio de Accesibilidad - TEMI

## ⚠️ IMPORTANTE: El servicio de accesibilidad DEBE estar habilitado para que funcione el envío automático

### Pasos para Habilitar en TEMI:

1. **Abrir Configuración**
   - En la pantalla principal del robot, presionar el ícono de Configuración
   - O ir a: Menú > Configuración

2. **Navegar a Accesibilidad**
   - Configuración > Accesibilidad
   - O buscar "Accessibility" en la barra de búsqueda

3. **Habilitar Deamon DB TEMI**
   - Buscar "Deamon DB TEMI" o "WhatsApp Accessibility"
   - Presionar el toggle/switch para HABILITAR
   - Confirmar los permisos si aparece un diálogo

4. **Verificar que está Habilitado**
   - El servicio debe aparecer en la lista de servicios activos
   - Debe haber un ícono de accesibilidad en la barra de estado

### Verificar desde Terminal (Opcional):

```bash
adb -s 192.168.191.67:5555 shell settings get secure enabled_accessibility_services
```

**Resultado esperado:**
```
com.spatium.deamon.db.temi/.ui.WhatsAppAccessibilityService
```

Si no aparece nada o aparece vacío, el servicio NO está habilitado.

### Habilitar desde Terminal (Si es necesario):

```bash
adb -s 192.168.191.67:5555 shell settings put secure enabled_accessibility_services com.spatium.deamon.db.temi/.ui.WhatsAppAccessibilityService
```

---

## Flujo de Prueba

1. ✅ Habilitar servicio de accesibilidad
2. ✅ Abrir app "Deamon DB TEMI"
3. ✅ Presionar botón "Me encanta" (corazón)
4. ✅ Seleccionar foto de la galería
5. ✅ Presionar "Compartir a WhatsApp"
6. ✅ **Observar**: WhatsApp debe abrir y enviar automáticamente

---

## Troubleshooting

### Problema: "El servicio no está habilitado"
**Solución**: Seguir los pasos 1-4 arriba

### Problema: "WhatsApp abre pero no envía"
**Solución**: 
- Verificar que el servicio está habilitado
- Revisar logs: `adb logcat WhatsAppA11y:D`
- Asegurarse de que WhatsApp está actualizado

### Problema: "No aparece el botón de envío"
**Solución**:
- Esperar 2-3 segundos a que WhatsApp cargue completamente
- Verificar que la foto está adjunta en WhatsApp
- Revisar logs para ver qué está buscando el servicio

---

**Estado**: App compilada e instalada ✅
**Próximo paso**: Habilitar servicio de accesibilidad
