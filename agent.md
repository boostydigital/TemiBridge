# Agent Configuration - TemiBridge

## Supabase Integration

**Proyecto Supabase asociado:** `supabase-temi`

- **Project Ref:** `mkakxmjkwcymwosfrwkl`
- **MCP Server:** `mcp9_*` (prefijo para todas las herramientas)

### Instrucciones para el Agente

**SIEMPRE** utilizar el MCP `supabase-temi` para cualquier comunicación con la base de datos de este proyecto. Las herramientas disponibles incluyen:

- `mcp9_execute_sql` - Ejecutar consultas SQL
- `mcp9_apply_migration` - Aplicar migraciones DDL
- `mcp9_list_tables` - Listar tablas del schema
- `mcp9_get_logs` - Obtener logs del proyecto
- `mcp9_deploy_edge_function` - Desplegar Edge Functions
- `mcp9_list_edge_functions` - Listar Edge Functions
- `mcp9_search_docs` - Buscar documentación de Supabase

### Ejemplo de Uso

```
# Para consultar datos
mcp9_execute_sql(query: "SELECT * FROM tabla")

# Para crear tablas o modificar schema
mcp9_apply_migration(name: "create_tabla", query: "CREATE TABLE...")

# Para ver estructura de la base de datos
mcp9_list_tables(schemas: ["public"], verbose: true)
```

---

## Modo Patrullaje con Anuncios (Announcement Patrol)

### Descripción

El robot Temi puede entrar en **modo patrullaje** donde recorre waypoints predefinidos mientras:
- Muestra una **imagen fullscreen** en pantalla
- Habla un **texto de anuncio** en loop cada 15 segundos
- Mantiene el **volumen a nivel 6**
- Activa **Kiosk Mode** para ocultar la UI de navegación de Temi

### Arquitectura

```
┌─────────────────┐     POST      ┌──────────────────────┐
│  Sistema        │ ────────────► │  Edge Function       │
│  Externo        │               │  activar-anuncio     │
└─────────────────┘               └──────────┬───────────┘
                                             │
                                             ▼
                                  ┌──────────────────────┐
                                  │  Tabla: anuncios     │
                                  │  (Supabase)          │
                                  └──────────┬───────────┘
                                             │
                        Polling cada 30s     │
                                             ▼
┌─────────────────┐               ┌──────────────────────┐
│  Robot Temi     │ ◄──────────── │  Edge Function       │
│  (App Android)  │    GET        │  anuncio-activo      │
└─────────────────┘               └──────────────────────┘
```

### Componentes

#### 1. Edge Functions (Supabase)

| Función | Método | Descripción |
|---------|--------|-------------|
| `activar-anuncio` | POST | Crea un nuevo anuncio activo |
| `anuncio-activo` | GET | Retorna el anuncio activo actual |

#### 2. Tabla `anuncios`

```sql
CREATE TABLE anuncios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  texto TEXT NOT NULL,                    -- Texto a hablar (max 500 chars)
  imagen_url TEXT,                        -- URL de imagen fullscreen
  duracion_minutos INTEGER NOT NULL,      -- Duración (1-120 min)
  waypoints JSONB DEFAULT '[]',           -- Array de waypoints (min 3)
  activo BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ                  -- Auto-calculado
);
```

#### 3. Componentes Android

| Archivo | Descripción |
|---------|-------------|
| `AnnouncementManager.kt` | Polling, control de patrullaje, TTS loop |
| `AnnouncementActivity.kt` | UI fullscreen con imagen y texto |
| `TemiController.kt` | Métodos: `patrol()`, `setVolume()`, `setKioskModeOn()` |

### API de Activación

**Endpoint:** `POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/activar-anuncio`

**Request Body:**
```json
{
  "texto": "Bienvenidos al evento de Inteligencia Artificial",
  "imagen_url": "https://example.com/imagen.png",
  "duracion_minutos": 5,
  "waypoints": ["l01", "l04", "l05", "l01"]
}
```

**Response:**
```json
{
  "success": true,
  "anuncio": {
    "id": "uuid",
    "texto": "...",
    "imagen_url": "...",
    "duracion_minutos": 5,
    "waypoints": ["l01", "l04", "l05", "l01"],
    "activo": true,
    "created_at": "2026-04-17T18:00:00Z",
    "expires_at": "2026-04-17T18:05:00Z"
  }
}
```

### Configuración del Robot

Para que el modo patrullaje funcione correctamente:

1. **Kiosk Mode**: Configurar "Deamon DB TEMI" como app de Kiosk en Settings del robot
2. **Permisos**: La app debe tener permisos de `settings` y `sequence`
3. **Waypoints**: Los waypoints deben existir previamente en el robot

### Flujo de Ejecución

1. Sistema externo llama a `activar-anuncio` con datos del anuncio
2. Edge Function inserta en tabla `anuncios` con `activo=true`
3. App Android hace polling cada 30s a `anuncio-activo`
4. Al detectar anuncio activo:
   - Guarda velocidad y volumen originales
   - Configura velocidad SLOW y volumen 6
   - Activa Kiosk Mode (oculta UI de Temi)
   - Abre `AnnouncementActivity` con imagen fullscreen
   - Inicia loop de TTS (habla cada 15 segundos)
   - Inicia `patrol()` con waypoints en loop infinito
5. Al expirar el anuncio:
   - Detiene TTS y patrullaje
   - Restaura velocidad, volumen y UI
   - Cierra Activity
   - Robot vuelve a "home base"

### Parámetros Configurables

| Parámetro | Valor | Ubicación |
|-----------|-------|-----------|
| Intervalo de polling | 30 segundos | `AnnouncementManager.kt` |
| Intervalo de TTS | 15 segundos | `AnnouncementManager.kt` |
| Volumen de anuncio | 6 (de 0-10) | `AnnouncementManager.kt` |
| Velocidad de patrullaje | SLOW | `AnnouncementManager.kt` |
| Tiempo en cada waypoint | 10 segundos | `AnnouncementManager.kt` |

### Integración con Sistemas Externos

Para integrar desde otro sistema, simplemente hacer POST a la Edge Function:

```javascript
// Ejemplo en JavaScript
const response = await fetch(
  'https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/activar-anuncio',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      texto: 'Mensaje del anuncio',
      imagen_url: 'https://...',
      duracion_minutos: 3,
      waypoints: ['l01', 'l04', 'l05']
    })
  }
);
```

### Troubleshooting

| Problema | Solución |
|----------|----------|
| Imagen no se muestra | Verificar URL pública y accesible |
| UI de Temi se superpone | Configurar app como Kiosk en Settings |
| Robot no patrulla | Verificar que waypoints existen en el robot |
| TTS no funciona | Verificar volumen y permisos |
| Anuncio no se detecta | Verificar conectividad y logs de polling |

---

## Modo Rating/Evaluación de Salones

### Descripción

El robot Temi puede recibir **evaluaciones programadas** de reuniones en salones. Cuando una reunión está por terminar, el robot:
- Navega al salón **3 minutos antes** del fin de la reunión
- Muestra la **pantalla de rating** con 5 estrellas interactivas
- Permanece **15 minutos** esperando la evaluación
- Habla un **TTS de invitación** cada 60 segundos
- Envía la evaluación a un **sistema externo**
- Agradece y vuelve a **home base**

### Arquitectura

```
┌─────────────────┐     POST      ┌──────────────────────────┐
│  Sistema        │ ────────────► │  Edge Function           │
│  Externo        │               │  programar-evaluacion    │
└─────────────────┘               └──────────┬───────────────┘
                                             │
                                             ▼
                                  ┌──────────────────────────┐
                                  │  Tabla:                  │
                                  │  evaluaciones_programadas│
                                  └──────────┬───────────────┘
                                             │
                        Polling cada 30s     │
                                             ▼
┌─────────────────┐               ┌──────────────────────────┐
│  Robot Temi     │ ◄──────────── │  Edge Function           │
│  (App Android)  │    GET        │  evaluacion-pendiente    │
└─────────────────┘               └──────────────────────────┘
         │
         │ POST (al recibir rating)
         ▼
┌─────────────────────────────────────────────────────────────┐
│  API Externa: create-evaluation                             │
│  https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/...  │
└─────────────────────────────────────────────────────────────┘
```

### Componentes

#### 1. Edge Functions (Supabase - supabase-temi)

| Función | Método | Descripción |
|---------|--------|-------------|
| `programar-evaluacion` | POST | Programa una evaluación para un salón |
| `evaluacion-pendiente` | GET | Retorna evaluación pendiente (hora_llegada <= now) |

#### 2. Tabla `evaluaciones_programadas`

```sql
CREATE TABLE evaluaciones_programadas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  salon TEXT NOT NULL,                    -- Nombre del salón
  waypoint TEXT NOT NULL,                 -- Waypoint mapeado
  hora_fin TIMESTAMPTZ NOT NULL,          -- Hora fin de reunión
  hora_llegada TIMESTAMPTZ NOT NULL,      -- hora_fin - 3 minutos
  nombre_reserva TEXT NOT NULL,           -- Nombre del cliente
  estado TEXT DEFAULT 'programada',       -- programada|en_proceso|completada|timeout
  rating INTEGER CHECK (rating BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ DEFAULT now()
);
```

#### 3. Mapeo de Salones a Waypoints

| Salón | Waypoint |
|-------|----------|
| Sala Duarte | salonduarte |
| Sala Enriquillo | salonenriquillo |
| Sala Multimedia | salonmultimedia |
| Sala Quisqueya | salonquisqueya |
| Sala Santo Domingo | salonsantodomingo |

#### 4. Componentes Android

| Archivo | Descripción |
|---------|-------------|
| `RatingManager.kt` | Polling, navegación, control de timeout, envío a API externa |
| `RatingActivity.kt` | UI de rating con 5 estrellas (existente, extendido) |
| `rating.html` | WebView con estrellas interactivas |

### API de Programación

**Endpoint:** `POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/programar-evaluacion`

#### Programar nueva evaluación:
**Request Body:**
```json
{
  "salon": "Sala Santo Domingo",
  "hora_fin": "2026-04-17T21:00:00Z",
  "nombre_reserva": "Juan Pérez"
}
```

**Response:**
```json
{
  "success": true,
  "action": "programada",
  "evaluacion": {
    "id": "uuid",
    "salon": "Sala Santo Domingo",
    "waypoint": "salonsantodomingo",
    "hora_fin": "2026-04-17T21:00:00Z",
    "hora_llegada": "2026-04-17T20:57:00Z",
    "nombre_reserva": "Juan Pérez",
    "estado": "programada",
    "rating": null,
    "created_at": "2026-04-17T20:50:00Z"
  }
}
```

#### Cancelar evaluación (reunión cancelada):
**Request Body:**
```json
{
  "salon": "Sala Santo Domingo",
  "hora_fin": "2026-04-17T21:00:00Z",
  "nombre_reserva": "Juan Pérez",
  "estado": "cancelado"
}
```

**Response:**
```json
{
  "success": true,
  "action": "cancelada",
  "evaluaciones_canceladas": 1,
  "evaluaciones": [...]
}
```

### API Externa de Evaluación

**Endpoint:** `POST https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation`

**Request Body (enviado por el robot):**
```json
{
  "rating": 5,
  "customer_name": "Juan Pérez",
  "salon": "Sala Santo Domingo",
  "feedback_text": "Excelente servicio",
  "category": "Sala Santo Domingo"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Feedback registrado exitosamente",
  "data": {
    "id": "uuid",
    "rating": 5,
    "salon": "Sala Santo Domingo",
    "customer_name": "Juan Pérez",
    "feedback_text": "Excelente servicio",
    "created_at": "2026-04-17T21:01:49Z"
  }
}
```

### Flujo de Ejecución

1. Sistema externo llama a `programar-evaluacion` con salon, hora_fin, nombre_reserva
2. Edge Function calcula `hora_llegada = hora_fin - 3 minutos` e inserta en BD
3. App Android hace polling cada 30s a `evaluacion-pendiente`
4. Al detectar evaluación pendiente (hora_llegada <= now):
   - Activa Kiosk Mode
   - Navega al waypoint del salón
   - Abre `RatingActivity` con pantalla de estrellas
   - Inicia TTS de invitación cada 60 segundos
   - Configura timeout de 15 minutos
5. Si usuario toca una estrella:
   - Envía POST a `create-evaluation` con rating y datos
   - Muestra página de agradecimiento
   - TTS: "¡Muchas gracias por tu evaluación!"
   - Vuelve a home base
6. Si pasan 15 minutos sin evaluación:
   - TTS: "Gracias por visitarnos. Hasta pronto."
   - Actualiza estado a "timeout"
   - Vuelve a home base

### Parámetros Configurables

| Parámetro | Valor | Ubicación |
|-----------|-------|-----------|
| Intervalo de polling | 30 segundos | `RatingManager.kt` |
| Tiempo antes de llegada | 3 minutos | Edge Function |
| Timeout de rating | 15 minutos | `RatingManager.kt` |
| Intervalo de TTS | 60 segundos | `RatingManager.kt` |

### Mapeo de Rating a Feedback

| Rating | Feedback Text |
|--------|---------------|
| 1 | Necesita mejorar |
| 2 | Regular |
| 3 | Bueno |
| 4 | Muy bueno |
| 5 | Excelente servicio |

### Integración con Sistemas Externos

```javascript
// Ejemplo: Programar evaluación cuando termina una reunión
const response = await fetch(
  'https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/programar-evaluacion',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      salon: 'Sala Santo Domingo',
      hora_fin: '2026-04-17T21:00:00Z',
      nombre_reserva: 'Juan Pérez'
    })
  }
);
```

### Troubleshooting

| Problema | Solución |
|----------|----------|
| Robot no navega | Verificar que waypoint existe en el robot |
| Evaluación no se envía | Verificar conectividad y logs de RatingManager |
| Botones no funcionan | En modo RatingManager, back/skip/home están deshabilitados (15 min) |
| TTS no habla | Verificar volumen y permisos de TTS |
| Timeout inmediato | Verificar que hora_fin no esté en el pasado |

---

## Modo Guia (Tour Guiado por Evento)

### Descripción

El robot Temi puede participar en **tours guiados** donde recibe un evento con waypoints, contenido multimedia y navegación programada. Cuando se activa un tour:
- El robot espera en un **waypoint inicial** con la pantalla de bienvenida
- El usuario tapa un botón para iniciar
- El robot reproduce un **video en loop** mientras navega al **waypoint final** con **face tracking** activo
- Una vez llegado, reproduce un **TTS de llegada** y retorna a home base
- Todo el flujo es **mutuamente exclusivo** con Patrullaje y Rating (solo uno activo a la vez)

### Arquitectura

```
┌─────────────────┐     POST      ┌──────────────────────┐
│  Sistema        │ ────────────► │  Edge Function       │
│  Externo        │               │  activar-guia        │
└─────────────────┘               └──────────┬───────────┘
                                             │
                                             ▼
                                  ┌──────────────────────┐
                                  │  Tabla: guias        │
                                  │  (Supabase)          │
                                  └──────────┬───────────┘
                                             │
                        Polling cada 30s     │
                                             ▼
┌─────────────────┐               ┌──────────────────────┐
│  Robot Temi     │ ◄──────────── │  Edge Function       │
│  (App Android)  │    GET        │  guia-pendiente      │
└──────┬──────────┘               └──────────────────────┘
       │                                    │
       │ POST {id, estado_final}            │
       │                                    ▼
       └───────────────────────────────────►│
                                  ┌──────────────────────┐
                                  │  finalizar-guia      │
                                  │  (row UPDATE)        │
                                  └──────────────────────┘
```

### Componentes

#### 1. Edge Functions (Supabase)

| Función | Método | Descripción |
|---------|--------|-------------|
| `activar-guia` | POST | Crea o programa un nuevo tour guiado |
| `guia-pendiente` | GET | Retorna guia pendiente (reclamo atómico) |
| `finalizar-guia` | POST | Marca la guia como completada/expirada/cancelada |

#### 2. Tabla `guias`

```sql
CREATE TABLE guias (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nombre_evento TEXT NOT NULL,                -- Nombre del evento (max 200 chars)
  descripcion TEXT,                           -- Descripción opcional
  waypoint_inicial TEXT NOT NULL,             -- Waypoint donde espera el robot
  waypoint_final TEXT NOT NULL,               -- Waypoint de destino
  hora_inicio TIMESTAMPTZ NOT NULL,           -- Cuándo empieza el tour
  duracion_horas NUMERIC(3,1) NOT NULL,      -- Duración máxima (0.5 - 8 horas)
  imagen_fondo_url TEXT,                      -- URL de imagen fullscreen (waiting)
  video_loop_url TEXT,                        -- URL de video durante navegación
  bienvenida_tts TEXT NOT NULL,              -- TTS al iniciar
  llegada_tts TEXT,                           -- TTS al llegar (opcional)
  etiqueta_boton TEXT NOT NULL,              -- Texto del botón de inicio
  estado TEXT DEFAULT 'programada',           -- programada|esperando_usuario|guiando|completada|expirada|cancelada
  expires_at TIMESTAMPTZ NOT NULL,            -- Auto-calculado (trigger)
  finalizado_at TIMESTAMPTZ,                  -- Timestamp de finalización
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Trigger para calcular expires_at
CREATE TRIGGER trg_guias_expires_at
    BEFORE INSERT OR UPDATE OF hora_inicio, duracion_horas ON guias
    FOR EACH ROW
    EXECUTE FUNCTION set_expires_at_from_duracion();

-- Índices para polling eficiente
CREATE INDEX idx_guias_pendientes
    ON guias (estado, hora_inicio)
    WHERE estado IN ('programada','esperando_usuario','guiando');

CREATE INDEX idx_guias_expires
    ON guias (expires_at)
    WHERE estado IN ('esperando_usuario','guiando');
```

#### 3. Componentes Android

| Archivo | Descripción |
|---------|-------------|
| `GuiaManager.kt` | Polling cada 30s, state machine, control de timeouts, expiration |
| `GuiaActivity.kt` | UI dual (WAITING + GUIDING), manejo de botón, VideoView, TTS |
| `ExclusiveModeArbiter.kt` | Mutex singleton para serializar Guia, Announcement, Rating |
| `RobotGateway.kt` | Interfaz sobre TemiController para inyección en tests (no SDK acoplado) |
| `RobotStateSnapshot.kt` | Captura/restore del estado del robot (volumen, velocidad, kiosk) |
| `GuiaPayload.kt` | Data class con JSON parsing + fechas ISO-8601 |
| `GuiaState.kt` | Sealed class: Idle, Waiting, Guiding, Finishing |

### API de Activación

**Endpoint:** `POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/activar-guia`

**Request Body:**
```json
{
  "nombre_evento": "Tour Virtual - Museos de Arte",
  "descripcion": "Visita guiada a las galerías principales",
  "waypoint_inicial": "lobby",
  "waypoint_final": "galeria_norte",
  "hora_inicio": "2026-05-01T14:00:00Z",
  "duracion_horas": 1.5,
  "imagen_fondo_url": "https://example.com/bienvenida.png",
  "video_loop_url": "https://example.com/tour.mp4",
  "bienvenida_tts": "Bienvenido al tour virtual. Tapa el botón para empezar.",
  "llegada_tts": "Hemos llegado a la galería norte. Muchas gracias por visitarnos.",
  "etiqueta_boton": "Comenzar Tour"
}
```

**Response (Success):**
```json
{
  "success": true,
  "action": "programada",
  "guia": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "estado": "programada",
    "expires_at": "2026-05-01T15:30:00Z"
  }
}
```

**Response (409 Conflict — ya existe una guia activa):**
```json
{
  "error": "CONFLICT",
  "message": "Ya existe una guia activa o programada.",
  "existing_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

### API de Polling

**Endpoint:** `GET https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/guia-pendiente`

No requiere body. Usa el mismo header de autenticación.

**Response (Sin guia pendiente):**
```json
{
  "pendiente": false
}
```

**Response (Guia pendiente — estado claimed atomicamente a esperando_usuario):**
```json
{
  "pendiente": true,
  "guia": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "nombre_evento": "Tour Virtual - Museos de Arte",
    "descripcion": "Visita guiada a las galerías principales",
    "waypoint_inicial": "lobby",
    "waypoint_final": "galeria_norte",
    "hora_inicio": "2026-05-01T14:00:00Z",
    "imagen_fondo_url": "https://example.com/bienvenida.png",
    "video_loop_url": "https://example.com/tour.mp4",
    "bienvenida_tts": "Bienvenido al tour virtual. Tapa el botón para empezar.",
    "llegada_tts": "Hemos llegado a la galería norte. Muchas gracias por visitarnos.",
    "etiqueta_boton": "Comenzar Tour",
    "expires_at": "2026-05-01T15:30:00Z"
  }
}
```

### API de Finalización

**Endpoint:** `POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/finalizar-guia`

**Request Body (Usuario completó el tour):**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "estado_final": "completada"
}
```

**Request Body (Tour expiró sin terminar):**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "estado_final": "expirada"
}
```

**Request Body (Cancelación externa):**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "estado_final": "cancelada"
}
```

**Response (HTTP 200):**
```json
{
  "success": true
}
```

### State Machine

```
┌────────┐
│ Idle   │ (esperando guia)
└────┬───┘
     │ guia-pendiente retorna
     ▼
┌────────────────────┐
│ Waiting            │ (robot en waypoint_inicial, esperando usuario)
│ - Kiosk Mode ON    │
│ - Pantalla: evento │
│ - TTS cada 30s     │
│ - Timeout: expirada│
└────┬───────────┬───┘
     │ timeout   │ usuario tapa botón
     │ (expira)  ▼
     │      ┌────────────────────┐
     │      │ Guiding            │ (robot navegando)
     │      │ - Video en loop     │
     │      │ - Face tracking ON  │
     │      │ - Velocidad: SLOW   │
     │      │ - Timeout: expirada │
     │      └────┬───────────┬────┘
     │           │           │ arrival en waypoint_final
     │           │ timeout   ▼
     │           │      ┌─────────────────┐
     │           │      │ Finishing       │ (cleanup)
     │           │      │ - TTS llegada   │
     │           │      │ - Restore state │
     │           │      │ - Release arbiter
     │           │      └────┬────────────┘
     │           └───────────┘
     └───────────────────────┘
             ▼
        ┌────────┐
        │ Idle   │ (done)
        └────────┘
```

### Flujo de Ejecución

1. Sistema externo llama a `activar-guia` con datos completos del tour
2. Edge Function valida payload y verifica que no haya otra guia activa (409 si existe)
3. Inserta en tabla `guias` con `estado='programada'`, calcula `expires_at` automáticamente
4. App Android hace polling a `guia-pendiente` cada 30 segundos
5. Cuando `hora_inicio <= now()`:
   - Edge Function reclama la fila atomicamente: `estado: programada → esperando_usuario`
   - GuiaManager adquiere lock del ExclusiveModeArbiter (falla si Announcement o Rating activo)
   - Guarda snapshot del robot (volumen, velocidad, kiosk, navigation billboard)
   - Configura volumen 7, velocidad SLOW, Kiosk Mode ON, Navigation Billboard OFF (hidden)
   - Navega a `waypoint_inicial` con callback de llegada
   - Una vez llegado: abre GuiaActivity en estado WAITING con imagen de fondo + botón
   - Inicia TTS de invitación cada 30 segundos
6. Usuario tapa el botón:
   - GuiaActivity emite broadcast INTENT_GUIA_USER_TAPPED_START
   - GuiaManager transiciona a GUIDING
   - Activa Face Tracking
   - Habla `bienvenida_tts`
   - Inicia video en loop en la Activity (Fade+Slide de WAITING a GUIDING)
   - Navega a `waypoint_final` con callback de llegada
7. Robot llega a `waypoint_final`:
   - Detiene video
   - Habla `llegada_tts` si no está vacío
   - POST a `finalizar-guia` con `estado_final: completada`
   - Libera lock de ExclusiveModeArbiter
   - Restaura snapshot (volumen, velocidad, Kiosk OFF, Navigation Billboard visible)
   - Navega a home base
   - Cierra GuiaActivity
8. Si pasan `duracion_horas` sin terminar:
   - GuiaManager detecta `expires_at < now()`
   - POST a `finalizar-guia` con `estado_final: expirada`
   - Ejecuta cleanup idéntico a paso 7
9. Si sistema externo POST a `finalizar-guia` con `estado_final: cancelada`:
   - GuiaManager detecta cambio en siguiente poll
   - Ejecuta cleanup y libera arbiter

### Parámetros Configurables

| Parámetro | Valor | Ubicación |
|-----------|-------|-----------|
| Intervalo de polling | 30 segundos | `GuiaManager.kt` |
| Intervalo de TTS (Waiting) | 30 segundos | `GuiaManager.kt` |
| Volumen durante tour | 7 (de 0-10) | `GuiaManager.kt` |
| Velocidad de navegación | SLOW | `GuiaManager.kt` |
| Duración mínima de tour | 0.5 horas | Edge Function |
| Duración máxima de tour | 8 horas | Edge Function |

### Limitaciones Conocidas

- **El robot NO camina hacia atrás** — el SDK 1.136.0 no soporta retroceso. Se compensa con:
  - Face Tracking activo durante la navegación (el usuario ve la pantalla)
  - Velocidad SLOW para control fino
  - Video en loop para distracción visual
  - Si es necesario cambiar el orden de waypoints, hacerlo externamente (no en el tour)
- **Primera carga de video** — el VideoView inicia buffering al comenzar GUIDING; se tolera 1-2s de latencia en Wi-Fi de venue
- **Si video falla** — la Activity caerá a imagen de fondo; navegación continúa sin bloquearse
- **TTS interrumpido por navegación** — no se silencia; coexisten TTS de bienvenida/llegada + audio ambiental del venue
- **Una guia a la vez** — ExclusiveModeArbiter previene solapamiento con Patrullaje y Rating; si ambos quedan programados, se toman en el orden que `hora_inicio` les toque primero

### Integración con Sistemas Externos

Para integrar desde un CMS, scheduler cron, o aplicación web, hacer POST a la Edge Function:

```javascript
// Ejemplo en JavaScript
const response = await fetch(
  'https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/activar-guia',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      nombre_evento: 'Exposición Temporal - Arte Contemporáneo',
      descripcion: 'Tour guiado por las obras premiadas',
      waypoint_inicial: 'entrada_principal',
      waypoint_final: 'sala_principal',
      hora_inicio: '2026-05-01T10:00:00Z',
      duracion_horas: 2,
      imagen_fondo_url: 'https://cdn.example.com/expo-banner.png',
      video_loop_url: 'https://cdn.example.com/tour-video.mp4',
      bienvenida_tts: 'Bienvenido. Esta es una exhibición de arte contemporáneo.',
      llegada_tts: 'Gracias por visitarnos. El tour ha finalizado.',
      etiqueta_boton: 'Iniciar Recorrido'
    })
  }
);

const data = await response.json();
if (response.ok) {
  console.log('Tour programado:', data.guia.id);
} else {
  console.error('Error:', data.error);
}
```

### Troubleshooting

| Problema | Solución |
|----------|----------|
| Imagen de bienvenida no carga | Verificar que imagen_fondo_url es URL pública y accesible desde el venue |
| Video no se reproduce | Verificar conectividad Wi-Fi del venue; video_loop_url debe estar disponible en HTTP(S) |
| Robot no navega de waypoint_inicial a final | Verificar que ambos waypoints existen en el robot; comprobar connectivity del Temi SDK |
| Botón no responde | Verificar que GuiaActivity está en foreground; comprobar que otro modo (Patrullaje/Rating) no está activo |
| Modo no termina (queda en Guiding) | Verifica que `expires_at` no está en el futuro; POST manual a `finalizar-guia` con `estado_final: expirada` |
| Conflicto 409 al activar | Otra guia está en estado programada/esperando_usuario/guiando; POST a `finalizar-guia` para cancelarla primero |
| TTS no se escucha | Verificar volumen del robot (GuiaManager lo sube a 7); comprobar permisos de TTS en settings |
| Face tracking no activa | Verificar que TemiController.enableFaceTracking() tiene permisos SETTINGS en Manifest |

### Modo Guia → Limitaciones Conocidas

**Navegación en reversa (backwards-walk): SOPORTADO por el SDK.**
Investigación del 2026-04-29: La clase `Robot` del Temi SDK expone un overload `goTo(location: String, backwards: Boolean)` documentado en el repositorio oficial (`sdk/src/main/java/com/robotemi/sdk/Robot.kt`). Pasar `backwards = true` hace que el robot navegue de espaldas, manteniendo la pantalla orientada hacia el visitante que lo sigue.
`TemiController.goToBackwards(place: String)` implementa este overload via reflection (mismo patrón que el `goTo` estándar), con fallback a `goTo` normal si el método no está disponible en la versión del SDK instalada.
Se usa en `GuiaManager.onUserTappedStart` para el viaje guiado a `waypoint_final`.

### Integración con MainActivity

`MainActivity.onResume()` (líneas 113-118 de `MainActivity.kt`) re-aserta Kiosk Mode cada vez que MainActivity vuelve al primer plano — es decir, después de que GuiaActivity, AnnouncementActivity o RatingActivity terminan y el control regresa a la pantalla principal.
Esto es una capa de defensa: cualquier activity overlay que haya desactivado Kiosk Mode no puede dejar ese estado filtrado hacia el resto del flujo.
El método simplemente llama `TemiController.setKioskModeOn(true)` y registra la acción en logcat con el tag `TemiBridge`.
