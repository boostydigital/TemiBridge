# Plan de QA — Robot Temi / spatium-hub
**Versión**: 1.0  
**Fecha**: 2026-05-06  
**Proyecto Supabase**: `fojrqrkbzsgcefsnwldk` (spatium-hub)  
**IP Robot**: `192.168.191.10`  
**App ID**: `com.spatium.deamon.db.temi`

---

## Prerrequisitos

- [ ] APK debug instalado en el robot (ver sección Deploy)
- [ ] Robot conectado a la misma red WiFi que el PC (`192.168.191.10` responde a ping)
- [ ] Tablas `robot_*` presentes en spatium-hub Dashboard
- [ ] Edge Functions deployadas en spatium-hub
- [ ] `local.properties` apunta a `fojrqrkbzsgcefsnwldk.supabase.co`

---

## Deploy

```powershell
# 1. Conectar ADB
adb connect 192.168.191.10:5555

# 2. Compilar
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug --no-daemon

# 3. Desinstalar versión anterior
adb -s 192.168.191.10:5555 uninstall com.spatium.deamon.db.temi

# 4. Instalar
adb -s 192.168.191.10:5555 install "C:\Users\samir\TemiDeamonDBBuild\app\outputs\apk\debug\app-debug.apk"
```

---

## TC-01 — Pedidos: Cámara detecta persona

**Módulo**: `robot_pedidos` + `robot-crear-pedido`  
**Prioridad**: Alta  
**Precondición**: Robot en `MainActivity`, tabla `robot_pedidos` vacía o con todas las filas en `realizado=true`

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Ejecutar `curl -X POST https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/robot-crear-pedido -H "Authorization: Bearer {SERVICE_ROLE_KEY}" -H "Content-Type: application/json" -d '{"sequence_id":"tour_recepcion","place":"recepcion"}'` | Respuesta `{ "id": "..." }` con status 200 |
| 2 | Esperar máximo 15 segundos (polling del worker) | Robot ejecuta la secuencia configurada en `sequence_id` |
| 3 | Observar pantalla del robot | `MenuActivity` se abre mostrando opciones (café, agua, té) |
| 4 | Seleccionar un producto en la pantalla del robot | Mensaje llega al grupo de Telegram con el pedido |
| 5 | Verificar en Supabase Dashboard → tabla `robot_pedidos` | La fila tiene `realizado = true` |

### Verificación de idempotencia
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 6 | Repetir paso 1 con el mismo payload | Nueva fila insertada, robot la procesa independientemente |
| 7 | Intentar dos POSTs simultáneos | Solo uno es procesado por ciclo de polling (sin duplicados) |

**Estado**: ⬜ Pendiente

---

## TC-02 — Guías: Visita guiada completa

**Módulo**: `robot_guias`  
**Prioridad**: Alta  
**Precondición**: Existen waypoints `recepcion` y `sala_reunion` configurados en el robot

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Insertar fila en `robot_guias` desde Dashboard con `estado='programada'`, `waypoint_inicial='recepcion'`, `waypoint_final='sala_reunion'`, `hora_inicio=now()`, `duracion_horas=1` | Fila creada, `expires_at` calculado automáticamente por trigger |
| 2 | Llamar `POST /functions/v1/activar-guia` con `{ "id": "{id_de_la_fila}" }` | Respuesta con datos de la guía, fila pasa a `esperando_usuario` |
| 3 | Esperar polling del robot (30 s) | Robot navega a `waypoint_inicial`, muestra `GuiaActivity` en pantalla WAITING |
| 4 | Tocar el botón "Comenzar" en la pantalla del robot | Robot navega al `waypoint_final` |
| 5 | Robot llega al destino | TTS de llegada se reproduce, pantalla muestra estado final |
| 6 | Llamar `POST /functions/v1/finalizar-guia` con `{ "id": "...", "estado_final": "completada" }` | Fila pasa a `completada`, `finalizado_at` registrado |

### Prueba de CAS (concurrencia)
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 7 | Con dos filas `esperando_usuario`, llamar `GET /functions/v1/guia-pendiente` dos veces simultáneas | Solo una respuesta trae datos; la otra retorna `{ "pendiente": false }` |

### Prueba de sweep
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 8 | Insertar fila con `expires_at` en el pasado | Fila existe con estado `guiando` o `esperando_usuario` |
| 9 | Llamar `POST /functions/v1/robot-sweep-guias` | Fila pasa a `expirada` |

**Estado**: ⬜ Pendiente

---

## TC-03 — Evaluaciones: Rating de salón

**Módulo**: `robot_evaluaciones`  
**Prioridad**: Alta  
**Precondición**: Existe waypoint `sala_duarte` en el robot

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Llamar `POST /functions/v1/programar-evaluacion` con `{ "salon": "Sala Duarte", "waypoint": "sala_duarte", "hora_fin": "{ahora - 5min}", "hora_llegada": "{ahora + 30min}", "nombre_reserva": "Juan Pérez" }` | Fila creada con `estado='programada'`, respuesta `{ "id": "..." }` |
| 2 | Esperar polling del robot (30 s) | Robot detecta evaluación pendiente, navega a `sala_duarte` |
| 3 | Robot llega al waypoint | `RatingActivity` se abre con pantalla de estrellas |
| 4 | Seleccionar 5 estrellas en la pantalla | TTS: "Muchas gracias por tu evaluación" |
| 5 | Verificar fila en `robot_evaluaciones` | `estado='completada'`, `rating=5` |
| 6 | Verificar API externa `fojrqrkbzsgcefsnwldk` | Evaluación registrada en `create-evaluation` |

### Prueba de cancelación
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 7 | Con evaluación en `programada`, llamar `POST /functions/v1/programar-evaluacion` con `{ "id": "...", "estado": "cancelada" }` | Fila pasa a `cancelada`, robot no navega |

### Prueba de timeout
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 8 | No interactuar con `RatingActivity` durante 10 minutos | Robot vuelve a home base automáticamente, fila queda `timeout` |

**Estado**: ⬜ Pendiente

---

## TC-04 — Anuncios: Patrullaje de evento

**Módulo**: `robot_anuncios` + `AnnouncementManager`  
**Prioridad**: Alta  
**Precondición**: Al menos 3 waypoints configurados en el robot (ej: `recepcion`, `sala_a`, `sala_b`)

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Desde `http://localhost:8080/admin/events`, activar un evento con anuncio | `activar-anuncio` es llamado; fila en `robot_anuncios` con `estado='pendiente'` |
| 2 | Verificar en Supabase Dashboard | Fila creada con `texto`, `imagen_url`, `waypoints` correctos |
| 3 | Esperar polling del robot (30 s) | Robot detecta anuncio, entra en modo patrullaje |
| 4 | Observar robot en movimiento | Navega por los waypoints en loop |
| 5 | Observar pantalla del robot | `AnnouncementActivity` muestra la imagen/video del evento |
| 6 | Esperar 15 segundos en cada waypoint | TTS reproduce el texto del anuncio en cada parada |
| 7 | Esperar que expire `duracion_minutos` | Robot detiene patrullaje, vuelve a home base, fila pasa a `completado` |

### Prueba de exclusividad de modos
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 8 | Activar anuncio mientras hay una guía activa | Anuncio es ignorado (ExclusiveModeArbiter lo rechaza), log muestra warning |

**Estado**: ⬜ Pendiente

---

## TC-05 — Invitados: Flujo QR de check-in

**Módulo**: `robot_invitados` + `CheckinHandler`  
**Prioridad**: Alta  
**Precondición**: Existe un invitado registrado en spatium-hub con email válido

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Registrar invitado en `http://localhost:8080/admin/services/guests` | Sistema llama `send-guest-notification` |
| 2 | Verificar email del invitado | Email recibido con QR embebido (imagen PNG visible) |
| 3 | Inspeccionar QR del email | QR decodifica a `mytemi://guest?id={uuid}` |
| 4 | Mostrar el QR al lector del robot (cámara QR en `MainActivity`) | Robot lee el QR correctamente |
| 5 | Robot procesa el check-in | TTS: "Bienvenido/a {nombre}. Le hemos notificado a {contacto}" |
| 6 | `MenuActivity` se abre | Muestra opciones de café, agua, té |
| 7 | Verificar tabla `robot_invitados` | Fila con `status='bienvenido'`, `check_in_at` registrado |
| 8 | Verificar que el contacto fue notificado | Contacto recibió notificación push/email |

### Prueba de idempotencia (anti-duplicado)
| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 9 | Volver a mostrar el mismo QR al robot | Robot responde igual pero NO reenvía notificación al contacto |
| 10 | Verificar tabla `robot_invitados` | Misma fila, sin nueva entrada, `contact_notified_at` sin cambio |

**Estado**: ⬜ Pendiente

---

## TC-06 — Verificación de URL y conectividad

**Módulo**: `SupabaseGateway` / `BuildConfig`  
**Prioridad**: Media

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Abrir Supabase Dashboard → `fojrqrkbzsgcefsnwldk` → Table Editor | Tablas `robot_pedidos`, `robot_guias`, `robot_evaluaciones`, `robot_anuncios`, `robot_invitados` visibles |
| 2 | Abrir logcat con `adb logcat -s AnnouncementManager RatingManager GuiaManager RobotPedidosWorker` | Logs aparecen, ninguna URL hardcodeada con `mkakxmjkwcymwosfrwkl` visible |
| 3 | Verificar Edge Functions deployadas | `supabase functions list --project-ref fojrqrkbzsgcefsnwldk` muestra las 11 funciones `robot_*` |

**Estado**: ⬜ Pendiente

---

## TC-07 — Exclusividad de modos (Arbiter)

**Módulo**: `ExclusiveModeArbiter`  
**Prioridad**: Media  
**Precondición**: Robot en `MainActivity`

### Pasos

| # | Acción | Resultado esperado |
|---|--------|--------------------|
| 1 | Activar modo GUIA (TC-02 paso 2) | Robot entra en modo guía |
| 2 | Mientras está en modo GUIA, activar un ANUNCIO (TC-04 paso 1) | Anuncio rechazado, log muestra `"Skipping — another mode is active: GUIA"` |
| 3 | Finalizar la guía | Modo GUIA liberado |
| 4 | Activar el mismo anuncio nuevamente | Anuncio procesado correctamente |
| 5 | Repetir con combinación RATING + GUIA | Mismo comportamiento de exclusión |

**Estado**: ⬜ Pendiente

---

## Comandos útiles durante el QA

```bash
# Ver logs en tiempo real (filtrados por tags del proyecto)
adb -s 192.168.191.10:5555 logcat -s AnnouncementManager:D RatingManager:D GuiaManager:D CheckinHandler:D RobotPedidosWorker:D MainActivity:D

# Ver todas las tablas robot en Supabase (requiere service role key)
curl https://fojrqrkbzsgcefsnwldk.supabase.co/rest/v1/robot_pedidos \
  -H "apikey: {ANON_KEY}" \
  -H "Authorization: Bearer {ANON_KEY}"

# Limpiar todas las filas de prueba
# Ejecutar en Supabase Dashboard → SQL Editor:
# DELETE FROM robot_pedidos WHERE created_at < now() - interval '1 hour';
# DELETE FROM robot_anuncios WHERE created_at < now() - interval '1 hour';
# DELETE FROM robot_guias WHERE created_at < now() - interval '1 hour';
# DELETE FROM robot_evaluaciones WHERE created_at < now() - interval '1 hour';
# DELETE FROM robot_invitados WHERE created_at < now() - interval '1 hour';

# Reiniciar la app en el robot sin reinstalar
adb -s 192.168.191.10:5555 shell am force-stop com.spatium.deamon.db.temi
adb -s 192.168.191.10:5555 shell am start -n com.spatium.deamon.db.temi/.ui.MainActivity
```

---

## Resumen de casos

| ID | Módulo | Prioridad | Estado |
|----|--------|-----------|--------|
| TC-01 | Pedidos — cámara detecta persona | Alta | ⬜ Pendiente |
| TC-02 | Guías — visita guiada completa | Alta | ⬜ Pendiente |
| TC-03 | Evaluaciones — rating de salón | Alta | ⬜ Pendiente |
| TC-04 | Anuncios — patrullaje de evento | Alta | ⬜ Pendiente |
| TC-05 | Invitados — flujo QR check-in | Alta | ⬜ Pendiente |
| TC-06 | Verificación URL y conectividad | Media | ⬜ Pendiente |
| TC-07 | Exclusividad de modos (Arbiter) | Media | ⬜ Pendiente |
