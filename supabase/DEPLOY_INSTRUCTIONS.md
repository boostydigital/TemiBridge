# Instrucciones de Despliegue - Rating Mode

## 1. Crear la tabla en Supabase

Ve a **Supabase Dashboard > SQL Editor** y ejecuta el contenido de:
`supabase/migrations/create_evaluaciones_programadas.sql`

## 2. Desplegar Edge Functions

### Opción A: Usando Supabase CLI

```bash
# Instalar CLI si no lo tienes
npm install -g supabase

# Login
supabase login

# Desplegar funciones
supabase functions deploy programar-evaluacion --project-ref mkakxmjkwcymwosfrwkl
supabase functions deploy evaluacion-pendiente --project-ref mkakxmjkwcymwosfrwkl
```

### Opción B: Desde el Dashboard

1. Ve a **Supabase Dashboard > Edge Functions**
2. Click en **New Function**
3. Nombre: `programar-evaluacion`
4. Copia el contenido de `supabase/functions/programar-evaluacion/index.ts`
5. Click en **Deploy**
6. Repite para `evaluacion-pendiente`

## 3. URL para el Sistema Externo

Una vez desplegadas, la URL para programar evaluaciones es:

```
POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/programar-evaluacion
```

### Request Body:

```json
{
  "salon": "Sala Santo Domingo",
  "hora_fin": "2026-04-17T18:00:00Z",
  "nombre_reserva": "Juan Pérez"
}
```

### Salones válidos:
- Sala Duarte
- Sala Enriquillo
- Sala Multimedia
- Sala Quisqueya
- Sala Santo Domingo

### Response exitosa:

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

## 4. Flujo Completo

1. Sistema externo llama a `POST /programar-evaluacion` con datos de reunión
2. Robot hace polling cada 30s a `/evaluacion-pendiente`
3. Cuando `hora_llegada <= now()`, robot navega al salón
4. Muestra pantalla de rating por 15 minutos
5. Usuario toca estrellas → envía a `create-evaluation` del sistema externo
6. Robot agradece y vuelve a base

## 5. Ejemplo con cURL

```bash
curl -X POST https://mkakxmjkwcymwosfrwkl.supabase.co/functions/v1/programar-evaluacion \
  -H "Content-Type: application/json" \
  -d '{
    "salon": "Sala Santo Domingo",
    "hora_fin": "2026-04-17T18:00:00Z",
    "nombre_reserva": "Juan Pérez"
  }'
```
