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
