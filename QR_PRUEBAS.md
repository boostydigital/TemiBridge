# QR Codes de Prueba - TemiBridge

## 🧪 Pruebas Básicas

### Prueba 1: Saludo Simple
```
mytemi://say?text=Hola, estoy funcionando correctamente
```
**Genera este QR**: https://www.qr-code-generator.com/
- Pega el texto exacto
- Genera y descarga
- Acerca el QR al lector (cuadro dorado en la pantalla)

**Resultado esperado**: El robot debe decir "Hola, estoy funcionando correctamente"

---

### Prueba 2: Navegación Básica
```
mytemi://go?place=entrada
```
**Resultado esperado**: El robot navega a "entrada"

---

### Prueba 3: Flujo Escort Completo
```
mytemi://escort?greeting=Hola%20bienvenido&place=entrada&farewell=Hemos%20llegado&waitTime=5
```
**Resultado esperado**:
1. Dice: "Hola bienvenido"
2. Navega a "entrada"
3. Al llegar dice: "Hemos llegado"
4. Espera 5 segundos
5. Retorna a "entrada"

---

## 🔍 Verificar que la Cámara Funciona

### Síntomas si la cámara NO funciona:
- ❌ Cuadro negro en el lector QR
- ❌ No responde al acercar QR
- ❌ No hay preview de cámara

### Síntomas si la cámara SÍ funciona:
- ✅ Se ve la imagen de la cámara en vivo
- ✅ Línea dorada animada moviéndose
- ✅ Al acercar QR, se ejecuta la acción inmediatamente

---

## 📱 Ver Logs en Tiempo Real

Abre PowerShell y ejecuta:
```powershell
adb -s 192.168.52.25:5555 logcat -s TemiBridge:D | Select-String "CAMERA|QR escaneado"
```

**Logs que deberías ver**:
```
[CAMERA] Iniciando BarcodeView...
[CAMERA] BarcodeView.resume() ejecutado correctamente
[CAMERA] Escaneo continuo activo - Acerca un QR code
QR escaneado: mytemi://...
```

---

## 🐛 Solución de Problemas

### Problema: Cámara no se ve

**Solución 1**: Verificar permisos
```powershell
adb -s 192.168.52.25:5555 shell pm grant com.spatium.temibridge android.permission.CAMERA
```

**Solución 2**: Reiniciar la app
```powershell
adb -s 192.168.52.25:5555 shell am force-stop com.spatium.temibridge
adb -s 192.168.52.25:5555 shell am start -n com.spatium.temibridge/.ui.MainActivity
```

**Solución 3**: Ver errores
```powershell
adb -s 192.168.52.25:5555 logcat -s TemiBridge:E -d
```

---

### Problema: Logo no aparece

**Causa**: La imagen del logo no está en los recursos

**Solución temporal**: El logo cargará desde una URL. Para producción:
1. Guarda `spatium_logo_10.png` en `app/src/main/res/drawable/`
2. Edita `MainActivity.kt` línea ~106
3. Cambia a: `logoSpatium.setImageResource(R.drawable.spatium_logo_10)`
4. Recompila

---

## ✅ Checklist de Verificación

- [ ] Abrir la app en Temi
- [ ] Ver el cuadro dorado del lector QR (izquierda-centro)
- [ ] Ver imagen de cámara en vivo dentro del cuadro
- [ ] Ver línea dorada animada moviéndose
- [ ] Ver logo de Spatium (arriba-centro)
- [ ] Ver texto: "Escanee el QR para ver la información del evento"
- [ ] Generar QR de prueba 1
- [ ] Acercar QR al lector
- [ ] Escuchar al robot decir el mensaje
- [ ] Verificar que aparece "✓ QR Escaneado Exitosamente"

---

## 📊 Estado Actual

**Lector QR**:
- Tamaño: 240x240dp
- Posición: 100dp desde el borde izquierdo
- Cámara: BarcodeView con margin 4dp
- Estado: ✅ Inicializado correctamente según logs

**Logo**:
- Tamaño: 80dp altura
- Posición: Centro-derecha (320dp desde izquierda)
- Fuente: URL temporal (cambiar a recurso local)

**Animaciones**:
- Logo: Fade + slide down (1000ms)
- Escáner: Fade + scale (800ms)
- Texto: Fade + slide up (900ms)
- Línea: Loop continuo (2000ms)

---

**Última actualización**: 2025-01-20 18:40
**Versión**: Con lector QR continuo activo
