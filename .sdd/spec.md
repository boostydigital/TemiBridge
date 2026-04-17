# Spec: Rating Mode - Evaluación de Servicio en Salones

## Requisitos Funcionales

### RF-01: Programar Evaluación via API
- **Endpoint**: `POST /functions/v1/programar-evaluacion`
- **Autenticación**: No requerida (API pública)
- **Body**:
  ```json
  {
    "salon": "Sala Santo Domingo",
    "hora_fin": "2026-04-17T18:00:00Z",
    "nombre_reserva": "Juan Pérez"
  }
  ```
- **Response 200**:
  ```json
  {
    "success": true,
    "evaluacion": {
      "id": "uuid",
      "salon": "Sala Santo Domingo",
      "waypoint": "salonsantodomingo",
      "hora_fin": "2026-04-17T18:00:00Z",
      "hora_llegada": "2026-04-17T17:57:00Z",
      "nombre_reserva": "Juan Pérez",
      "estado": "programada"
    }
  }
  ```
- **Comportamiento**: Calcula hora_llegada = hora_fin - 3 minutos

### RF-02: Consultar Evaluación Pendiente
- **Endpoint**: `GET /functions/v1/evaluacion-pendiente`
- **Response 200** (hay evaluación):
  ```json
  {
    "pendiente": true,
    "evaluacion": {
      "id": "uuid",
      "salon": "Sala Santo Domingo",
      "waypoint": "salonsantodomingo",
      "hora_llegada": "2026-04-17T17:57:00Z",
      "nombre_reserva": "Juan Pérez"
    }
  }
  ```
- **Response 200** (no hay evaluación):
  ```json
  {
    "pendiente": false,
    "evaluacion": null
  }
  ```

### RF-03: Mapeo de Salones a Waypoints
| Salón | Waypoint |
|-------|----------|
| Sala Duarte | salonduarte |
| Sala Enriquillo | salonenriquillo |
| Sala Multimedia | salonmultimedia |
| Sala Quisqueya | salonquisqueya |
| Sala Santo Domingo | salonsantodomingo |

### RF-04: Navegación al Salón
- Robot hace polling cada 30s a `evaluacion-pendiente`
- Cuando `hora_llegada <= now()`:
  1. Navegar al waypoint del salón con `goTo(waypoint)`
  2. Activar Kiosk Mode para ocultar UI de Temi
  3. Mostrar `RatingActivity` con estrellas

### RF-05: Pantalla de Rating
- Fullscreen con fondo elegante
- 5 estrellas interactivas (1-5)
- Texto: "¿Cómo fue tu experiencia en [Nombre Salón]?"
- Subtexto: "Toca las estrellas para evaluar"
- Al tocar estrella: animación de selección

### RF-06: Detección de Personas y TTS
- Usar `OnBeWithMeStatusChangedListener` o detección de usuario cercano
- Al detectar persona, hablar TTS personalizado:
  - "Hola, espero que hayas disfrutado tu reunión en [Sala]. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla."
- Repetir TTS cada 60 segundos si no hay evaluación

### RF-07: Envío de Evaluación a Sistema Externo
- **Endpoint**: `POST https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation`
- **Body**:
  ```json
  {
    "rating": 5,
    "customer_name": "Juan Pérez",
    "salon": "Sala Santo Domingo",
    "feedback_text": "Excelente servicio"
  }
  ```
- **Mapeo feedback_text por rating**:
  | Rating | feedback_text |
  |--------|---------------|
  | 1 | "Necesita mejorar" |
  | 2 | "Regular" |
  | 3 | "Bueno" |
  | 4 | "Muy bueno" |
  | 5 | "Excelente servicio" |

### RF-08: Agradecimiento y Retorno
- Al recibir evaluación:
  1. Mostrar animación de agradecimiento
  2. TTS: "¡Muchas gracias por tu evaluación! Tu opinión nos ayuda a mejorar."
  3. Esperar 3 segundos
  4. Desactivar Kiosk Mode
  5. Navegar a "home base"

### RF-09: Permanencia en Salón (15 minutos)
- El robot DEBE quedarse 15 minutos en el salón con la pantalla de rating visible
- Durante los 15 minutos:
  - Pantalla de rating siempre visible
  - TTS de invitación cada 60 segundos si detecta persona
  - Esperando que el usuario toque una estrella
- Si el usuario evalúa ANTES de los 15 minutos:
  - Agradecer y volver a base inmediatamente
- Si pasan 15 minutos SIN evaluación:
  1. TTS: "Gracias por visitarnos. Hasta pronto."
  2. Desactivar Kiosk Mode
  3. Navegar a "home base"

## Requisitos No Funcionales

### RNF-01: Latencia
- Polling cada 30 segundos
- Envío de evaluación < 3 segundos

### RNF-02: UX
- Estrellas grandes y fáciles de tocar (mínimo 60dp)
- Feedback visual inmediato al tocar
- Animaciones suaves

### RNF-03: Disponibilidad
- Edge Functions disponibles 24/7
- Retry con backoff si falla envío

## Escenarios de Uso

### Escenario 1: Evaluación exitosa
**Given** hay una reunión programada en Sala Duarte que termina a las 18:00
**When** el sistema externo hace POST a `/programar-evaluacion`
**Then** a las 17:57 el robot va a salonduarte
**And** muestra pantalla de rating
**And** al detectar persona, invita a evaluar
**And** usuario toca 5 estrellas
**And** robot agradece y envía evaluación al sistema externo
**And** robot vuelve a base de carga

### Escenario 2: Timeout sin evaluación
**Given** robot está en salón mostrando pantalla de rating
**When** pasan 15 minutos sin que nadie evalúe
**Then** robot se despide y vuelve a base de carga

### Escenario 3: Múltiples reuniones
**Given** hay reuniones en Sala Duarte (18:00) y Sala Quisqueya (18:30)
**When** se programan ambas evaluaciones
**Then** robot atiende primero Sala Duarte
**And** después de completar, atiende Sala Quisqueya

## Criterios de Aceptación

- [ ] POST `/programar-evaluacion` crea registro con hora_llegada calculada
- [ ] GET `/evaluacion-pendiente` retorna evaluación cuando hora_llegada <= now()
- [ ] Robot navega al waypoint correcto según mapeo de salones
- [ ] Pantalla muestra 5 estrellas interactivas
- [ ] TTS personalizado menciona nombre del salón
- [ ] Al tocar estrella, se envía POST a create-evaluation con datos correctos
- [ ] Robot agradece y vuelve a base después de evaluación
- [ ] Timeout de 15 minutos funciona correctamente

## Restricciones Técnicas

1. **Supabase Edge Functions** usan Deno runtime
2. **TemiController** usa reflexión para compatibilidad con SDK
3. **Waypoints** deben existir en el mapa del robot
4. **Kiosk Mode** necesario para ocultar UI de navegación
5. **RatingActivity ya existe** - Solo agregar funcionalidad, no rediseñar

## Componentes Existentes (NO modificar diseño)

### RatingActivity.kt
- WebView con `rating.html` (estrellas interactivas)
- `RatingBridge` con `submitRating(rating)` vía JavaScript
- `sendRatingAsync()` envía a webhook configurado
- `loadThankYouPage()` muestra agradecimiento
- Auto-return después de 8 segundos

### rating.html
- 5 estrellas con animaciones
- Placeholder `{{EVENT_NAME}}` para nombre del evento/salón
- Botones: Volver, Saltar, Enviar Valoración

### Modificaciones Necesarias
1. **RatingActivity.kt**: Agregar extras para salon, nombre_reserva, API externa
2. **sendRatingAsync()**: Cambiar de GET webhook a POST create-evaluation
3. **RatingManager.kt**: Nuevo - maneja polling y programación
4. **Edge Functions**: programar-evaluacion, evaluacion-pendiente

## Casos Límite

| Caso | Comportamiento Esperado |
|------|------------------------|
| Salón no existe en mapeo | Error 400: salón no válido |
| hora_fin en el pasado | Error 400: hora inválida |
| Robot ocupado con anuncio | Esperar a que termine anuncio |
| API externa no responde | Retry 3 veces, agradecer igual |
| Waypoint no existe | Loggear error, quedarse en posición |

## Modelo de Datos

### Tabla: `evaluaciones_programadas`
```sql
CREATE TABLE evaluaciones_programadas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  salon TEXT NOT NULL,
  waypoint TEXT NOT NULL,
  hora_fin TIMESTAMPTZ NOT NULL,
  hora_llegada TIMESTAMPTZ NOT NULL,
  nombre_reserva TEXT NOT NULL,
  estado TEXT DEFAULT 'programada' CHECK (estado IN ('programada', 'en_proceso', 'completada', 'timeout')),
  rating INTEGER CHECK (rating BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_evaluaciones_pendientes ON evaluaciones_programadas (estado, hora_llegada) 
WHERE estado = 'programada';
```

## Diagrama de Flujo

```
[Sistema Externo POST /programar-evaluacion]
        │
        ▼
[Mapear salon → waypoint]
        │
        ▼
[Calcular hora_llegada = hora_fin - 3min]
        │
        ▼
[Guardar en evaluaciones_programadas]
        │
        ▼
[Robot polling cada 30s a /evaluacion-pendiente]
        │
        ▼
[¿hora_llegada <= now()?]──No──▶ [Continuar polling]
        │
       Yes
        ▼
[goTo(waypoint)]
        │
        ▼
[Activar Kiosk Mode]
        │
        ▼
[Mostrar RatingActivity]
        │
        ▼
┌─────────────────────────────────────────┐
│  Loop de detección (max 15 min)         │
│  ┌───────────────────────────────────┐  │
│  │ ¿Persona detectada?               │  │
│  │         │                         │  │
│  │        Yes                        │  │
│  │         ▼                         │  │
│  │ TTS: "Por favor evalúa..."        │  │
│  │         │                         │  │
│  │         ▼                         │  │
│  │ ¿Usuario tocó estrella?           │  │
│  │    │              │               │  │
│  │   Yes            No (60s)         │  │
│  │    │              │               │  │
│  │    ▼              └──▶ Repetir TTS│  │
│  │ [Enviar evaluación]               │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
        │
        ▼
[POST create-evaluation al sistema externo]
        │
        ▼
[TTS: "¡Gracias por tu evaluación!"]
        │
        ▼
[Desactivar Kiosk Mode]
        │
        ▼
[goTo("home base")]
```

## TTS Personalizados por Salón

| Salón | TTS de Invitación |
|-------|-------------------|
| Sala Duarte | "Hola, espero que hayas disfrutado tu reunión en la Sala Duarte. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla." |
| Sala Enriquillo | "Hola, espero que hayas disfrutado tu reunión en la Sala Enriquillo. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla." |
| Sala Multimedia | "Hola, espero que hayas disfrutado tu reunión en la Sala Multimedia. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla." |
| Sala Quisqueya | "Hola, espero que hayas disfrutado tu reunión en la Sala Quisqueya. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla." |
| Sala Santo Domingo | "Hola, espero que hayas disfrutado tu reunión en la Sala Santo Domingo. Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla." |
