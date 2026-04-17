# Proposal: Modo Anuncio con Patrullaje

## Título
API de Anuncios con Modo Patrullaje para Robot Temi

## Problema a Resolver
El coworking necesita una forma de comunicar eventos y anuncios a los usuarios de manera activa. Actualmente el robot Temi solo responde a interacciones directas (QR, comandos). No existe un mecanismo para que el robot patrulla las instalaciones anunciando información de forma proactiva durante un período determinado.

## Objetivo
Crear una API que permita activar un "modo anuncio" donde el robot:
1. Recibe texto de anuncio, imagen opcional y duración en minutos
2. Entra en modo patrullaje (visita waypoints secuencialmente)
3. En cada waypoint, anuncia el texto en voz alta
4. Muestra la imagen del evento en pantalla durante el anuncio
5. Continúa el ciclo hasta que expire el tiempo configurado

## Alcance Incluido
- **Edge Function** en Supabase como endpoint API
- **Tabla `anuncios`** para persistir anuncios activos
- **AnnouncementSkill** en Android para ejecutar el patrullaje
- **UI de anuncio** mostrando imagen + texto mientras habla
- **Polling desde la app** para detectar anuncios activos
- **Lógica de patrullaje** visitando waypoints de `salones`

## Alcance Excluido
- Dashboard web para gestionar anuncios (futuro)
- Múltiples anuncios simultáneos (v1 = 1 anuncio activo)
- Priorización de anuncios
- Historial de anuncios completados
- Notificaciones push al robot (usaremos polling)

## Enfoque Propuesto

### Backend (Supabase)
1. **Tabla `anuncios`**:
   - `id`, `texto`, `imagen_url`, `duracion_minutos`, `activo`, `created_at`, `expires_at`
   
2. **Edge Function `activar-anuncio`**:
   - POST con `texto`, `imagen_url`, `duracion_minutos`
   - Desactiva anuncios previos
   - Crea nuevo anuncio con `expires_at` calculado
   - Retorna el anuncio creado

3. **Edge Function `anuncio-activo`** (o query directo):
   - GET retorna anuncio activo si `activo=true` y `expires_at > now()`

### Android (TemiBridge)

**DESCUBRIMIENTO CLAVE:** El SDK de Temi (1.129.1+) tiene método `patrol()` nativo:
```kotlin
boolean patrol(List<String> locations, boolean nonstop, int times, int waiting)
```
- `locations`: Mínimo 3 ubicaciones, pueden repetirse
- `nonstop`: Si true, no espera en cada ubicación
- `times`: 0 = infinito, 1 = una vez
- `waiting`: Segundos de espera (3-60)

1. **AnnouncementService** (o coroutine en MainActivity):
   - Polling cada 30s a `anuncio-activo`
   - Si hay anuncio activo → inicia modo patrullaje con `patrol()`
   
2. **PatrolMode usando SDK nativo**:
   - **Velocidad lenta**: `setGoToSpeed(SpeedLevel.SLOW)` antes de iniciar
   - Usar `Robot.getInstance().patrol(locations, nonstop=false, times=0, waiting=10)`
   - Escuchar `OnGoToLocationStatusChangedListener` para detectar llegada (descriptionId=500)
   - En cada llegada: `speak(texto)` (imagen ya visible en pantalla)
   - Usar `stopMovement()` cuando expire el anuncio
   - Restaurar velocidad original al finalizar

3. **AnnouncementActivity** (pantalla de anuncio):
   - Pantalla fullscreen con imagen del evento + texto
   - **Se muestra al iniciar el patrullaje y permanece visible durante TODO el recorrido**
   - No se oculta entre waypoints
   - Se cierra solo cuando el anuncio expira

## Riesgos Principales
| Riesgo | Mitigación |
|--------|------------|
| Robot ocupado en otra tarea | Verificar estado antes de iniciar patrullaje |
| Polling consume batería | Intervalo de 30-60s, solo cuando app activa |
| Waypoints no existen | Validar contra `TemiController.getSavedLocations()` |
| Imagen no carga | Placeholder por defecto, timeout de carga |
| Anuncio expira mid-patrol | Verificar `expires_at` en cada ciclo |

## Supuestos Abiertos
- ¿Qué hacer si el robot está navegando? → Propuesta: esperar o cancelar navegación actual
- ¿Mostrar imagen en pantalla del robot o solo hablar? → Propuesta: ambos
- ¿Intervalo de polling? → Propuesta: 30 segundos

## Decisiones Confirmadas
1. **Imagen**: Se sube a Supabase Storage, la API recibe la URL del storage
2. **Al terminar**: El robot vuelve a "home base"
3. **Autenticación API**: No requerida (pública)
4. **Waypoints**: Se envían en el JSON de la API (no se leen de tabla `salones`)
5. **Anuncio activo**: Si se envía nuevo anuncio, reemplaza al anterior
