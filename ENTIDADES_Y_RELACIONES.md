# Entidades y Relaciones - TemiBridge

> Documento de referencia para el modelo de datos y relaciones del sistema TemiBridge.
> Este documento se actualiza conforme se construye el aplicativo para facilitar futuras migraciones a base de datos.

---

## 📊 Diagrama de Entidades

```
┌─────────────────┐
│   Robot Temi    │
│─────────────────│
│ - serialNumber  │
│ - currentX      │
│ - currentY      │
│ - status        │
└────────┬────────┘
         │
         │ 1:N
         │
    ┌────┴────┬────────┬──────────┐
    │         │        │          │
┌───▼───┐ ┌──▼──┐ ┌───▼────┐ ┌──▼────┐
│Waypoint│ │Tour │ │Sequence│ │QRCode │
└────────┘ └─────┘ └────────┘ └───────┘
```

---

## 🗂️ Entidades Principales

### 1. Robot Temi

**Descripción**: Representa el robot físico Temi y su estado actual.

**Atributos**:
- `serialNumber` (String, PK): Número de serie único del robot
- `currentX` (Double, nullable): Posición X actual en el mapa
- `currentY` (Double, nullable): Posición Y actual en el mapa
- `status` (Enum): Estado actual del robot
  - `IDLE` - En reposo
  - `NAVIGATING` - Navegando a un waypoint
  - `SPEAKING` - Reproduciendo TTS
  - `PLAYING_TOUR` - Ejecutando un tour
  - `PLAYING_SEQUENCE` - Ejecutando una secuencia
- `lastUpdate` (Timestamp): Última actualización de estado
- `firmwareVersion` (String): Versión del firmware Temi

**Operaciones**:
- `goTo(waypointName: String)`
- `speak(text: String)`
- `playTour(tourId: String)`
- `playSequence(sequenceId: String)`
- `getCurrentPosition(): Position`

**Relaciones**:
- 1 Robot → N Waypoints
- 1 Robot → N Tours
- 1 Robot → N Sequences
- 1 Robot → N QRCodes (configurados para ese robot)

---

### 2. Waypoint

**Descripción**: Punto de navegación guardado en el mapa del robot.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `name` (String, unique): Nombre del waypoint (ej: "Open_Space", "entrada")
- `x` (Double): Coordenada X en el mapa
- `y` (Double): Coordenada Y en el mapa
- `robotSerialNumber` (String, FK): Robot al que pertenece
- `description` (String, nullable): Descripción opcional
- `createdAt` (Timestamp): Fecha de creación
- `isActive` (Boolean): Si está activo para navegación

**Índices**:
- `idx_waypoint_name` en `name`
- `idx_waypoint_robot` en `robotSerialNumber`

**Relaciones**:
- N Waypoints → 1 Robot
- N Waypoints → N NavigationEvents (histórico)

**Validaciones**:
- `name` no puede contener espacios (usar guiones bajos)
- `x` y `y` deben estar dentro de los límites del mapa

---

### 3. Tour

**Descripción**: Secuencia predefinida de acciones y navegación configurada en Temi Center.

**Atributos**:
- `id` (String, PK): ID del tour en Temi Center
- `name` (String): Nombre del tour (ej: "Spatium_Visita")
- `robotSerialNumber` (String, FK): Robot al que pertenece
- `description` (String, nullable): Descripción del tour
- `duration` (Integer, nullable): Duración estimada en segundos
- `isActive` (Boolean): Si está disponible para ejecución
- `createdAt` (Timestamp): Fecha de creación

**Índices**:
- `idx_tour_name` en `name`
- `idx_tour_robot` en `robotSerialNumber`

**Relaciones**:
- N Tours → 1 Robot
- N Tours → N TourExecutions (histórico)

**Notas**:
- Los tours se gestionan principalmente en Temi Center
- Esta entidad sirve como referencia y para tracking

---

### 4. Sequence

**Descripción**: Secuencia de movimientos y acciones del robot (requiere permiso SEQUENCE).

**Atributos**:
- `id` (String, PK): ID de la secuencia en el SDK
- `name` (String): Nombre de la secuencia
- `robotSerialNumber` (String, FK): Robot al que pertenece
- `description` (String, nullable): Descripción
- `duration` (Integer, nullable): Duración en segundos
- `requiresPermission` (Boolean): Siempre true (requiere SEQUENCE permission)
- `isActive` (Boolean): Si está disponible
- `createdAt` (Timestamp): Fecha de creación

**Índices**:
- `idx_sequence_name` en `name`
- `idx_sequence_robot` en `robotSerialNumber`

**Relaciones**:
- N Sequences → 1 Robot
- N Sequences → N SequenceExecutions (histórico)

**Permisos**:
- Requiere `com.robotemi.sdk.permission.SEQUENCE` en AndroidManifest

---

### 5. QRCode

**Descripción**: Código QR que contiene un deep link para controlar el robot.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `code` (String, unique): Contenido del QR (deep link completo)
- `action` (Enum): Tipo de acción
  - `GO` - Navegación simple
  - `SAY` - Solo hablar
  - `WELCOME` - Saludo + navegación
  - `TOUR` - Iniciar tour
  - `SEQUENCE` - Ejecutar secuencia
- `targetPlace` (String, nullable): Waypoint destino (para GO, WELCOME)
- `textMessage` (String, nullable): Mensaje TTS (para SAY, WELCOME)
- `tourName` (String, nullable): Nombre del tour (para TOUR)
- `sequenceName` (String, nullable): Nombre de la secuencia (para SEQUENCE)
- `isReception` (Boolean): Si es un QR de recepción
- `phoneNumber` (String, nullable): Teléfono asociado (opcional)
- `robotSerialNumber` (String, FK): Robot al que está asociado
- `location` (String, nullable): Ubicación física del QR
- `createdAt` (Timestamp): Fecha de creación
- `lastScannedAt` (Timestamp, nullable): Última vez escaneado
- `scanCount` (Integer): Contador de escaneos
- `isActive` (Boolean): Si está activo

**Formato del Deep Link**:
```
mytemi://[action]?[parametros]

Ejemplos:
- mytemi://go?place=Open_Space&recepcion=false
- mytemi://welcome?text=Hola%20Mario&place=Salon_Duarte&recepcion=true&telefono=8091234567
- mytemi://tour?name=Spatium_Visita
- mytemi://sequence?name=Open_Space
```

**Índices**:
- `idx_qr_code` en `code`
- `idx_qr_robot` en `robotSerialNumber`
- `idx_qr_active` en `isActive`

**Relaciones**:
- N QRCodes → 1 Robot
- N QRCodes → N ScanEvents (histórico)
- 1 QRCode → 0..1 Waypoint (via targetPlace)
- 1 QRCode → 0..1 Tour (via tourName)
- 1 QRCode → 0..1 Sequence (via sequenceName)

**Validaciones**:
- `code` debe empezar con "mytemi://"
- Si `action = GO` o `WELCOME`, `targetPlace` es requerido
- Si `action = SAY` o `WELCOME`, `textMessage` es requerido
- Si `action = TOUR`, `tourName` es requerido
- Si `action = SEQUENCE`, `sequenceName` es requerido

---

### 6. WebhookEvent

**Descripción**: Evento enviado al webhook de Make.com cuando se escanea un QR.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `qrCodeId` (UUID, FK): QR que disparó el evento
- `isReception` (Boolean): Valor del parámetro recepcion
- `phoneNumber` (String, nullable): Teléfono del usuario
- `webhookUrl` (String): URL del webhook (actualmente Make.com)
- `status` (Enum): Estado del envío
  - `PENDING` - Pendiente de envío
  - `SUCCESS` - Enviado exitosamente
  - `FAILED` - Falló el envío
- `httpStatusCode` (Integer, nullable): Código HTTP de respuesta
- `errorMessage` (String, nullable): Mensaje de error si falló
- `createdAt` (Timestamp): Fecha de creación
- `sentAt` (Timestamp, nullable): Fecha de envío

**Payload JSON**:
```json
{
  "recepcion": true/false,
  "telefono": "8091234567" // opcional
}
```

**Índices**:
- `idx_webhook_qr` en `qrCodeId`
- `idx_webhook_status` en `status`
- `idx_webhook_created` en `createdAt`

**Relaciones**:
- N WebhookEvents → 1 QRCode
- 1 WebhookEvent → 0..1 KioskSession (si recepcion=true)

**Configuración Actual**:
- URL: `https://hook.us1.make.com/rpr19yvr51pufln58pwln4rdgz0dl6hq`

---

### 7. KioskSession

**Descripción**: Sesión de uso del modo kiosko (WebView de pedidos).

**Atributos**:
- `id` (UUID, PK): Identificador único
- `webhookEventId` (UUID, FK, nullable): Evento que disparó la sesión
- `url` (String): URL cargada en el WebView
- `startedAt` (Timestamp): Inicio de la sesión
- `endedAt` (Timestamp, nullable): Fin de la sesión
- `duration` (Integer, nullable): Duración en segundos
- `autoClosedAt` (Timestamp, nullable): Si se cerró automáticamente
- `manuallyClosedAt` (Timestamp, nullable): Si el usuario cerró manualmente
- `robotSerialNumber` (String, FK): Robot que ejecutó la sesión

**Índices**:
- `idx_kiosk_webhook` en `webhookEventId`
- `idx_kiosk_robot` en `robotSerialNumber`
- `idx_kiosk_started` en `startedAt`

**Relaciones**:
- N KioskSessions → 1 Robot
- 1 KioskSession → 0..1 WebhookEvent

**Configuración Actual**:
- URL: `https://spatium-desk.lovable.app/pedidos-publicos?ubicacion=Recepcion`
- Auto-cierre: 120 segundos (2 minutos)

---

## 🔗 Entidades de Histórico/Tracking

### 8. NavigationEvent

**Descripción**: Registro histórico de navegaciones del robot.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `robotSerialNumber` (String, FK): Robot que navegó
- `waypointId` (UUID, FK): Waypoint destino
- `qrCodeId` (UUID, FK, nullable): QR que disparó la navegación
- `startedAt` (Timestamp): Inicio de navegación
- `arrivedAt` (Timestamp, nullable): Llegada al destino
- `duration` (Integer, nullable): Duración en segundos
- `status` (Enum): Estado final
  - `COMPLETED` - Llegó exitosamente
  - `ABORTED` - Cancelado
  - `FAILED` - Falló (obstáculo, etc.)
- `errorMessage` (String, nullable): Mensaje de error si falló
- `returnToEntrance` (Boolean): Si debe retornar a "entrada"
- `returnedAt` (Timestamp, nullable): Cuándo retornó

**Índices**:
- `idx_nav_robot` en `robotSerialNumber`
- `idx_nav_waypoint` en `waypointId`
- `idx_nav_started` en `startedAt`

**Relaciones**:
- N NavigationEvents → 1 Robot
- N NavigationEvents → 1 Waypoint
- N NavigationEvents → 0..1 QRCode

---

### 9. ScanEvent

**Descripción**: Registro de cada escaneo de QR.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `qrCodeId` (UUID, FK): QR escaneado
- `robotSerialNumber` (String, FK): Robot que procesó el escaneo
- `scannedAt` (Timestamp): Fecha/hora del escaneo
- `executionSuccess` (Boolean): Si la acción se ejecutó exitosamente
- `errorMessage` (String, nullable): Mensaje de error si falló

**Índices**:
- `idx_scan_qr` en `qrCodeId`
- `idx_scan_robot` en `robotSerialNumber`
- `idx_scan_date` en `scannedAt`

**Relaciones**:
- N ScanEvents → 1 QRCode
- N ScanEvents → 1 Robot

---

### 10. TourExecution

**Descripción**: Registro de ejecuciones de tours.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `tourId` (String, FK): Tour ejecutado
- `robotSerialNumber` (String, FK): Robot que ejecutó
- `qrCodeId` (UUID, FK, nullable): QR que disparó el tour
- `startedAt` (Timestamp): Inicio del tour
- `completedAt` (Timestamp, nullable): Finalización del tour
- `duration` (Integer, nullable): Duración en segundos
- `status` (Enum): Estado final
  - `COMPLETED` - Completado
  - `ABORTED` - Cancelado
  - `FAILED` - Falló

**Índices**:
- `idx_tour_exec_tour` en `tourId`
- `idx_tour_exec_robot` en `robotSerialNumber`
- `idx_tour_exec_started` en `startedAt`

**Relaciones**:
- N TourExecutions → 1 Tour
- N TourExecutions → 1 Robot
- N TourExecutions → 0..1 QRCode

---

### 11. SequenceExecution

**Descripción**: Registro de ejecuciones de secuencias.

**Atributos**:
- `id` (UUID, PK): Identificador único
- `sequenceId` (String, FK): Secuencia ejecutada
- `robotSerialNumber` (String, FK): Robot que ejecutó
- `qrCodeId` (UUID, FK, nullable): QR que disparó la secuencia
- `startedAt` (Timestamp): Inicio de la secuencia
- `completedAt` (Timestamp, nullable): Finalización
- `duration` (Integer, nullable): Duración en segundos
- `status` (Enum): Estado final
  - `COMPLETED` - Completado
  - `PAUSED` - Pausado
  - `STOPPED` - Detenido
  - `FAILED` - Falló

**Índices**:
- `idx_seq_exec_sequence` en `sequenceId`
- `idx_seq_exec_robot` en `robotSerialNumber`
- `idx_seq_exec_started` en `startedAt`

**Relaciones**:
- N SequenceExecutions → 1 Sequence
- N SequenceExecutions → 1 Robot
- N SequenceExecutions → 0..1 QRCode

---

## 🔄 Flujos de Datos Principales

### Flujo 1: Escaneo QR → Navegación Simple

```
1. Usuario escanea QRCode (action=GO, targetPlace="Open_Space")
2. Se crea ScanEvent
3. Se crea NavigationEvent (status=STARTED)
4. Robot navega al waypoint
5. Se actualiza NavigationEvent (status=COMPLETED, arrivedAt)
6. Si returnToEntrance=true:
   - Espera 10s
   - Crea nuevo NavigationEvent (targetPlace="entrada")
   - Actualiza returnedAt
```

### Flujo 2: Escaneo QR → Modo Recepción

```
1. Usuario escanea QRCode (action=WELCOME, isReception=true)
2. Se crea ScanEvent
3. Se crea WebhookEvent (isReception=true)
4. Se envía webhook a Make.com
5. Se actualiza WebhookEvent (status=SUCCESS/FAILED)
6. Espera 5s
7. Se crea KioskSession
8. Se abre WebView con sistema de pedidos
9. Después de 120s o cierre manual:
   - Se actualiza KioskSession (endedAt, duration)
```

### Flujo 3: Escaneo QR → Tour

```
1. Usuario escanea QRCode (action=TOUR, tourName="Spatium_Visita")
2. Se crea ScanEvent
3. Se crea TourExecution (status=STARTED)
4. Robot ejecuta tour vía SDK
5. Se actualiza TourExecution (status=COMPLETED/ABORTED/FAILED)
```

---

## 📈 Métricas y Analytics

### Métricas Calculables

**Por Robot**:
- Total de navegaciones
- Navegaciones exitosas vs fallidas
- Tiempo promedio de navegación
- Waypoint más visitado
- Tours más ejecutados

**Por QR Code**:
- Total de escaneos
- Tasa de éxito de ejecución
- Horarios de mayor uso
- Ubicación física del QR

**Por Waypoint**:
- Frecuencia de visitas
- Tiempo promedio de llegada
- Tasa de éxito de navegación

**Generales**:
- Sesiones de kiosko por día
- Webhooks exitosos vs fallidos
- Tours completados vs abortados

---

## 🗄️ Consideraciones para Migración a BD

### Prioridad Alta
1. **QRCode**: Gestión centralizada de códigos QR
2. **Waypoint**: Sincronización con waypoints del robot
3. **ScanEvent**: Tracking de uso y analytics

### Prioridad Media
4. **NavigationEvent**: Histórico de navegaciones
5. **WebhookEvent**: Logs de integraciones
6. **KioskSession**: Métricas de uso del kiosko

### Prioridad Baja
7. **Tour/TourExecution**: Si se necesita gestión avanzada
8. **Sequence/SequenceExecution**: Si se usan frecuentemente

### Base de Datos Recomendada
- **PostgreSQL** o **Supabase**: Para app web de gestión
- **SQLite** local: Para caché en el dispositivo Android
- **Firebase Firestore**: Para sincronización en tiempo real

---

## 🔐 Seguridad y Validaciones

### Validaciones de Negocio
1. Un waypoint no puede tener el mismo nombre que otro del mismo robot
2. Un QR no puede tener múltiples acciones simultáneas
3. Los deep links deben seguir el formato `mytemi://[action]?[params]`
4. Los webhooks deben reintentar hasta 3 veces en caso de fallo

### Restricciones de Integridad
1. No se puede eliminar un Waypoint si tiene NavigationEvents asociados
2. No se puede desactivar un QRCode si tiene ScanEvents recientes (< 24h)
3. Un Robot debe tener al menos un waypoint "entrada" activo

---

## 📝 Notas de Implementación Actual

**Estado Actual**: No hay base de datos persistente. Todo se maneja en memoria durante la ejecución de la app.

**Datos Persistidos**:
- Waypoints: Gestionados por el SDK de Temi
- Tours: Gestionados por Temi Center
- Sequences: Gestionados por el SDK de Temi

**Datos No Persistidos**:
- QR Codes: Generados externamente, no se almacenan
- Eventos: No se registran (sin histórico)
- Webhooks: Fire-and-forget, sin retry

**Próximos Pasos**:
1. Implementar SQLite local para caché de QR codes
2. Añadir logging de eventos a archivo local
3. Implementar backend con Supabase para gestión centralizada
4. Crear dashboard web para administración

---

## 🔄 Historial de Cambios

| Fecha | Versión | Cambios |
|-------|---------|---------|
| 2025-01-20 | 1.0 | Creación inicial del documento |

---

**Última actualización**: 2025-01-20  
**Mantenido por**: Equipo de Desarrollo TemiBridge
