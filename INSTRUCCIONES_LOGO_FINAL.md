# Instrucciones para Añadir el Logo de Spatium 10 Aniversario

## 📋 Pasos para Integrar el Logo

### Opción 1: Imagen Local (Recomendado para Producción)

1. **Guarda la imagen del logo** que te envié como:
   - Nombre: `spatium_logo_10.png`
   - Ubicación: `app/src/main/res/drawable/`
   - Formato: PNG con transparencia
   - Dimensiones recomendadas: 800x240px

2. **Actualiza MainActivity.kt** (línea ~100):
   
   Cambia:
   ```kotlin
   logoSpatium.load("https://cdn.prod.website-files.com/...") {
       crossfade(true)
       placeholder(android.R.color.transparent)
       error(android.R.color.transparent)
   }
   ```
   
   Por:
   ```kotlin
   logoSpatium.setImageResource(R.drawable.spatium_logo_10)
   ```

3. **Recompila**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

4. **Reinstala en Temi**:
   ```powershell
   adb -s 192.168.52.25:5555 install -r app\build\outputs\apk\debug\app-debug.apk
   ```

### Opción 2: URL Remota (Temporal)

Si prefieres usar una URL mientras preparas la imagen local:

1. **Sube la imagen** a un servidor accesible

2. **Actualiza la URL** en MainActivity.kt línea ~100:
   ```kotlin
   logoSpatium.load("https://tu-servidor.com/spatium_logo_10.png") {
       crossfade(true)
       placeholder(android.R.color.transparent)
       error(android.R.color.transparent)
   }
   ```

## 🎨 Diseño Actual

```
┌─────────────────────────────────────────────────┐
│  [QR]          [Logo Spatium 10]                │  ← Header
│  180x180       Aniversario                      │
│                                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│         Escanee el QR para                      │
│         ver la información                      │
│         del evento                              │
│                                                 │
└─────────────────────────────────────────────────┘
```

## 📐 Especificaciones del Logo

- **Posición**: Centro-derecha del header
- **Altura**: 80dp
- **Ancho**: Proporcional (fitCenter)
- **Margen izquierdo**: 320dp (deja espacio para QR)
- **Margen derecho**: 80dp
- **Margen superior**: 8dp

## ✅ Verificación

Después de añadir el logo, verifica:
- [ ] Logo aparece centrado en la parte superior
- [ ] No interfiere con el lector QR (izquierda)
- [ ] Proporciones correctas (10 Aniversario visible)
- [ ] Colores dorados coinciden con el diseño
- [ ] Animación de entrada funciona (fade + slide)

---

**Nota**: La imagen actual carga desde una URL temporal. Para producción, usa la imagen local.
