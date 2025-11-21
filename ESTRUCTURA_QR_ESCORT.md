# Estructura QR - Deep Link `mytemi://escort`

## 📋 Descripción

El deep link `mytemi://escort` implementa un flujo completo de acompañamiento con 4 fases:

1. **Bienvenida** - Saludo personalizado al invitado
2. **Navegación** - Robot lleva al invitado al destino
3. **Despedida** - Mensaje al llegar al destino
4. **Retorno** - Robot regresa al punto de origen

---

## 🔧 Parámetros

| Parámetro | Tipo | Requerido | Default | Descripción |
|-----------|------|-----------|---------|-------------|
| `greeting` | String | No | - | Mensaje de bienvenida personalizado |
| `place` | String | **SÍ** | - | Waypoint de destino (debe existir en Temi) |
| `farewell` | String | No | "Hemos llegado a tu destino. Disfruta del evento." | Mensaje de despedida al llegar |
| `waitTime` | Number | No | 10 | Segundos de espera antes de retornar |
| `returnTo` | String | No | "entrada" | Waypoint de retorno |
| `returnMessage` | String | No | - | Mensaje opcional durante el retorno |
| `recepcion` | Boolean | No | false | Activar modo recepción (webhook) |
| `telefono` | String | No | - | Teléfono para webhook |

---

## 📝 Estructura Básica

```
mytemi://escort?place=<DESTINO>
```

### Ejemplo Mínimo
```
mytemi://escort?place=Salon_Duarte
```

**Flujo**:
1. ❌ Sin saludo
2. ✅ Navega a "Salon_Duarte"
3. ✅ Dice: "Hemos llegado a tu destino. Disfruta del evento."
4. ⏱️ Espera 10 segundos
5. ✅ Retorna a "entrada"

---

## 🎯 Estructura Completa

```
mytemi://escort?
  greeting=<SALUDO>
  &place=<DESTINO>
  &farewell=<DESPEDIDA>
  &waitTime=<SEGUNDOS>
  &returnTo=<PUNTO_RETORNO>
  &returnMessage=<MENSAJE_RETORNO>
```

### Ejemplo Completo
```
mytemi://escort?greeting=Bienvenido%20Juan%20P%C3%A9rez,%20te%20llevar%C3%A9%20al%20Sal%C3%B3n%20Duarte&place=Salon_Duarte&farewell=Disfruta%20del%20evento%20Juan,%20tu%20anfitri%C3%B3n%20te%20atender%C3%A1&waitTime=15&returnTo=entrada&returnMessage=Regresando%20a%20la%20entrada
```

**Flujo**:
1. ✅ Dice: "Bienvenido Juan Pérez, te llevaré al Salón Duarte"
2. ✅ Navega a "Salon_Duarte"
3. ✅ Al llegar dice: "Disfruta del evento Juan, tu anfitrión te atenderá"
4. ⏱️ Espera 15 segundos
5. ✅ Dice: "Regresando a la entrada"
6. ✅ Retorna a "entrada"

---

## 🧪 Ejemplos de Prueba

### Prueba 1: Básico (Solo Destino)
```
mytemi://escort?place=Salon_Duarte
```

### Prueba 2: Con Saludo y Despedida
```
mytemi://escort?greeting=Hola%20Mar%C3%ADa,%20s%C3%ADgueme&place=Salon_Duarte&farewell=Aqu%C3%AD%20est%C3%A1%20tu%20mesa,%20disfruta
```

### Prueba 3: Tiempo de Espera Personalizado
```
mytemi://escort?greeting=Bienvenido&place=Salon_Duarte&waitTime=20
```

### Prueba 4: Retorno Personalizado
```
mytemi://escort?place=Salon_Duarte&returnTo=recepcion&returnMessage=Volviendo%20a%20la%20recepci%C3%B3n
```

### Prueba 5: Evento Completo (Recomendado)
```
mytemi://escort?greeting=Bienvenido%20al%20evento%20de%20Spatium%2010%20a%C3%B1os.%20Te%20llevar%C3%A9%20a%20tu%20mesa&place=Mesa_VIP&farewell=Disfruta%20del%20evento.%20Tu%20anfitri%C3%B3n%20te%20atender%C3%A1%20en%20breve&waitTime=12&returnTo=entrada&returnMessage=Regresando%20al%20punto%20de%20inicio
```

---

## 🎨 Generador de QR

### Herramientas Recomendadas

1. **QR Code Generator** (https://www.qr-code-generator.com/)
   - Seleccionar "URL"
   - Pegar el deep link completo
   - Descargar PNG

2. **QRCode Monkey** (https://www.qrcode-monkey.com/)
   - Permite personalizar colores (usar dorado #D4AF37)
   - Añadir logo de Spatium
   - Alta resolución

3. **Comando en línea** (Node.js):
```bash
npm install -g qrcode
qrcode "mytemi://escort?place=Salon_Duarte" -o qr_salon_duarte.png
```

---

## 📐 Plantilla para Eventos

### Para Invitado VIP
```
mytemi://escort?
  greeting=Bienvenido%20[NOMBRE],%20es%20un%20placer%20tenerlo%20en%20Spatium
  &place=[WAYPOINT]
  &farewell=Disfruta%20del%20evento%20[NOMBRE],%20tu%20anfitri%C3%B3n%20te%20espera
  &waitTime=15
  &returnTo=entrada
```

### Para Invitado Regular
```
mytemi://escort?
  greeting=Hola%20[NOMBRE],%20s%C3%ADgueme%20por%20favor
  &place=[WAYPOINT]
  &farewell=Hemos%20llegado,%20disfruta%20del%20evento
  &waitTime=10
```

### Para Recepción con Webhook
```
mytemi://escort?
  greeting=Bienvenido
  &place=[WAYPOINT]
  &recepcion=true
  &telefono=[NUMERO]
```

---

## 🔍 Codificación de Caracteres

### Caracteres Especiales

| Carácter | Codificado | Ejemplo |
|----------|------------|---------|
| Espacio | `%20` | "Juan Pérez" → `Juan%20P%C3%A9rez` |
| á | `%C3%A1` | "Salón" → `Sal%C3%B3n` |
| é | `%C3%A9` | "José" → `Jos%C3%A9` |
| í | `%C3%AD` | "María" → `Mar%C3%ADa` |
| ó | `%C3%B3` | "Adiós" → `Adi%C3%B3s` |
| ú | `%C3%BA` | "Menú" → `Men%C3%BA` |
| ñ | `%C3%B1` | "Año" → `A%C3%B1o` |
| ¿ | `%C2%BF` | "¿Cómo?" → `%C2%BF` |
| ¡ | `%C2%A1` | "¡Hola!" → `%C2%A1` |
| , | `%2C` | "Hola, Juan" → `Hola%2C%20Juan` |

### Herramienta Online
https://www.urlencoder.org/

---

## 📊 Waypoints Comunes

Asegúrate de que estos waypoints existan en tu robot Temi:

- `entrada` - Punto de entrada/recepción
- `Salon_Duarte` - Salón principal
- `Mesa_VIP` - Mesa VIP
- `recepcion` - Área de recepción
- `Open_Space` - Área abierta
- `Cocina` - Cocina/bar

**Verificar waypoints**: Usa la app de Temi para confirmar los nombres exactos.

---

## ⚠️ Consideraciones Importantes

### Tiempos Recomendados

- **waitTime mínimo**: 5 segundos (dar tiempo al invitado)
- **waitTime máximo**: 30 segundos (no dejar robot parado mucho tiempo)
- **waitTime recomendado**: 10-15 segundos

### Mensajes

- **Máximo 200 caracteres** por mensaje (límite TTS)
- Usar lenguaje natural y amigable
- Evitar caracteres especiales complejos
- Probar pronunciación antes del evento

### Navegación

- Verificar que todos los waypoints existan
- Probar rutas antes del evento
- Asegurar que el robot tenga batería suficiente
- Limpiar sensores antes de usar

---

## 🐛 Debugging

### Ver Logs en Tiempo Real

```bash
adb -s 192.168.52.25:5555 logcat -s TemiBridge:D
```

### Logs Importantes

```
[ESCORT] Iniciando flujo: greeting='...', place='...', farewell='...', waitTime=10s, returnTo='entrada'
[ESCORT] Bienvenida: ...
[ESCORT] Navegación iniciada a: ...
[ESCORT] Llegada al destino: ...
[ESCORT] Despedida: ...
[ESCORT] Iniciando retorno a: ...
[ESCORT] Retorno ejecutado a: ...
```

---

## 📱 Integración con Sistema de Pedidos

Si quieres combinar con el sistema de pedidos:

```
mytemi://escort?
  greeting=Bienvenido
  &place=Recepcion
  &recepcion=true
  &telefono=8091234567
```

Esto activará el webhook y abrirá el sistema de pedidos después de 5 segundos.

---

## ✅ Checklist Pre-Evento

- [ ] Todos los waypoints creados y probados
- [ ] QR codes generados y impresos
- [ ] Mensajes probados (pronunciación correcta)
- [ ] Tiempos de espera ajustados
- [ ] Batería del robot al 100%
- [ ] Sensores limpios
- [ ] App TemiBridge actualizada
- [ ] Prueba completa del flujo
- [ ] Backup de QR codes en digital

---

**Última actualización**: 2025-01-20  
**Versión**: 1.0 - Deep Link Escort  
**Desarrollado por**: Equipo TemiBridge
