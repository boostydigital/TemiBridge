# Instrucciones para Añadir el Logo de Spatium 10 Aniversario

## 📋 Pasos para Integrar el Logo

### Opción 1: Usar Imagen Local (Recomendado)

1. **Guardar la imagen del logo**:
   - Nombre del archivo: `spatium_logo_10.png`
   - Ubicación: `app/src/main/res/drawable/`
   - Formato recomendado: PNG con transparencia
   - Dimensiones sugeridas: 800x240px (mantener proporción 10:3)

2. **La imagen ya está configurada en el layout**:
   - El `ImageView` con id `logoSpatium` ya existe en `activity_main.xml`
   - Altura: 80dp
   - Posición: Centrado en el header
   - ScaleType: fitCenter (mantiene proporciones)

3. **Actualizar el código para usar la imagen local**:
   
   En `MainActivity.kt`, línea ~106, cambiar:
   ```kotlin
   logoSpatium.load("https://cdn.prod.website-files.com/...") {
   ```
   
   Por:
   ```kotlin
   logoSpatium.setImageResource(R.drawable.spatium_logo_10)
   ```

### Opción 2: Usar URL Remota

Si prefieres cargar el logo desde una URL:

1. **Subir la imagen a un servidor**:
   - Puede ser el CDN de Spatium
   - O cualquier servidor accesible

2. **Actualizar la URL en MainActivity.kt** (línea ~106):
   ```kotlin
   logoSpatium.load("https://tu-servidor.com/spatium_logo_10.png") {
       crossfade(true)
       placeholder(android.R.color.transparent)
       error(android.R.color.transparent)
   }
   ```

## 🎨 Diseño Actual

```
┌─────────────────────────────────────────────┐
│  [Logo Spatium 10]          [Botón QR] ⬜  │  ← Header
├─────────────────────────────────────────────┤
│                                             │
│              🔲 (Icono QR)                  │
│                                             │
│      Escanee el QR para                     │
│      ver la información                     │
│      del evento                             │
│                                             │
│         [Marco de Escaneo]                  │
│                                             │
└─────────────────────────────────────────────┘
```

## ✨ Animación del Logo

El logo tiene una animación de entrada elegante:
- **Fade in**: De transparente a opaco
- **Slide down**: Desde -20dp hacia posición final
- **Duración**: 900ms
- **Timing**: Aparece primero (antes del icono y texto central)

## 📐 Especificaciones Técnicas

### Layout (activity_main.xml)
```xml
<ImageView
    android:id="@+id/logoSpatium"
    android:layout_width="0dp"
    android:layout_height="80dp"
    android:layout_marginStart="80dp"
    android:layout_marginEnd="80dp"
    android:scaleType="fitCenter"
    android:contentDescription="Spatium 10 Aniversario"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent" />
```

### Animación (MainActivity.kt)
```kotlin
logoSpatium.alpha = 0f
logoSpatium.translationY = -20f
logoSpatium.animate()
    .alpha(1f)
    .translationY(0f)
    .setDuration(900)
    .setInterpolator(DecelerateInterpolator())
    .start()
```

## 🔄 Secuencia de Animaciones

1. **Logo** (0ms): Slide down + fade in (900ms)
2. **Icono QR** (200ms delay): Scale + fade in (800ms)
3. **Texto** (400ms delay): Slide up + fade in (700ms)
4. **Botón QR**: Inicia pulsación continua

## 📝 Notas Importantes

1. **Formato de Imagen**:
   - PNG con transparencia preferido
   - Fondo transparente para que se vea el navy oscuro
   - Colores dorados del logo coinciden con la paleta (#D4AF37)

2. **Tamaño del Archivo**:
   - Optimizar para web (< 100KB recomendado)
   - Usar compresión PNG sin pérdida

3. **Responsive**:
   - El logo se adapta automáticamente al ancho disponible
   - Mantiene proporciones con `scaleType="fitCenter"`
   - Márgenes laterales de 80dp para no tocar los bordes

4. **Accesibilidad**:
   - ContentDescription configurado: "Spatium 10 Aniversario"
   - Contraste adecuado con el fondo navy

## 🚀 Para Aplicar los Cambios

### Si usas imagen local:

1. Coloca `spatium_logo_10.png` en `app/src/main/res/drawable/`

2. Modifica `MainActivity.kt` línea ~106:
   ```kotlin
   // Eliminar o comentar estas líneas:
   // logoSpatium.load("https://...") { ... }
   
   // Añadir esta línea:
   logoSpatium.setImageResource(R.drawable.spatium_logo_10)
   ```

3. Recompilar la app:
   ```powershell
   .\gradlew assembleDebug
   ```

### Si usas URL remota:

1. Sube la imagen a tu servidor

2. Actualiza la URL en `MainActivity.kt` línea ~106

3. Recompilar la app

## ✅ Verificación

Después de aplicar los cambios, verifica:
- [ ] Logo aparece centrado en el header
- [ ] Animación de entrada es suave
- [ ] Logo no interfiere con el botón QR
- [ ] Proporciones se mantienen correctas
- [ ] Colores dorados coinciden con el diseño

---

**Última actualización**: 2025-01-20  
**Archivo de referencia**: Logo proporcionado por el usuario (Spatium 10 Aniversario)
