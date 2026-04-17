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
