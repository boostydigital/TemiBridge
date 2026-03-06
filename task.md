# Task: Renombrar aplicativo Android (TemiBridge)

## 1. Objetivo

Renombrar el aplicativo Android actual (internamente "TemiBridge" / `com.spatium.temibridge`) a un nuevo nombre, cubriendo:

- **Nombre visible** en la pantalla del Temi.
- **applicationId / package / namespace** de Android.
- **Paquetes Kotlin** (`package com.spatium.temibridge...`).
- **Acciones de Intent y ejemplos de integración**.
- **Scripts y workflows locales** que hacen referencia al package o nombre actual.

> Nota: Este documento solo define el plan. La ejecución debe hacerse en una rama independiente y validarse en al menos un robot Temi antes de desplegar a producción.

---

## 2. Contexto actual (inventario)

Rellenado a partir del análisis del repositorio:

- **Nombre de proyecto (Gradle)**
  - `settings.gradle.kts` → `rootProject.name = "TemiBridge"`

- **Identidad técnica principal**
  - `app/build.gradle.kts`:
    - `namespace = "com.spatium.temibridge"`
    - `applicationId = "com.spatium.temibridge"`
  - `app/src/main/AndroidManifest.xml`:
    - Atributo `package="com.spatium.temibridge"`.

- **Nombre visible (UI)**
  - `app/src/main/AndroidManifest.xml`:
    - `<application android:label="Temi Bridge" ... />`
  - `app/src/main/res/values/strings.xml`:
    - `app_name = "TemiBridge"`
    - `TemiBridge = "Temi Bridge"`

- **Paquetes Kotlin**
  - Código fuente bajo `app/src/main/java/com/spatium/temibridge/...`.
  - Ejemplos: `com.spatium.temibridge.ui.MainActivity`, `com.spatium.temibridge.ui.IntentEntryActivity`, `com.spatium.temibridge.core.TemiController`, etc.

- **Intents y acciones personalizadas**
  - En `AndroidManifest.xml` y `IntentEntryActivity`:
    - Acciones tipo `"com.spatium.temibridge.ACTION_GO_TO"`, `"com.spatium.temibridge.ACTION_SAY"`, etc.
  - Integraciones de ejemplo en `README.md` usan:
    - `setPackage("com.spatium.temibridge")`
    - `setClassName("com.spatium.temibridge", "com.spatium.temibridge.ui.IntentEntryActivity")`
    - URIs `intent://...;package=com.spatium.temibridge;...`.

- **Scripts / tooling local**
  - `install-to-temi.ps1`:
    - Variable `$PACKAGE = "com.spatium.temibridge"`.
    - Usa `$PACKAGE` para desinstalar e iniciar la app en Temi.
  - Workflow `.windsurf/workflows/compilar.md`:
    - Descripción: "Compilar APK debug de TemiBridge".
    - Ruta de ejemplo a la carpeta del proyecto haciendo referencia al nombre actual.

---

## 3. Decisiones previas (rellenar ANTES de tocar código)

Antes de ejecutar el renombrado, definir explícitamente:

- **D1. Nuevo nombre de app (marketing / visible)**
  - Valor decidido: `Deamon DB TEMI`
  - Dónde se verá: launcher del Temi, permisos, UI del dispositivo, etc.

- **D2. Nuevo `applicationId` / `namespace` / paquete base**
  - Valor decidido: `com.spatium.deamon.db.temi`
  - Al aplicar el checklist, usar este valor donde se indica `<NUEVO_APPLICATION_ID>`.
  - Este valor impacta:
    - `namespace` y `applicationId` en `app/build.gradle.kts`.
    - Atributo `package` de `AndroidManifest.xml`.
    - Estructura de paquetes Kotlin (`package ...`).

- **D3. Estrategia de compatibilidad para acciones de Intent**
  - Acciones actuales están "namespaced" con el package actual (ej. `com.spatium.temibridge.ACTION_GO_TO`).
  - Opciones:
    - **Opción A (conservadora)**: Mantener los mismos nombres de acción aunque cambie el `applicationId`, para no romper integraciones existentes.
    - **Opción B (breaking)**: Renombrar acciones al nuevo espacio (`<NUEVO_APPLICATION_ID>.ACTION_*`) y coordinar actualización en todos los clientes.
    - **Opción C (transición)**: Aceptar temporalmente ambas variantes (antiguas y nuevas) en `IntentEntryActivity`, con plan de retirada.

- **D4. Alcance del renombrado del proyecto**
  - ¿También se va a renombrar el **nombre del proyecto** (`rootProject.name`) y/o la **carpeta física** del repositorio?
  - Placeholder: `<NUEVO_NOMBRE_PROYECTO>` (si aplica).

Documentar estas decisiones al inicio del `task.md` (puede añadirse una sección "Decisiones tomadas" una vez acordadas).

---

## 4. Checklist técnico (código Android)

### 4.1. Preparación

- [ ] Crear una **rama nueva** para el cambio de nombre (ej.: `feature/rename-app`).
- [ ] Confirmar que el proyecto compila y corre en Temi **antes** del cambio (baseline).
- [ ] Hacer backup / tag ligero en Git del estado actual (por si hay que volver atrás).

### 4.2. Configuración Gradle y Manifest

- [ ] Editar `settings.gradle.kts` (opcional):
  - Si se decide cambiar el nombre del proyecto:
    - [ ] Cambiar `rootProject.name` de `"TemiBridge"` a `<NUEVO_NOMBRE_PROYECTO>`.

- [ ] Editar `app/build.gradle.kts`:
  - [ ] Cambiar `namespace` de `"com.spatium.temibridge"` a `<NUEVO_APPLICATION_ID>`.
  - [ ] Cambiar `applicationId` de `"com.spatium.temibridge"` a `<NUEVO_APPLICATION_ID>`.
  - [ ] Revisar que no haya otras referencias literales al package anterior.

- [ ] Editar `app/src/main/AndroidManifest.xml`:
  - [ ] Cambiar `package="com.spatium.temibridge"` a `package="<NUEVO_APPLICATION_ID>"`.
  - [ ] Revisar `<application android:label="Temi Bridge" ...>`:
    - Opción 1: Cambiar directamente el valor a `<NUEVO_NOMBRE_VISIBLE>`.
    - Opción 2 (recomendada): usar `android:label="@string/app_name"` y gestionar el texto desde `strings.xml`.

### 4.3. Recursos de texto (strings)

- [ ] Editar `app/src/main/res/values/strings.xml`:
  - [ ] Cambiar el valor de `app_name` de `TemiBridge` a `<NUEVO_NOMBRE_VISIBLE>`.
  - [ ] Revisar el string con nombre `TemiBridge` (valor `Temi Bridge`):
    - Decidir si se mantiene solo como recurso histórico o si también se renombra/elimina.

### 4.4. Paquetes Kotlin (refactor de código)

> Recomendado usar el **Refactor > Rename** de Android Studio sobre el paquete, en lugar de buscar y reemplazar manualmente.

- [ ] En el árbol `app/src/main/java`, seleccionar el paquete raíz `com.spatium.temibridge` y:
  - [ ] Aplicar refactor para renombrarlo a `<NUEVO_APPLICATION_ID>` (Android Studio creará/moverá las carpetas necesarias).
  - [ ] Confirmar que todos los archivos `.kt` actualizan automáticamente su línea `package ...`.

- [ ] Revisar referencias internas:
  - [ ] `import com.spatium.temibridge...` → deben pasar a `import <NUEVO_APPLICATION_ID>...`.
  - [ ] Clases en `ui/`, `core/`, etc. compilan sin errores de paquete.

- [ ] Tests (si existen):
  - [ ] Actualizar paquetes y referencias en `app/src/androidTest` y `app/src/test`.

### 4.5. Acciones de Intent y deep links

- **Deep links `mytemi://`**
  - El esquema `mytemi` no depende del `applicationId`, por lo que **no es obligatorio cambiarlo**.
  - [ ] Verificar que `AndroidManifest.xml` mantiene el `intent-filter` de `VIEW` con `scheme="mytemi"` sin cambios funcionales.

- **Acciones de Intent explícitas**
  - [ ] Revisar en `AndroidManifest.xml` todas las acciones `action android:name="com.spatium.temibridge.ACTION_*"`.
  - [ ] Decidir según D3:
    - Si se mantienen, no hay cambios aquí.
    - Si se renombran, actualizar a `<NUEVO_APPLICATION_ID>.ACTION_*`.

- **Manejo de acciones en código (`IntentEntryActivity`)**
  - [ ] Revisar el `when (i.action)` de `IntentEntryActivity`.
  - [ ] Si se opta por la **Opción C (transición)**, plan propuesto:
    - [ ] Aceptar tanto las acciones antiguas como las nuevas (ej. múltiples `when`/`when` con alias).
    - [ ] Documentar en comentarios el periodo de transición y la fecha objetivo para eliminar las antiguas.

> Importante: cualquier cambio en los nombres de acción rompe integraciones existentes si no se actualizan. Coordinar con todos los consumidores (web, otras apps, automatizaciones, etc.).

---

## 5. Checklist de documentación y tooling

### 5.1. README y documentación

- [ ] Actualizar `README.md`:
  - [ ] Título `# Temi Bridge (com.spatium.temibridge)` → reflejar `<NUEVO_NOMBRE_VISIBLE> (<NUEVO_APPLICATION_ID>)`.
  - [ ] Sección "Package" y ejemplos de uso:
    - [ ] `package=com.spatium.temibridge` → `package=<NUEVO_APPLICATION_ID>`.
    - [ ] Fragmentos de código con `setPackage("com.spatium.temibridge")` → `setPackage("<NUEVO_APPLICATION_ID>")`.
    - [ ] Fragmentos de código con `setClassName("com.spatium.temibridge", "com.spatium.temibridge.ui.IntentEntryActivity")` → actualizar a `<NUEVO_APPLICATION_ID>`.
  - [ ] Ejemplos ADB:
    - [ ] `adb shell am start -n com.spatium.temibridge/.ui.IntentEntryActivity ...` → actualizar a `<NUEVO_APPLICATION_ID>/.ui.IntentEntryActivity`.

### 5.2. Scripts locales

- [ ] Editar `install-to-temi.ps1`:
  - [ ] Cambiar `$PACKAGE = "com.spatium.temibridge"` a `$PACKAGE = "<NUEVO_APPLICATION_ID>"`.
  - [ ] Revisar mensajes en consola que mencionan el nombre de la app y ajustarlos a `<NUEVO_NOMBRE_VISIBLE>` si aplica.

### 5.3. Workflows y otras herramientas

- [ ] Actualizar `.windsurf/workflows/compilar.md`:
  - [ ] Descripción `Compilar APK debug de TemiBridge` → usar el nuevo nombre.
  - [ ] Rutas de ejemplo que hagan referencia a `TemiBridge` → adaptar si también se renombra la carpeta del proyecto.

- [ ] Buscar otras referencias a `TemiBridge` o `com.spatium.temibridge` en archivos `.md`, `.ps1`, `.sh`, `.bat`, etc., y actualizar según corresponda.

---

## 6. Consideraciones de despliegue en Temi

- [ ] Confirmar la estrategia con respecto a instalaciones existentes en robots Temi:
  - Si se cambia el `applicationId`, el sistema considerará la nueva app como **distinta** a la anterior.
  - Opciones:
    - [ ] Desinstalar manualmente la app antigua (`com.spatium.temibridge`) y luego instalar la nueva.
    - [ ] Mantener ambas temporalmente solo si hay una razón clara (no recomendado a largo plazo).

- [ ] Actualizar cualquier documentación interna de **instalación** y **actualización** que haga referencia al package antiguo:
  - Ejemplos de `adb install`, `adb uninstall`, etc.

---

## 7. Plan de pruebas después del renombrado

- [ ] Compilación local:
  - [ ] Ejecutar `./gradlew assembleDebug` y asegurar que el proyecto compila sin errores.

- [ ] Instalación en un robot Temi de pruebas:
  - [ ] Usar el script `install-to-temi.ps1` ya actualizado o comandos ADB equivalentes.
  - [ ] Verificar que la app aparece con `<NUEVO_NOMBRE_VISIBLE>` en la lista de apps/permisos.

- [ ] Pruebas funcionales mínimas:
  - [ ] Lanzar la app desde el launcher del Temi y verificar que no hay crashes.
  - [ ] Probar un deep link `mytemi://go?...` desde ADB / navegador y confirmar que `IntentEntryActivity` responde.
  - [ ] Probar al menos una acción de cada tipo (go, say, tour, sequence, escort) usando el nuevo package en Intents si aplica.

- [ ] Verificación de integraciones externas:
  - [ ] Actualizar y probar cualquier sistema que construya URIs `intent://...;package=com.spatium.temibridge;...`.
  - [ ] Actualizar automatizaciones que usen acciones explícitas (`com.spatium.temibridge.ACTION_*`) si se optó por renombrarlas.

---

## 8. Checklist de cierre

- [ ] Código y documentación actualizados al nuevo nombre / package.
- [ ] Pruebas manuales en al menos un robot Temi completadas sin errores críticos.
- [ ] Rama mergeada a la rama principal según flujo de trabajo del proyecto.
- [ ] Plan de comunicación: equipos que consumen el bridge informados del cambio de package y, si aplica, de los nuevos nombres de acción.

> Este `task.md` debe mantenerse actualizado si se añaden nuevas rutas de integración o tooling que dependan del nombre del aplicativo.

---

## 9. Procesamiento en background de pedidos (Supabase `public.robot_pedidos`)

### 9.1 Objetivo y contrato

- [ ] Definir claramente el comportamiento deseado:
  - La app Deamon DB TEMI debe procesar de forma continua los registros de `public.robot_pedidos` donde `realizado = false`.
  - Para cada registro pendiente, se debe:
    - Leer el valor de `secuencia`.
    - Disparar la ejecución lógica asociada a esa secuencia en la app.
    - Marcar el registro como `realizado = true` en la base de datos (solo si la app acepta procesarlo).
    - Ejecutar efectivamente la secuencia en el robot Temi usando `TemiController`.
  - Garantizar que cada registro se procese **una sola vez** (idempotencia) incluso si hay reconexiones o varios robots.

### 9.2 Verificación inicial con MCP Supabase (solo lectura)

- [ ] **Se requieren consultas MCP a la DB; propongo ejecutar:**
  - Verificar esquema de la tabla:
    ```sql
    SELECT column_name, data_type, is_nullable, column_default
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'robot_pedidos';
    ```
  - Verificar índices existentes:
    ```sql
    SELECT indexname, indexdef
    FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'robot_pedidos';
    ```
  - Verificar si la tabla tiene RLS activa:
    ```sql
    SELECT c.relname, c.relrowsecurity
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'robot_pedidos';
    ```
- [ ] Documentar resultados (especialmente si existe o no índice por `realizado`/`created_at` y si RLS está activa).

### 9.3 Decisiones de arquitectura

- [ ] Confirmar patrón de ejecución:
  - La app en Temi actuará como **worker cliente** leyendo Supabase.
  - El procesamiento se considera "siempre activo" mientras la app esté en primer plano o en modo kiosk en el robot.
- [ ] Decidir cómo se mantiene vivo el worker:
  - Opción A: `LifecycleService` en primer plano con notificación mínima (foreground service) mientras el robot esté en modo kiosko.
  - Opción B: Worker atado al ciclo de vida de `MainActivity`/`KioskWebActivity` si la app está siempre visible.
- [ ] Definir API interna del worker:
  - `start()` / `stop()`.
  - `enqueueFromDbRow(id: Long, secuencia: String)`.
  - Manejo de reintentos y logging.

### 9.4 Ajustes de base de datos (opcional, rendimiento y seguridad)

- [ ] **APROBADO_PARA_CAMBIO** (si se valida necesario) crear un índice para acelerar los filtros por pendiente:
  - SQL sugerido:
    ```sql
    CREATE INDEX CONCURRENTLY IF NOT EXISTS robot_pedidos_realizado_created_at_idx
    ON public.robot_pedidos (realizado, created_at DESC);
    ```
  - Rollback documentado:
    ```sql
    DROP INDEX IF EXISTS robot_pedidos_realizado_created_at_idx;
    ```
- [ ] Evaluar añadir columna `robot_id` (o similar) si debe haber segmentación por robot:
  - **APROBADO_PARA_CAMBIO** solo si se decide implementarlo.
- [ ] Revisar/definir políticas RLS para `robot_pedidos`:
  - Permitir que la app (usando clave `anon`) solo vea y modifique sus propios pedidos (por robot) si aplica.
  - Mantener el uso de `service_role` exclusivamente en backend seguro / Edge Functions.

### 9.5 SDK Supabase y configuración en Android

- [ ] Elegir SDK para Android (Kotlin):
  - Revisar la documentación de **Supabase Kotlin / supabase-kt** usando MCP Supabase.
  - Confirmar soporte para PostgREST, Realtime y Auth en Android.
- [ ] Añadir dependencia del cliente Supabase en `app/build.gradle.kts` según la guía oficial (sin hardcodear versiones aquí; seguir docs).
- [ ] Definir configuración centralizada en la app:
  - `SUPABASE_URL` (https://<project>.supabase.co).
  - `SUPABASE_ANON_KEY` (clave anon/publishable, **no** `service_role`).
  - Exponerlos vía `BuildConfig` o un wrapper seguro, nunca hardcodeados en código fuente plano.
- [ ] Implementar un `SupabaseClientProvider` singleton que inicialice el cliente una sola vez y se reutilice en toda la app.

### 9.6 Diseño del worker en la app Deamon DB TEMI

- [ ] Crear un componente de dominio, por ejemplo `RobotPedidosWorker` (objeto o servicio con lifecycle propio), responsable de:
  - Mantener una **cola en memoria** de pedidos pendientes (`id`, `secuencia`).
  - Gestionar la conexión a Supabase (PostgREST + Realtime).
  - Orquestar la ejecución secuencial de cada pedido.
- [ ] Decidir cómo se mantiene vivo el worker:
  - Opción A: `LifecycleService` en primer plano con notificación mínima (foreground service) mientras el robot esté en modo kiosko.
  - Opción B: Worker atado al ciclo de vida de `MainActivity`/`KioskWebActivity` si la app está siempre visible.
- [ ] Definir API interna del worker:
  - `start()` / `stop()`.
  - `enqueueFromDbRow(id: Long, secuencia: String)`.
  - Manejo de reintentos y logging.

### 9.7 Obtención de pedidos pendientes desde Supabase

- [ ] Implementar en el worker un método de sincronización inicial al arrancar la app:
  - Consulta (lectura) a Supabase para traer registros pendientes más recientes, por ejemplo:
    - `SELECT id, secuencia, realizado FROM public.robot_pedidos WHERE realizado = false ORDER BY created_at ASC LIMIT <N>`.
  - Agregar cada registro pendiente a la cola interna.
- [ ] Suscribirse en tiempo real a cambios en `robot_pedidos` usando Supabase Realtime (patrón recomendado en las reglas globales):
  - Canal: `supabase.channel("realtime:robot_pedidos")`.
  - Filtro `postgres_changes` con `schema='public'`, `table='robot_pedidos'`, `event='INSERT'` (y opcionalmente `UPDATE`).
  - Handler `applyChange(prev, payload)` que, ante un nuevo registro con `realizado = false`, lo encola en `RobotPedidosWorker`.
- [ ] Implementar backoff y reconexión al canal Realtime, registrando eventos de reconexión/errores.

### 9.8 Proceso para cada pedido (algoritmo por registro)

- [ ] Definir el flujo para cada item en cola (pseudocódigo conceptual):
  1. Tomar el siguiente pedido (menor `created_at` o menor `id`) desde la cola interna.
  2. Validar que `secuencia` no sea nula/vacía; si lo es, registrar error y marcar el pedido como descartado (según decisión de negocio).
  3. Intentar **reclamar** el pedido en Supabase para evitar dobles ejecuciones:
     - Hacer un `UPDATE public.robot_pedidos SET realizado = true WHERE id = :id AND realizado = false`.
     - Si la cantidad de filas afectadas es 0, otro worker/robot ya lo tomó → no ejecutar en este robot.
  4. Si el `UPDATE` devolvió 1 fila (pedido reclamado correctamente):
     - Invocar a `TemiController` para ejecutar la secuencia indicada por `secuencia`:
       - Decidir si `secuencia` es nombre o ID y usar `playSequenceByName` o `playSequenceById`.
     - Registrar logs de éxito o fallo de la secuencia.
  5. Manejar errores de red o de Supabase con reintentos limitados y logs.

### 9.9 Manejo de errores y reintentos

- [ ] Definir política de reintentos por pedido (por ejemplo, hasta N intentos antes de marcarlo como fallido en una columna adicional opcional).
- [ ] Registrar en logs:
  - Errores de conexión a Supabase.
  - Errores al actualizar `realizado`.
  - Errores al ejecutar la secuencia en Temi.
- [ ] Considerar añadir columnas opcionales en la tabla (con **APROBADO_PARA_CAMBIO**):
  - `intentos` (int), `ultimo_error` (text), `procesado_por` (text/uuid para identificar el robot).

### 9.10 Pruebas end-to-end

- [ ] Pruebas en entorno de desarrollo (Supabase + emulador/simulación o robot de pruebas):
  - Insertar manualmente registros en `public.robot_pedidos` con `realizado = false` y distintos valores de `secuencia`.
  - Verificar que la app los detecta (por sincronización inicial o Realtime) y los procesa en orden esperado.
  - Confirmar que, después de procesar, el campo `realizado` pasa a `true`.
  - Confirmar que el robot ejecuta la secuencia correspondiente.
- [ ] Pruebas de desconexión:
  - Simular pérdida de red y reconexión; validar que los pedidos pendientes no se pierden y no se ejecutan duplicados.
- [ ] Pruebas de RLS y seguridad:
  - Verificar que, usando la clave `anon`, la app solo puede leer/actualizar lo permitido por las políticas.
  - Validar que la clave `service_role` nunca se use ni se exponga en la app (solo en backend/Edge Functions, si se llega a necesitar).
