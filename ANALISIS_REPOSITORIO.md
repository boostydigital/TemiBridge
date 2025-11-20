# Análisis del Repositorio TemiBridge

## 📋 Resumen Ejecutivo

**TemiBridge** es una aplicación Android que actúa como puente de comunicación para controlar robots Temi mediante Intents, Deep Links y códigos QR. Desarrollada para Spatium Group, permite la integración entre aplicaciones web/nativas y el robot Temi.

---

## 🏗️ Arquitectura del Proyecto

### Tipo de Proyecto
- **Plataforma**: Android (Kotlin)
- **Build System**: Gradle (Kotlin DSL)
- **SDK Mínimo**: API 26 (Android 8.0)
- **SDK Target**: API 36
- **Package**: `com.spatium.temibridge`

### Estructura de Directorios

```
TemiBridge/
├── app/
│   ├── src/main/
│   │   ├── java/com/spatium/temibridge/
│   │   │   ├── core/
│   │   │   │   └── TemiController.kt          # Controlador principal del SDK Temi
│   │   │   └── ui/
│   │   │       ├── IntentEntryActivity.kt     # Activity receptor de intents/deep links
│   │   │       ├── MainActivity.kt            # Activity principal con scanner QR
│   │   │       ├── KioskWebActivity.kt        # WebView en modo kiosko
│   │   │       └── SplashActivity.kt          # Pantalla de inicio
│   │   ├── res/                               # Recursos (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml                # Configuración de la app
│   └── build.gradle.kts                       # Configuración de build del módulo
├── build.gradle.kts                           # Configuración de build raíz
├── gradle/libs.versions.toml                  # Catálogo de versiones
└── README.md                                  # Documentación del proyecto
```

---

## 🎯 Funcionalidades Core

### 1. Control del Robot Temi
El sistema permite controlar el robot mediante:

#### Deep Links (`mytemi://`)
- **`mytemi://go?place=<ubicacion>`** - Navegar a un waypoint
- **`mytemi://say?text=<mensaje>`** - Hacer que el robot hable
- **`mytemi://welcome?text=<saludo>&place=<ubicacion>`** - Saludo + navegación combinada
- **`mytemi://tour?name=<nombre_tour>`** - Iniciar un tour
- **`mytemi://sequence?name=<nombre_secuencia>`** - Ejecutar secuencia
- **`mytemi://sequence-control?action=<next|pause|play|stop>`** - Controlar secuencia
- **`mytemi://sequence-list`** - Listar secuencias disponibles
- **`mytemi://sequence-permission`** - Solicitar permiso de secuencias

#### Intent Actions
- `com.spatium.temibridge.ACTION_GO_TO`
- `com.spatium.temibridge.ACTION_SAY`
- `com.spatium.temibridge.ACTION_FOLLOW_ME`
- `com.spatium.temibridge.ACTION_STOP`
- `com.spatium.temibridge.ACTION_HEAD_TILT`
- `com.spatium.temibridge.ACTION_VOLUME`
- `com.spatium.temibridge.ACTION_TOUR_START`

### 2. Scanner QR
- Integración con ZXing para escaneo de códigos QR
- Decodificación robusta de parámetros URL (hasta 3 pasadas)
- Soporte para deep links `mytemi://` embebidos en QR

### 3. Modo Kiosko Web
- WebView fullscreen con auto-cierre (2 minutos)
- Integración con sistema de pedidos: `https://spatium-desk.lovable.app/pedidos-publicos`
- Botón de retroceso visual
- Mantiene pantalla encendida

### 4. Flujo de Recepción
- Webhook a Make.com para notificaciones
- Apertura automática de KioskWebActivity cuando `recepcion=true`
- Retorno automático a "entrada" después de completar navegación

---

## 🔧 Componentes Técnicos

### TemiController.kt (468 líneas)
**Responsabilidad**: Abstracción del SDK de Temi usando reflexión

**Métodos Principales**:
- `speak(text: String)` - TTS (Text-to-Speech)
- `goTo(place: String)` - Navegación a waypoints
- `setArrivalCallbackOnce(callback)` - Callback al llegar a destino
- `playTourByName/ById(identifier)` - Control de tours
- `playSequenceByName/ById(identifier)` - Control de secuencias
- `controlSequence(action)` - Control de reproducción de secuencias
- `requestSequencePermission(activity?)` - Gestión de permisos
- `getCurrentPose()` - Obtener posición actual
- `getSavedLocations()` - Listar waypoints guardados

**Características**:
- Uso de reflexión para compatibilidad con diferentes versiones del SDK
- Listeners dinámicos para eventos de navegación
- Manejo robusto de errores

### IntentEntryActivity.kt (253 líneas)
**Responsabilidad**: Activity "headless" que procesa intents y deep links

**Flujos**:
1. **Deep Links**: Parseo de URIs `mytemi://`
2. **Intent Actions**: Procesamiento de acciones explícitas
3. **Navegación con callback**: Retorno automático a "entrada" después de 10s
4. **Modo recepción**: Apertura de KioskWebActivity tras 5s

**Características**:
- `launchMode="singleTop"` para evitar múltiples instancias
- Tema transparente para UX fluida
- Decodificación robusta de parámetros

### MainActivity.kt (307 líneas)
**Responsabilidad**: Interfaz principal con scanner QR

**Características UI**:
- Fondo con imagen de Coil + efecto Ken Burns
- Logo de Spatium con animación de entrada
- Tarjetas animadas (fade + slide + scale)
- Botones: "Escanear QR" y "Iniciar Tour"

**Flujos**:
1. Scanner QR → Parseo deep link → Ejecución de acción
2. Webhook a Make.com con datos de recepción/teléfono
3. Apertura condicional de KioskWebActivity

### KioskWebActivity.kt (109 líneas)
**Responsabilidad**: WebView en modo kiosko

**Características**:
- Fullscreen inmersivo
- JavaScript habilitado
- Auto-cierre tras 2 minutos
- Botón visual de retroceso
- Navegación interna del WebView

---

## 📦 Dependencias

### SDK y Frameworks
- **Temi SDK**: 1.136.0 (control del robot)
- **AndroidX Core**: 1.13.1
- **Material Design**: 1.12.0
- **ConstraintLayout**: 2.1.4

### Librerías Externas
- **ZXing Embedded**: 4.3.0 (scanner QR)
- **Coil**: 2.6.0 (carga de imágenes)
- **OkHttp**: 4.12.0 (cliente HTTP para webhooks)
- **Coroutines**: 1.8.1 (trabajo asíncrono)

### Build Tools
- **AGP**: 8.11.2
- **Kotlin**: 2.0.21
- **Java**: 17

---

## 🔐 Permisos y Configuración

### Permisos Android
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="com.robotemi.sdk.permission.SEQUENCE" />
```

### Metadata Temi
```xml
<meta-data android:name="com.robotemi.sdk.metadata.SKILL" android:value="Temi Bridge" />
<meta-data android:name="com.robotemi.sdk.metadata.PERMISSIONS" android:value="com.robotemi.permission.sequence" />
<meta-data android:name="com.robotemi.sdk.metadata.KIOSK" android:value="TRUE" />
```

---

## 🔄 Flujos de Usuario

### Flujo 1: Escaneo QR Estándar
```
Usuario → Botón "Escanear QR" → Scanner ZXing → 
Parseo deep link → TemiController ejecuta acción → 
Webhook Make.com → [Opcional] KioskWebActivity
```

### Flujo 2: Navegación con Retorno
```
QR con recepcion=false → goTo(place) → 
Callback al llegar → Anuncio TTS → 
Espera 10s → goTo("entrada")
```

### Flujo 3: Modo Recepción
```
QR con recepcion=true → goTo(place) → 
Webhook Make.com → Espera 5s → 
KioskWebActivity (pedidos) → Auto-cierre 2min
```

### Flujo 4: Deep Link Externo
```
App/Web externa → Intent URI → IntentEntryActivity → 
Parseo acción → TemiController → goHome()
```

---

## 🎨 Diseño UI/UX

### Principios Aplicados
- **Minimalismo**: Interfaz limpia con 2 acciones principales
- **Feedback Visual**: Animaciones suaves (fade, scale, slide)
- **Accesibilidad**: Botones grandes y claros
- **Branding**: Logo de Spatium + imagen de fondo corporativa

### Animaciones
- **Ken Burns**: Zoom sutil en imagen de fondo (14s loop)
- **Card Entry**: Fade + slide + scale con delays escalonados
- **Logo**: Fade + scale en entrada (650ms)

### Temas
- **MainActivity**: Tema estándar con fondo oscuro
- **IntentEntryActivity**: Tema transparente (headless)
- **KioskWebActivity**: Fullscreen inmersivo

---

## 🔗 Integraciones Externas

### 1. Webhook Make.com
**URL**: `https://hook.us1.make.com/rpr19yvr51pufln58pwln4rdgz0dl6hq`

**Payload**:
```json
{
  "recepcion": true/false,
  "telefono": "opcional"
}
```

### 2. Sistema de Pedidos
**URL**: `https://spatium-desk.lovable.app/pedidos-publicos?ubicacion=Recepcion`

**Uso**: WebView en modo kiosko para pedidos en recepción

---

## 📊 Entidades y Relaciones

### Entidad: Robot Temi
- **Atributos**: posición (x, y), waypoints, tours, secuencias
- **Operaciones**: goTo, speak, playTour, playSequence

### Entidad: Waypoint
- **Atributos**: nombre, x, y
- **Relaciones**: N waypoints → 1 Robot

### Entidad: Tour
- **Atributos**: id, nombre
- **Relaciones**: N tours → 1 Robot

### Entidad: Secuencia
- **Atributos**: id, nombre
- **Relaciones**: N secuencias → 1 Robot
- **Permisos**: Requiere `SEQUENCE` permission

### Entidad: QR Code
- **Atributos**: deep link URI, parámetros (text, place, recepcion, telefono)
- **Relaciones**: 1 QR → 1 Acción del Robot

### Entidad: WebHook Event
- **Atributos**: recepcion (boolean), telefono (string)
- **Relaciones**: 1 Escaneo QR → 0..1 Webhook

---

## 🐛 Puntos de Mejora Identificados

### Código
1. **Duplicación**: Lógica de decodificación de parámetros repetida en MainActivity e IntentEntryActivity
2. **Hardcoding**: URLs de webhook y sistema de pedidos hardcodeadas
3. **Manejo de errores**: Algunos try-catch silenciosos sin logging adecuado
4. **Testing**: No hay tests unitarios ni de integración

### Arquitectura
1. **Separación de responsabilidades**: MainActivity tiene lógica de negocio mezclada con UI
2. **Configuración**: Falta archivo de configuración para URLs y parámetros
3. **Inyección de dependencias**: No se usa DI (Hilt/Koin)

### UX
1. **Feedback**: No hay indicadores de carga durante navegación del robot
2. **Errores**: Mensajes de error genéricos
3. **Accesibilidad**: Falta soporte para TalkBack

---

## 🚀 Recomendaciones

### Corto Plazo
1. Extraer URLs a `BuildConfig` o archivo de configuración
2. Crear función utilitaria compartida para decodificación de parámetros
3. Añadir logging estructurado (Timber)
4. Documentar formato de QR codes en README

### Mediano Plazo
1. Implementar ViewModel + LiveData para MainActivity
2. Crear repositorio para gestión de estado del robot
3. Añadir tests unitarios para TemiController
4. Implementar manejo de errores centralizado

### Largo Plazo
1. Migrar a Jetpack Compose para UI
2. Implementar sistema de configuración remota (Firebase Remote Config)
3. Añadir analytics y crash reporting
4. Crear dashboard web para gestión de QR codes

---

## 📝 Notas Técnicas

### Reflexión en TemiController
El uso de reflexión permite compatibilidad con diferentes versiones del SDK de Temi sin recompilar. Esto es crucial porque:
- El SDK de Temi puede variar entre robots
- Evita crashes por métodos no disponibles
- Permite fallbacks graceful

### Decodificación Múltiple
La función `decodeParam` realiza hasta 3 pasadas de decodificación URL porque:
- Algunos generadores de QR codifican múltiples veces
- Garantiza compatibilidad con diferentes fuentes
- Previene errores de parseo

### LaunchMode SingleTop
`IntentEntryActivity` usa `singleTop` para:
- Evitar múltiples instancias en el back stack
- Procesar múltiples intents sin crear activities
- Mantener limpia la navegación

---

## 🔍 Archivos Temporales Detectados

Los siguientes archivos temporales están en el repositorio:
- `temp_IntentEntry.txt` (12.5 KB)
- `temp_Main.txt` (14.8 KB)
- `temp_TemiController_full.txt` (20.3 KB)
- `temp_permission.md` (7.2 KB)

**Recomendación**: Añadir `temp_*.txt` y `temp_*.md` al `.gitignore`

---

## 📈 Métricas del Proyecto

- **Total de Activities**: 4
- **Total de clases Kotlin**: 5
- **Líneas de código (aprox.)**: ~1,150
- **Dependencias externas**: 7
- **Permisos Android**: 3
- **Deep link schemes**: 1 (`mytemi://`)
- **Intent actions**: 7

---

## 🎯 Casos de Uso Principales

### CU-01: Escanear QR para Navegación
**Actor**: Usuario visitante  
**Flujo**: Escanea QR → Robot saluda → Navega a destino → Retorna a entrada  
**Precondiciones**: Waypoint existe en el robot  

### CU-02: Iniciar Tour Guiado
**Actor**: Usuario visitante  
**Flujo**: Escanea QR de tour → Robot inicia tour predefinido  
**Precondiciones**: Tour configurado en Temi Center  

### CU-03: Modo Recepción
**Actor**: Recepcionista  
**Flujo**: Escanea QR recepción → Robot navega → Abre sistema de pedidos  
**Precondiciones**: Conexión a internet activa  

### CU-04: Control Remoto vía Web
**Actor**: Aplicación web  
**Flujo**: Genera deep link → Usuario hace clic → Robot ejecuta acción  
**Precondiciones**: TemiBridge instalado en el robot  

---

## 📚 Documentación Existente

- **README.md**: Completo y bien estructurado
  - Instalación vía ADB
  - Ejemplos de deep links
  - Pruebas con ADB
  - Solución de problemas
  - Integración web

---

## ✅ Conclusión

TemiBridge es una aplicación bien diseñada que cumple su propósito como puente de comunicación para robots Temi. La arquitectura es sólida, aunque hay oportunidades de mejora en:

1. **Modularización**: Separar lógica de negocio de UI
2. **Configuración**: Externalizar parámetros hardcodeados
3. **Testing**: Añadir cobertura de tests
4. **Documentación**: Crear diagramas de flujo y arquitectura

El código es mantenible y sigue buenas prácticas de Kotlin/Android, con un enfoque pragmático en la compatibilidad mediante reflexión.
