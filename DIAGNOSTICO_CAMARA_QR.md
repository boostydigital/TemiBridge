# 🔍 Diagnóstico: Cámara QR No Visible

**Fecha**: 2025-01-20  
**Problema**: La preview de la cámara en el lector QR no se muestra en pantalla

---

## 📊 Análisis del Problema

### Historial de Cambios

1. **Primera Implementación** (Funcionaba ✅):
   - Estructura: `FrameLayout` con `android:background="@drawable/qr_scan_frame"`
   - `BarcodeView` con `android:layout_margin="8dp"`
   - `View` para línea de escaneo

2. **Modificación que Rompió** (Step 557):
   - Se eliminó el `android:background` del FrameLayout
   - Se añadió un `ImageView` encima con `android:src="@drawable/qr_scan_frame"`
   - **Resultado**: El ImageView tapó el BarcodeView

3. **Reversión** (Step 574):
   - Se volvió a la estructura original
   - **Problema**: Aún no se ve la preview

### Evidencia de Logs

```
[CAMERA] BarcodeView width: 320, height: 320
[CAMERA] BarcodeView visibility: 0 (VISIBLE)
[CAMERA] BarcodeView isShown: true
[CAMERA] hasWindowFocus: true
[CAMERA] Cámara iniciada correctamente
```

**Conclusión**: La cámara se inicializa correctamente, pero la preview no se renderiza.

---

## 🎯 Causas Identificadas

### 1. Hardware Acceleration (Más Probable) ⭐⭐⭐⭐⭐
**Síntoma**: Preview negra o invisible en dispositivos específicos  
**Causa**: Conflicto entre aceleración de hardware y renderizado de cámara  
**Solución**: Deshabilitar `hardwareAccelerated` en MainActivity

### 2. PreviewScalingStrategy No Configurado ⭐⭐⭐⭐
**Síntoma**: Preview renderizada fuera del área visible  
**Causa**: Tamaño de preview no coincide con el contenedor  
**Solución**: Configurar `PreviewScalingStrategy.CENTER_CROP`

### 3. Z-Order Issues ⭐⭐⭐
**Síntoma**: Preview renderizada pero tapada por otros elementos  
**Causa**: Orden de capas incorrecto  
**Solución**: `bringToFront()` + `invalidate()` + `requestLayout()`

### 4. SurfaceView vs TextureView ⭐⭐⭐
**Síntoma**: SurfaceView renderiza fuera del contenedor  
**Causa**: Limitación de SurfaceView en Android  
**Solución**: `setUseTextureView(true)`

---

## 🛠️ Soluciones Implementadas

### ✅ Solución 1: Deshabilitar Hardware Acceleration

**Archivo**: `AndroidManifest.xml`

```xml
<activity
    android:name=".ui.MainActivity"
    android:hardwareAccelerated="false"
    ...>
```

**Justificación**: Los robots Temi pueden tener problemas con hardware acceleration. Esta es la solución más común para preview negra/invisible según issues de ZXing.

### ✅ Solución 2: Configurar PreviewScalingStrategy

**Archivo**: `MainActivity.kt`

```kotlin
barcodeView.setPreviewScalingStrategy(
    com.journeyapps.barcodescanner.camera.PreviewScalingStrategy.CENTER_CROP
)
```

**Justificación**: Asegura que la preview se escale correctamente dentro del contenedor de 320x320dp.

### ✅ Solución 3: TextureView + Z-Order

**Archivo**: `MainActivity.kt`

```kotlin
barcodeView.setUseTextureView(true)
barcodeView.bringToFront()
barcodeView.invalidate()
qrScannerContainer.requestLayout()
```

**Justificación**: TextureView es más confiable que SurfaceView para renderizado dentro de layouts complejos.

### ✅ Solución 4: Continuous Focus

**Archivo**: `MainActivity.kt`

```kotlin
cameraSettings.isContinuousFocusEnabled = true
```

**Justificación**: Mejora la detección de QR codes en movimiento.

---

## 🧪 Cómo Probar

### 1. Compilar e Instalar

```powershell
.\gradlew assembleDebug
adb -s 192.168.52.25:5555 install -r app\build\outputs\apk\debug\app-debug.apk
```

### 2. Verificar Logs

```powershell
adb -s 192.168.52.25:5555 logcat -s TemiBridge:D
```

Buscar:
```
[CAMERA] ========== CONFIGURACIÓN APLICADA ==========
[CAMERA] - TextureView: ACTIVADO
[CAMERA] - PreviewScaling: CENTER_CROP
[CAMERA] - Z-order: FORZADO AL FRENTE
```

### 3. Prueba Visual

- Abrir la app en el Temi
- Verificar si se ve la preview de la cámara en el cuadro dorado (esquina superior izquierda)
- Acercar un QR code para verificar detección

### 4. Prueba Funcional (Si no se ve la preview)

Generar QR con: `mytemi://say?text=Funciona`

- Acercar el QR al cuadro dorado
- Si el robot habla "Funciona", la cámara SÍ funciona aunque no se vea la preview
- Esto indicaría un problema de renderizado, no de detección

---

## 📚 Referencias

- [ZXing Embedding Documentation](https://github.com/journeyapps/zxing-android-embedded/blob/master/EMBEDDING.md)
- [Issue #299 - Black Screen](https://github.com/journeyapps/zxing-android-embedded/issues/299)
- [Issue #64 - Custom UI No Preview](https://github.com/journeyapps/zxing-android-embedded/issues/64)
- [Issue #132 - Failed to Configure Camera](https://github.com/journeyapps/zxing-android-embedded/issues/132)

---

## 🔄 Próximos Pasos si No Funciona

### Plan B: Usar DecoratedBarcodeView

Si las soluciones actuales no funcionan, cambiar a `DecoratedBarcodeView` con layout personalizado:

```xml
<com.journeyapps.barcodescanner.DecoratedBarcodeView
    android:id="@+id/barcodeScanner"
    app:zxing_scanner_layout="@layout/custom_barcode_scanner" />
```

### Plan C: Verificar Permisos de Cámara en Runtime

Añadir verificación explícita de disponibilidad de cámara:

```kotlin
val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
val cameraIds = cameraManager.cameraIdList
Log.d("TemiBridge", "Cámaras disponibles: ${cameraIds.joinToString()}")
```

### Plan D: Probar con Cámara Frontal

Cambiar `requestedCameraId = 0` a `requestedCameraId = 1` para probar con cámara frontal.

---

## 📝 Notas Importantes

1. **Hardware Acceleration**: Esta es la causa más probable en dispositivos Temi
2. **Preview vs Detección**: La cámara puede funcionar (detectar QR) aunque no se vea la preview
3. **Logs Detallados**: Todos los cambios incluyen logging exhaustivo para diagnóstico
4. **Estructura Original**: El layout XML está correcto, el problema es de configuración/renderizado

---

**Estado**: ✅ Soluciones implementadas - Pendiente de prueba en dispositivo
