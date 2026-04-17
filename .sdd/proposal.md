# Proposal: Rating Mode - Evaluación de Servicio en Salones

## Título
Modo Rating para Evaluación de Servicio Post-Reunión

## Problema a Resolver
El sistema necesita que el robot Temi vaya automáticamente a un salón 3 minutos antes de que termine una reunión, muestre una pantalla de rating/evaluación, detecte personas cercanas para invitarlas a evaluar el servicio, y envíe la evaluación a un sistema externo.

## Objetivo
Implementar un modo de evaluación donde:
1. Un sistema externo notifica al robot sobre una reunión próxima a terminar
2. El robot va al salón correspondiente 3 minutos antes del fin
3. Muestra pantalla de rating con estrellas (1-5)
4. Detecta personas cercanas y las invita a evaluar con TTS personalizado
5. Al recibir evaluación, agradece y envía datos al sistema externo
6. Después de 15 minutos o evaluación completada, vuelve a base de carga

## Alcance Incluido
- Edge Function `programar-evaluacion` para recibir datos de reunión
- Edge Function `evaluacion-pendiente` para polling del robot
- `RatingManager.kt` (NUEVO) para programar y controlar el modo rating
- Modificar `RatingActivity.kt` para recibir extras y enviar a API externa
- Detección de personas cercanas (SDK Temi)
- TTS personalizado por salón
- Envío de evaluación a API externa (create-evaluation)
- Mapeo de salones a waypoints

## Alcance Excluido
- **Rediseño de RatingActivity** (ya existe con WebView y estrellas)
- **Rediseño de rating.html** (ya tiene UI funcional)
- Gestión de reuniones (viene del sistema externo)
- Historial local de evaluaciones (se envía al sistema externo)
- Múltiples evaluaciones simultáneas
- Modificación del sistema externo

## Enfoque Propuesto
1. Edge Function recibe: salon, hora_fin, nombre_reserva
2. Calcular tiempo de espera (hora_fin - 3 minutos - ahora)
3. Programar navegación al waypoint del salón
4. Mostrar RatingActivity con estrellas interactivas
5. Activar detección de personas → TTS invitación
6. Al tocar estrella: enviar POST a create-evaluation, agradecer, ir a base
7. Timeout 15 minutos → ir a base sin evaluación

## Mapeo de Salones

| Salón | Waypoint |
|-------|----------|
| Sala Duarte | salonduarte |
| Sala Enriquillo | salonenriquillo |
| Sala Multimedia | salonmultimedia |
| Sala Quisqueya | salonquisqueya |
| Sala Santo Domingo | salonsantodomingo |

## API Externa para Envío de Evaluación

**Endpoint:** `POST https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation`

**Request Body:**
```json
{
  "rating": 5,
  "customer_name": "Juan Pérez",
  "salon": "Sala Santo Domingo",
  "feedback_text": "Excelente servicio"
}
```

**Mapeo de feedback_text por rating:**
| Rating | Texto |
|--------|-------|
| 1 ⭐ | "Necesita mejorar" |
| 2 ⭐⭐ | "Regular" |
| 3 ⭐⭐⭐ | "Bueno" |
| 4 ⭐⭐⭐⭐ | "Muy bueno" |
| 5 ⭐⭐⭐⭐⭐ | "Excelente servicio" |

## Riesgos Principales

| Riesgo | Mitigación |
|--------|------------|
| Robot ocupado en otra tarea | Verificar estado antes de iniciar |
| Persona no evalúa | Timeout 15 minutos, volver a base |
| API externa no responde | Retry con backoff, agradecer igual |
| Waypoint no existe | Validar mapeo, loggear error |

## Supuestos Abiertos
- Los waypoints de salones ya están configurados en el robot
- El sistema externo enviará hora_fin en formato ISO 8601
- La API create-evaluation está disponible y funcional

## Decisiones Confirmadas
1. **Tiempo de anticipación**: 3 minutos antes del fin de reunión
2. **Tiempo en salón**: Máximo 15 minutos
3. **Al evaluar**: Agradecer y volver a base de carga
4. **Sin evaluación**: Después de 15 min, volver a base
5. **TTS**: Personalizado por salón, invitando a evaluar
