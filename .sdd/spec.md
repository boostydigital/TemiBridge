# Spec: Modo Anuncio con Patrullaje

## Requisitos Funcionales

### RF-01: Activar Anuncio via API
- **Endpoint**: `POST /functions/v1/activar-anuncio`
- **Autenticación**: No requerida (API pública)
- **Body**:
  ```json
  {
    "texto": "string (requerido, max 500 chars)",
    "imagen_url": "string (opcional, URL de Supabase Storage)",
    "duracion_minutos": "number (requerido, 1-120)",
    "waypoints": ["string"] (requerido, mínimo 3 ubicaciones)
  }
  ```
- **Response 200**:
  ```json
  {
    "success": true,
    "anuncio": {
      "id": "uuid",
      "texto": "...",
      "imagen_url": "...",
      "duracion_minutos": 15,
      "waypoints": ["Sala_A", "Sala_B", "Recepcion"],
      "activo": true,
      "created_at": "ISO8601",
      "expires_at": "ISO8601"
    }
  }
  ```
- **Comportamiento**: Desactiva cualquier anuncio previo antes de crear el nuevo

### RF-02: Consultar Anuncio Activo
- **Endpoint**: `GET /functions/v1/anuncio-activo`
- **Response 200** (hay anuncio):
  ```json
  {
    "activo": true,
    "anuncio": { ... }
  }
  ```
- **Response 200** (no hay anuncio):
  ```json
  {
    "activo": false,
    "anuncio": null
  }
  ```

### RF-03: Modo Patrullaje en Robot (usando SDK nativo)
- Usar método `patrol()` del SDK Temi (disponible desde 1.129.1)
- **Firma:** `boolean patrol(List<String> locations, boolean nonstop, int times, int waiting)`
- Configuración:
  - `locations`: Lista de waypoints de tabla `salones` (mínimo 3, pueden repetirse)
  - `nonstop`: `false` (para que espere en cada ubicación)
  - `times`: `0` (infinito, hasta que expire el anuncio)
  - `waiting`: `10` segundos (tiempo de espera en cada ubicación)
- **Velocidad lenta**: Antes de iniciar patrullaje, llamar `setGoToSpeed(SpeedLevel.SLOW)`
  - SDK 1.136.0 soporta: `VERY_HIGH`, `HIGH`, `MEDIUM`, `SLOW`
  - SDK 1.137.1+ añade: `VERY_SLOW`
  - Requiere permiso `SETTINGS`
- **Pantalla de anuncio visible durante TODO el recorrido** (no solo en llegadas)
- En cada llegada (detectada via `OnGoToLocationStatusChangedListener`, descriptionId=500):
  1. Habla el texto del anuncio con `speak()`
- Detener con `stopMovement()` cuando `expires_at < now()`
- Al finalizar, restaurar velocidad original con `setGoToSpeed(velocidadOriginal)`
- El ciclo se repite automáticamente por el SDK hasta llamar `stopMovement()`

### RF-04: Pantalla de Anuncio (visible durante TODO el patrullaje)
- **Se muestra al iniciar el patrullaje y permanece visible hasta que termine**
- Fullscreen con fondo oscuro
- Imagen centrada (si existe), cargada desde Supabase Storage
- Texto del anuncio visible debajo de la imagen
- Indicador de "Anunciando..." o similar
- La pantalla NO se oculta entre waypoints, solo al finalizar el anuncio

### RF-05: Cancelación Automática
- Cuando `expires_at` pasa, el robot:
  1. Detiene el patrullaje con `stopMovement()`
  2. Restaura velocidad original
  3. Vuelve a "home base" con `goTo("home base")`
  4. Retorna a pantalla principal (QR scanner)

## Requisitos No Funcionales

### RNF-01: Latencia
- Polling máximo cada 30 segundos
- Tiempo de respuesta API < 2 segundos

### RNF-02: Disponibilidad
- Edge Function debe estar disponible 24/7
- Si falla el polling, reintentar con backoff exponencial

### RNF-03: Seguridad
- API sin autenticación JWT (acceso público para v1)
- Validación de inputs en Edge Function
- Rate limiting implícito de Supabase

## Escenarios de Uso

### Escenario 1: Activar anuncio de evento
**Given** el robot está en modo idle (pantalla QR)
**When** se hace POST a `/activar-anuncio` con texto="Workshop de IA a las 3pm en Sala Turing" y duracion=30
**Then** el robot inicia patrullaje, visitando cada salón y anunciando el mensaje
**And** después de 30 minutos, vuelve a modo idle

### Escenario 2: Reemplazar anuncio activo
**Given** hay un anuncio activo con 10 minutos restantes
**When** se hace POST a `/activar-anuncio` con nuevo texto
**Then** el anuncio anterior se desactiva
**And** el robot comienza a anunciar el nuevo mensaje

### Escenario 3: Consultar sin anuncio activo
**Given** no hay anuncios activos
**When** se hace GET a `/anuncio-activo`
**Then** retorna `{ "activo": false, "anuncio": null }`

### Escenario 4: Robot ocupado
**Given** el robot está navegando a un destino (invitación)
**When** se detecta un anuncio activo via polling
**Then** el robot espera a completar la navegación actual
**And** luego inicia el modo patrullaje

## Criterios de Aceptación

- [ ] POST `/activar-anuncio` crea registro en tabla `anuncios`
- [ ] GET `/anuncio-activo` retorna anuncio si `activo=true AND expires_at > now()`
- [ ] App Android hace polling cada 30s cuando está en foreground
- [ ] Robot visita al menos 3 waypoints en un ciclo de patrullaje
- [ ] Robot habla el texto en cada waypoint
- [ ] Pantalla muestra imagen durante el anuncio
- [ ] Patrullaje se detiene automáticamente al expirar
- [ ] Nuevo anuncio reemplaza al anterior

## Restricciones Técnicas

1. **Supabase Edge Functions** usan Deno runtime
2. **TemiController** usa reflexión para compatibilidad con SDK
3. **Waypoints** deben existir en el mapa del robot (validar contra `salones.temi_place`)
4. **Polling** solo cuando la app está en foreground (usar `Lifecycle`)
5. **Imágenes** deben ser URLs públicas accesibles (no requieren auth)

## Casos Límite

| Caso | Comportamiento Esperado |
|------|------------------------|
| `duracion_minutos = 0` | Error 400: duración mínima 1 minuto |
| `duracion_minutos > 120` | Error 400: duración máxima 2 horas |
| `texto` vacío | Error 400: texto requerido |
| `texto` > 500 chars | Error 400: texto muy largo |
| `imagen_url` inválida | Ignorar imagen, solo mostrar texto |
| `waypoints` < 3 | Error 400: mínimo 3 waypoints requeridos |
| Todos los waypoints fallan | Quedarse en posición actual, seguir anunciando |
| Robot sin conexión | Reintentar polling cuando recupere conexión |

## Modelo de Datos

### Tabla: `anuncios`
```sql
CREATE TABLE anuncios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  texto TEXT NOT NULL CHECK (char_length(texto) <= 500),
  imagen_url TEXT,
  duracion_minutos INTEGER NOT NULL CHECK (duracion_minutos BETWEEN 1 AND 120),
  waypoints JSONB NOT NULL DEFAULT '[]'::jsonb,
  activo BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  
  -- Validar que waypoints tenga al menos 3 elementos
  CONSTRAINT waypoints_min_3 CHECK (jsonb_array_length(waypoints) >= 3)
);

-- Índice para consultas de anuncio activo
CREATE INDEX idx_anuncios_activo ON anuncios (activo, expires_at) WHERE activo = true;

-- RLS: Permitir lectura pública (para polling del robot)
ALTER TABLE anuncios ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anuncios públicos para lectura" ON anuncios FOR SELECT USING (true);
```

## Diagrama de Flujo

```
[API POST /activar-anuncio]
        │
        ▼
[Desactivar anuncios previos]
        │
        ▼
[Crear nuevo anuncio con expires_at]
        │
        ▼
[Robot polling cada 30s]
        │
        ▼
[¿Hay anuncio activo?]──No──▶ [Continuar en modo idle]
        │
       Yes
        ▼
[Obtener waypoints del anuncio (vienen en JSON)]
        │
        ▼
[Guardar velocidad actual: getGoToSpeed()]
        │
        ▼
[setGoToSpeed(SpeedLevel.SLOW)]
        │
        ▼
[Mostrar AnnouncementActivity (imagen + texto) - PERMANECE VISIBLE]
        │
        ▼
[Robot.patrol(locations, nonstop=false, times=0, waiting=10)]
        │
        ▼
┌─────────────────────────────────────────┐
│  SDK maneja el loop automáticamente     │
│  (imagen visible durante todo el loop)  │
│  ┌───────────────────────────────────┐  │
│  │ OnGoToLocationStatusChanged       │  │
│  │ (descriptionId == 500 = llegada)  │  │
│  │         │                         │  │
│  │         ▼                         │  │
│  │ speak(texto)                      │  │
│  │         │                         │  │
│  │         ▼                         │  │
│  │ SDK espera 10s → siguiente lugar  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
        │
        ▼
[Timer verifica expires_at cada 30s]
        │
        ▼
[¿Expiró?]──No──▶ [Continuar patrullaje]
        │
       Yes
        ▼
[stopMovement()]
        │
        ▼
[Cerrar AnnouncementActivity]
        │
        ▼
[Restaurar velocidad: setGoToSpeed(velocidadOriginal)]
        │
        ▼
[goTo("home base")]
        │
        ▼
[Retornar a pantalla QR (MainActivity)]
```
