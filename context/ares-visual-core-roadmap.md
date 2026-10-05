# Ares Visual Core — Roadmap de Diseño

**Última actualización: 1 de octubre de 2026**

Este documento consolida el estado actual del **Visual Core de Ares**, las decisiones arquitectónicas ya tomadas, los avances cerrados y las integraciones reales que ya forman parte del flujo productivo.

La fase visual principal está estabilizada. También están cerrados Telemetry, Runtime, Core audiovisual y el lifecycle visual completo de Ares (`IDLE / LISTENING / PROCESSING / INTERACTING / EXECUTING / SPEAKING / DEGRADED`).

Desde la última revisión se cerraron dos bloques importantes:

1. **TOOLS Workspace real**: catálogo JSON, top 5 táctil, `More Tools` paginado e integración con el OS Skill existente.
2. **MEDIA Workspace real v1**: sesión multimedia Windows, artwork/metadata/transport, dispositivo de salida, calidad efectiva de TIDAL y `QUEUE // NEXT`.

El foco de la siguiente etapa deja de ser “convertir mocks a reales” de forma general y pasa a completar las capacidades restantes de cada workspace, endurecer selección de providers/sesiones y cerrar los pendientes específicos.

---

# 1. Estado general

| Área / componente | Estado | Ya implementado / validado | Pendiente / por mejorar | Integración futura |
|---|---|---|---|---|
| **Layout general 1080p** | ✅ Estable | Header + panel izquierdo + `AresCoreView` central + `AresWorkspaceView` derecha + footer. Geometría del shell estabilizada: body flexible y Header/Footer invariantes entre workspaces. | Validación final al cerrar la fase visual. Ajustar proporciones sólo si una integración real lo exige. | Fullscreen real, bounds completos y multi-monitor. |
| **HudBackground** | ✅ Estable | Grid, cruces técnicas, esquinas, círculos ambientales y elementos HUD tenues. | Revisión final de densidad visual. No añadir decoración sin una función concreta. | Posible reacción sutil a estados globales. |
| **Header v2** | ✅ Productivo en estados | `ARES // SYSTEM`, `VISUAL CORE`, estado global semántico real (`STATE // ...`), fecha/hora separadas y pseudo-clases para los siete estados de Ares. | Mantener geometría estable; sólo añadir metadata si existe una necesidad operacional concreta. | Ya conectado al lifecycle real del pipeline/coordinadores. |
| **Footer v2** | ✅ Productivo en runtime/input | Rail de tres zonas: `ARES // READY`, resumen real Phi/Qwen y modalidad de entrada. Incluye botón `MUTE` que conmuta `VOICE + TOUCH ↔ TOUCH ONLY` sin silenciar TTS. | Teclado táctil dinámico pendiente para `TOUCH ONLY`; mantener altura/composición. | `RuntimeStatusCoordinator` + `VoiceInputController`. |
| **AresCoreView v2** | ✅ Lifecycle visual completo | Core central reactivo con `IDLE/LISTENING/PROCESSING/INTERACTING/EXECUTING/SPEAKING/DEGRADED` reales. `LISTENING` usa PCM del micrófono; `SPEAKING`, PCM real del TTS; RMS/peak/VAD modulan waveform/halo/pulse con smoothing. | Mantener geometría estable; calibrar thresholds sólo después de uso prolongado. | Audio/VAD/TTS + Interaction/Execution lifecycle + Runtime degradation. |
| **CoreVisualProfile** | ✅ Implementado | Centraliza velocidades, waveform, opacidad, pulse y sweep por `AssistantVisualState`. | Mantener toda la semántica visual por estado centralizada aquí. | Alimentado por eventos reales. |
| **TelemetryPanel v2** | ✅ Real integrada | Panel visual v2 cerrado + providers reales. CPU/RAM/Network provienen de OSHI; GPU/VRAM/temperatura provienen de NVML vía JNA; polling `LIVE // 1s` validado end-to-end. | Thresholds, warnings térmicos, estados degradados/unavailable, selección robusta de interfaz/GPU, histórico opcional y cadence configurable. | Ya integrada mediante `OshiHostTelemetryProvider` + `NvidiaGpuTelemetryProvider` + `RealCoreVisualSource`. |
| **RuntimePanel v2** | ✅ Real integrado | Runtime compacto con Phi/Qwen/STT/TTS reales. `RuntimeStatusCoordinator` además propaga pérdida de capacidad a `DEGRADED` cuando corresponde. | Endurecer liveness/recovery posterior al startup y detalles de error bajo demanda. | `RuntimeStatusCoordinator` + coordinadores/workers actuales de Ares. |
| **AresWorkspaceView** | ✅ Arquitectura validada | Shell común, navegación por tabs, `StackPane` de contenido, workspaces heterogéneos y geometría estable independientemente del `prefHeight` del workspace activo. | Formalizar navegación programática externa y, opcionalmente, transiciones suaves. | Eventos externos, notificaciones y navegación contextual. |
| **TOOLS Workspace** | ✅ Real integrado v1 | Renombrado desde DEV. Catálogo `ares-tools-applications.json`, top 5 táctil (`IntelliJ IDEA`, `Visual Studio Code`, `PowerShell`, `TIDAL`, `Studio One`), `More Tools` mediante Interaction Surface paginada y ejecución real reutilizando `OsCommandSkill`/`ApplicationRegistry`. | Afinar ranking/usage real, estados visuales por acción y política final de exposición táctil por categoría. | OS Skill / catálogo global de aplicaciones ya reutilizado. |
| **MEDIA Workspace** | ✅ Real integrado v1 | GSMTC/Windows media session para metadata, artwork, progreso y transport; Core Audio para output device; UI Automation de TIDAL para calidad efectiva y `QUEUE // NEXT`. TIDAL validado end-to-end. | Fijar selección exclusivamente a providers musicales (TIDAL/Spotify) para evitar que WhatsApp/video tome el foco al pausar; volumen queda como R&D separado por Exclusive Mode/Focusrite. | `WindowsMediaSessionProvider` + `WindowsAudioOutputProvider` + `TidalMediaEnrichmentProvider`. |
| **FILES Workspace** | ✅ Cerrado v1 para mockup | Explorer embebido con navegación mock, breadcrumb dinámico/compactado, Back/Up/Home con iconos HUD + tooltips, búsqueda local, selección y `SELECTION // CONTEXT`. | Mantener congelado visualmente. Quedan integración real, preview, permisos, operaciones y datos reales. | `java.nio.file`, controller async y snapshots reales. |
| **SYSTEM Workspace** | ✅ Cerrado v1 para mockup | Panel operativo 2×2 con Audio, Network, Displays y Power/Session; iconos HUD por sección; estados `ACTIVE/CONNECTED`, volumen, link/latency, topología de monitores y sesión del sistema. | Mantener composición congelada. Quedan providers reales, quick controls, estados degradados y acciones confirmables. | JNA / Windows APIs / PowerShell cuando corresponda. |
| **WEB / RESEARCH Workspace** | ✅ Cerrado v1 para mockup | Superficie documental con status rail, query, layout 58/42, executive summary, findings, visual evidence, sources/context y lifecycle mock `RESEARCHING → PARTIAL → COMPLETE`. Sincronización validada con Core/Header/TTS mediante coordinator. | Integrar `ResearchResult`, fuentes/citas/imágenes reales, errores, historial y notificación `RESEARCH COMPLETE`. | GPT API / Qwen / Browser / MCP / `ResearchResult`. |
| **MockCoreVisualSource** | ✅ Funcional | Genera snapshots periódicos sin levantar infraestructura real. | Ampliar sólo si se requieren nuevos estados mock. | Reemplazar por sources reales. |
| **RealCoreVisualSource** | ✅ Funcional | Combina host telemetry (OSHI) y GPU telemetry (NVML), genera `CoreVisualSnapshot` cada 1 s y mantiene la UI desacoplada de los providers. | Añadir degradación explícita por provider y evolucionar runtime states reales. | Fuente real actual del dashboard para Telemetry. |
| **Core Visual en Main** | ✅ Integrado | `JavaFxCoreVisual` y `RealCoreVisualSource` ya forman parte del arranque principal de Ares; el demo queda como smoke test. | Mantener shutdown ordenado y completar lifecycle de estados/eventos. | `PresentationComponents` + `ResourceCleanup`. |
| **JavaFxCoreVisualDemo** | ✅ Funcional | Smoke test visual del dashboard completo; ya puede ejecutarse con `RealCoreVisualSource`, OSHI y NVML para validar Telemetry real. | Mantener modo de regresión visual y evitar acoplarlo al startup productivo. | Seguir utilizándolo como demo/regresión visual. |
| **Interaction Surface** | ✅ Lifecycle integrado | `CHOICE`, `CONFIRMATION`, `FREE_TEXT` siguen separados del dashboard; cuando la superficie queda visible, el Core/Header pasan a `INTERACTING` y liberan el override al submit/cancel/expire/unavailable/close. | Teclado táctil dinámico para `TOUCH ONLY`; notificación pasiva espejo en monitor de trabajo queda diferida. | `DefaultInteractionService` + `InteractionLifecycleListener` + `Source.INTERACTION`. |
| **VisualOutput** | ✅ Separado + política de display real | Overlays/notificaciones usan la ventana foreground para elegir monitor de trabajo, excluyen explícitamente el Command Deck 1080p y hacen fallback a primary. | Notificación pasiva espejo de Interaction en 1440p queda diferida/no bloqueante. | Routing contextual por tipo de salida. |
| **Voice Input / MUTE** | ✅ Integrado | `VoiceInputController` bloquea micrófono antes de VAD/STT; el Command Deck conmuta a `TOUCH ONLY`; TTS y waveform de salida continúan operativos. | Teclado interactivo/dinámico para entrada táctil de texto. | `VoicePipeline` + `VoiceInputController` + Footer. |
| **Iconography v1** | ✅ Cerrada para mockup | `HudIcon` + `HudIconView` como renderer centralizado; transport de MEDIA, navegación de FILES y headers de SYSTEM migrados a iconos lineales propios; TOOLS conserva monogramas por diseño. | Añadir nuevos glyphs sólo cuando exista una necesidad funcional concreta; mantener fallback textual/monograma. | Assets/provider-specific icons sólo si una integración real lo requiere. |
| **Taskbar / fullscreen / bounds** | ⏸️ Postergado | Ventana dedicada funcional para desarrollo. | Quitar taskbar, usar bounds completos y resolver política multi-monitor al final. | Window management productivo. |

---

# 2. Arquitectura de Workspaces

La superficie modular actual queda:

```text
CoreDashboardView
│
├── TelemetryPanel
├── RuntimePanel
├── AresCoreView
│
└── AresWorkspaceView
     │
     ├── TOOLS
     │    └── ToolsWorkspaceView
     │
     ├── MEDIA
     │    └── MediaWorkspaceView
     │
     ├── FILES
     │    └── FilesWorkspaceView
     │
     ├── SYSTEM
     │    └── SystemWorkspaceView
     │
     └── WEB
          └── ResearchWorkspaceView
```

Principios actuales:

- `AresWorkspaceView` controla navegación y hosting.
- Cada workspace posee layout, snapshots y controllers propios.
- La UI no conoce directamente detalles de COM, UI Automation, OSHI, NVML o providers externos.
- Los modelos de estado especializados permanecen separados de `CoreVisualSnapshot`.
- Los workspaces pueden cargarse de forma lazy.
- La navegación programática externa sigue pendiente de formalización.
- Los datos específicos de proveedor se enriquecen fuera de la vista y se componen en el controller correspondiente.

---

# 3. TOOLS Workspace

## Estado

✅ **Integración real v1 cerrada**

## Implementado / validado

El antiguo `TOOLS Workspace` evolucionó a **TOOLS**, evitando limitar la superficie táctil exclusivamente a herramientas de desarrollo.

La configuración proviene de:

```text
config/ares-tools-applications.json
```

El top 5 actual del Command Deck es:

```text
IntelliJ IDEA
Visual Studio Code
PowerShell
TIDAL
Studio One
```

Flujo táctil real:

```text
ToolsWorkspaceView
      ↓ TOUCH
ToolsActionHandler
      ↓
SpeechProcessingService.submitDirectTurn(...)
      ↓
AssistantPipeline.processDirectTurn(...)
      ↓ EXECUTING
OsCommandSkill.executionAction(...)
      ↓
ApplicationRegistry / ApplicationCatalog
      ↓
WindowsApplicationController
      ↓
AssistantOutputCoordinator
      ↓
TTS / SPEAKING
```

Validado:

- `PROCESSING → EXECUTING → SPEAKING`;
- apertura real de aplicaciones;
- resolución de aplicaciones reutilizando el dominio OS existente;
- no se lanzan executables directamente desde JavaFX;
- `More Tools` abre una Interaction Surface;
- selección paginada de aplicaciones adicionales;
- `INTERACTING` mientras la superficie está visible;
- retorno a `PROCESSING/EXECUTING/SPEAKING` al seleccionar;
- catálogo desacoplado de la lista fija de cards;
- Bionic eliminado del catálogo al dejar de formar parte del entorno actual;
- `ApplicationRegistry` sigue siendo la autoridad de resolución.

## Política actual

El JSON funciona como **catálogo de herramientas/aplicaciones**, pero la superficie táctil sigue siendo intencionalmente acotada:

```text
Top 5
  ↓
More Tools
  ↓
selección paginada
```

No se pretende convertir todo el Command Deck en un launcher indiscriminado.

## Pendiente / mejoras

- ranking de top 5 basado en uso real;
- persistencia de usage separada del catálogo;
- decidir si el ranking considera tanto aperturas por voz como por touch;
- mejorar estados visuales selected/executing;
- ampliar categorías sin duplicar resolución;
- mantener `resolverTarget` como input lógico y nunca lanzar `targetPath` directamente.

## Decisión visual

Los monogramas siguen siendo válidos para herramientas técnicas cuando aportan reconocimiento rápido. No es necesario migrar todo a logos de marca.

---

# 4. MEDIA Workspace

## Estado

✅ **Integración real v1 cerrada para TIDAL**

La vista dejó de ser mock y ya compone tres fuentes independientes:

```text
Windows GSMTC / mediainterface
        ↓
WindowsMediaSessionProvider
        ├─ source application
        ├─ title / artist / album
        ├─ position / duration
        ├─ playback state
        ├─ artwork
        └─ previous / play-pause / next

Windows Core Audio / COM
        ↓
WindowsAudioOutputProvider
        └─ endpoint / friendly device name

TIDAL UI Automation
        ↓
TidalMediaEnrichmentProvider
        ├─ effective quality
        └─ queue / next

                ↓
       MediaWorkspaceController
                ↓
       MediaWorkspaceSnapshot
                ↓
        MediaWorkspaceView
```

## Implementado / validado

### Sesión multimedia

- detección de sesiones Windows;
- selección de sesión activa;
- TIDAL detectado como `com.squirrel.TIDAL.TIDAL`;
- normalización visual a `TIDAL // ACTIVE`;
- título real;
- artista real;
- álbum real;
- posición real;
- duración real;
- estado `PLAYING/PAUSED`;
- controles Previous / Play-Pause / Next;
- progress bar corregido y proporcional.

### Artwork

El artwork se obtiene desde `NowPlaying.getArtwork()` y se propaga:

```text
GSMTC thumbnail
      ↓
mediainterface
      ↓ Base64
MediaTrack.artwork
      ↓
JavaFX Image
      ↓
ImageView
```

La decodificación se evita cuando la carátula no cambia.

### AUDIO // OUTPUT

`WindowsAudioOutputProvider` usa Core Audio mediante COM/JNA:

```text
IMMDeviceEnumerator
      ↓
GetDefaultAudioEndpoint(eRender, eMultimedia)
      ↓
IMMDevice
      ├─ endpoint id
      └─ PKEY_Device_FriendlyName
```

Validado con:

```text
Altavoces (2- Focusrite USB Audio)
```

Para la Focusrite:

```text
hardwareVolume=false
volume=N/A
```

Por tanto `VOLUME --` es intencional y evita presentar el mixer software de Windows como si fuese el volumen efectivo de un stream WASAPI Exclusive.

### QUALITY

TIDAL expone en Raw UI Automation:

```text
ControlType: Text
Name: "16-bit 44.1kHz"
FrameworkId: Chrome
```

`TidalMediaEnrichmentProvider` obtiene la calidad efectiva mediante UIA y la muestra en el HUD.

Ejemplos soportados por el patrón actual:

```text
16-bit 44.1kHz
24-bit 96kHz
24-bit 192kHz
```

### QUEUE // NEXT

TIDAL expone `Reproducir cola` como:

```text
Table
└─ Group
   └─ Row
      └─ Canción
         ├─ título [Link]
         ├─ artista [Link]
         ├─ artista adicional [Link] opcional
         └─ Mostrar opciones
```

El enrichment toma los siguientes items de `A continuación` y el Command Deck presenta actualmente hasta 3 tracks:

```text
01  Title  //  Artist
02  Title  //  Artist
03  Title  //  Artist
```

El provider usa cache/refresco propio para no recorrer el árbol UIA en cada repaint de JavaFX.

## Pendiente importante — selección de sesión musical

Existe un comportamiento conocido:

```text
TIDAL PLAYING
→ sesión activa = TIDAL

TIDAL PAUSED
+
WhatsApp notification / browser video activo
→ Windows puede cambiar la active session
→ MEDIA puede saltar a WhatsApp/video
```

La solución futura no debe ser “seguir siempre la sesión activa” sino introducir una política explícita de selección musical:

```text
MediaSessionSelectionPolicy
      ↓
music providers permitidos
├─ TIDAL
├─ Spotify
└─ futuros providers musicales
```

Objetivo:

- mantener/pinear el provider musical preferido mientras exista;
- ignorar sesiones de notificaciones, mensajería y video para este workspace;
- permitir Spotify además de TIDAL;
- separar `active Windows session` de `selected music session`.

Este pendiente se deja para el track de **music-session filtering/pinning**.

## Pendiente separado — volumen

El volumen queda fuera del cierre v1:

- TIDAL Exclusive Mode deshabilita su control visual de volumen;
- Focusrite no reporta hardware endpoint volume;
- `IAudioEndpointVolume` no representa de forma fiable el nivel efectivo en este escenario;
- queda abierta investigación con `IAudioMeterInformation`, Focusrite/driver/vendor telemetry o semántica de nivel dBFS;
- no bloqueará el resto de MEDIA.

## Mejoras futuras

- `MediaSessionSelectionPolicy` TIDAL/Spotify;
- resiliencia adicional ante `UIA_E_ELEMENTNOTAVAILABLE`;
- conservar último enrichment válido ante reconstrucciones transitorias del árbol Chrome;
- queue interactiva sólo si aporta valor real;
- `shuffle/repeat` sólo si el provider lo expone de forma fiable;
- nivel/peak dBFS como métrica distinta de volumen;
- Spotify enrichment cuando se integre.

---

# 5. FILES Workspace

## Estado

✅ **FilesWorkspaceView v1 cerrado para mockup**

## Implementado / validado

- Explorer completamente embebido en Ares.
- No se lanza `explorer.exe`.
- Navegación mock entre directorios.
- `Back`.
- `Up`.
- `Home`.
- Back/Up/Home usan `HudIconView` (`BACK`, `UP`, `HOME`).
- Tooltips `Back`, `Up` y `Home` validados en hover.
- Breadcrumb dinámico.
- Breadcrumb compactado en rutas largas.
- Breadcrumb navegable.
- Search local sobre el snapshot actual.
- Listado de carpetas y archivos.
- Columnas `NAME / TYPE / SIZE`.
- Selección visual de archivo.
- `SELECTION // CONTEXT`.
- Mock de `MODIFIED`.
- Panel `LOCATIONS`.
- Scroll propio.
- Sin estilos Modena visibles.
- Render coherente al cambiar directorio.
- Búsqueda y selección verificadas visualmente.

Ejemplo validado:

```text
HOME > … > Proyectos Personales > AI > agentic-ai

SEARCH: pom

NAME       pom.xml
TYPE       XML
SIZE       4 KB

SELECTION // CONTEXT
NAME       pom.xml
TYPE       XML DOCUMENT
SIZE       4 KB
MODIFIED   19 SEP 2026 21:10
```

## Pendiente / mejoras

Quedan explícitamente fuera del mock actual:

- `java.nio.file`;
- directorios y archivos reales;
- carga async;
- permisos;
- access denied;
- filesystem errors;
- drives;
- recent reales;
- favorites/pinned;
- thumbnails;
- preview ligero;
- metadata real;
- copiar;
- mover;
- borrar;
- rename;
- context menu;
- drag/drop;
- file watcher;
- archivos grandes;
- symlinks/reparse points;
- hidden files;
- estados loading/empty/error reales.

## Arquitectura futura

```text
FilesWorkspaceView
        ↑
FilesWorkspaceController
        ↑
FileSystemService
        ↑
java.nio.file.Files
```

Reglas:

- nunca bloquear JavaFX Application Thread;
- filesystem fuera del FX thread;
- snapshots inmutables hacia UI;
- errores/permisos como estado de presentación.

---

# 6. SYSTEM Workspace

## Estado

✅ **SystemWorkspaceView v1 cerrado para mockup**

## Objetivo validado

SYSTEM funciona como un panel operativo embebido para observar configuración y estado inmediato del equipo sin duplicar la función de `TelemetryPanel` y sin convertirse en un launcher hacia ventanas externas.

La composición final v1 utiliza una grilla 2×2:

```text
SYSTEM // CONTROL
LOCAL MACHINE // MOCK

┌─────────────────────────┐  ┌─────────────────────────┐
│ AUDIO // DEVICES        │  │ NETWORK // LINK         │
│ ● ACTIVE                │  │ ● CONNECTED             │
│                         │  │                         │
│ OUTPUT  FOCUSRITE USB   │  │ ADAPTER  ETHERNET      │
│ INPUT   FOCUSRITE USB   │  │ IP       192.168.1.42  │
│ VOLUME  62% ━━━━━───    │  │ LINK     1.0 Gbps      │
│                         │  │                         │
│                         │  │ DOWN 14.5 Mbps          │
│                         │  │ UP    3.2 Mbps          │
│                         │  │ LATENCY 5 ms            │
└─────────────────────────┘  └─────────────────────────┘

┌─────────────────────────┐  ┌─────────────────────────┐
│ DISPLAYS // TOPOLOGY    │  │ POWER // SYSTEM         │
│                         │  │                         │
│ 01 2560×1440 @ 85 Hz    │  │ POWER                   │
│              PRIMARY    │  │ PLAN     BALANCED      │
│ 02 1920×1080 @ 60 Hz    │  │ SOURCE   AC POWER      │
│              [ ARES ]   │  │                         │
│ 03 2560×1440 @ 75 Hz    │  │ SESSION                 │
│              EXT        │  │ HOST     ARES-DESKTOP   │
│                         │  │ STATE    ACTIVE         │
│                         │  │ UPTIME   03:42:18       │
└─────────────────────────┘  └─────────────────────────┘
```

## Implementado / validado

### Audio

- `AUDIO // DEVICES`.
- estado `● ACTIVE` con la misma semántica positiva/celeste que `● CONNECTED`;
- dispositivo de salida mock;
- dispositivo de entrada mock;
- volumen porcentual;
- barra de volumen propia;
- soporte de `muted` ya presente en el snapshot para estados futuros.

### Network

- `NETWORK // LINK`;
- estado `● CONNECTED`;
- adapter;
- IP;
- link speed (`1.0 Gbps` en mock);
- download;
- upload;
- latency;
- `DOWN / UP / LATENCY` comparten jerarquía visual y valor en gris plata.

### Displays

- `DISPLAYS // TOPOLOGY`;
- tres monitores mock;
- resolución y refresh rate;
- roles `PRIMARY`, `ARES` y `EXT`;
- badge `ARES` diferenciado visualmente para el display dedicado al Visual Core.

### Power / Session

- `POWER // SYSTEM`;
- subsección `POWER`;
- plan energético;
- source (`AC POWER`);
- subsección `SESSION`;
- host;
- estado de sesión;
- uptime formateado.

### Iconografía de secciones

Los cuatro módulos usan iconografía lineal propia mediante `HudIconView`:

```text
AUDIO      → HudIcon.AUDIO
NETWORK    → HudIcon.NETWORK
DISPLAYS   → HudIcon.DISPLAY
POWER      → HudIcon.POWER
```

Los glyphs permanecen secundarios al texto del encabezado y no alteran la jerarquía de las cards.

### Arquitectura / modelos

La vista consume modelos separados del estado global:

```text
SystemWorkspaceSnapshot
├── SystemAudioSnapshot
├── SystemNetworkSnapshot
├── List<SystemDisplaySnapshot>
└── SystemPowerSnapshot
```

Esto mantiene SYSTEM desacoplado de `CoreVisualSnapshot` y deja preparada la sustitución de mocks por providers reales.

### Sincronización global validada

El lifecycle mock ya controla el estado visual global mediante `AssistantVisualStateCoordinator`:

```text
RESEARCHING / PARTIAL
        ↓
PROCESSING

COMPLETE + TTS SPEAKING
        ↓
SPEAKING

TTS DELIVERED
        ↓
IDLE
```

La transición fue validada simultáneamente en Header, `AresCoreView` y Research Workspace, sin detener las actualizaciones de telemetría.

## Decisiones visuales cerradas

- composición 2×2 conservada;
- no repetir CPU/RAM/GPU/VRAM porque pertenecen a `TelemetryPanel`;
- estados positivos operativos (`ACTIVE`, `CONNECTED`) usan cyan;
- valores secundarios y métricas hermanas usan gris plata;
- `ARES` en topology actúa como badge funcional;
- no añadir `Processes`, `Storage` o `Devices` sólo para llenar espacio;
- no añadir todavía acciones `Sleep / Restart / Shutdown`.

## Pendiente / mejoras

Quedan fuera del mock v1:

- dispositivos de audio reales;
- volumen y mute reales;
- selección/cambio de output e input;
- adapter, IP, link speed y latency reales;
- estado `DISCONNECTED / DEGRADED`;
- topología real de monitores;
- identificación real del display dedicado a Ares;
- power plan real;
- source real;
- host/session/uptime reales;
- quick controls;
- warnings visuales;
- estados `READY / DEGRADED / OFFLINE`;
- acciones destructivas con `InteractionService`/confirmation;
- evaluar `Processes / Storage / Devices` en una futura v2 sólo si aportan valor operativo.

## Integración futura

```text
SystemWorkspaceView
        ↑
SystemController
        ↑
System providers / Windows adapters
        ├── Audio provider
        ├── Network provider
        ├── Display provider
        ├── Power/session provider
        ├── JNA / Windows APIs
        └── PowerShell sólo donde sea razonable
```

---

# 7. WEB / RESEARCH Workspace

## Estado

✅ **ResearchWorkspaceView v1 cerrado para mockup**

## Responsabilidad validada

WEB no funciona principalmente como navegador ni como launcher.

Es la superficie documental de las operaciones de investigación de Ares:

```text
CURRENT_RESEARCH
       ↓
GPT API / Qwen
       ↓
Browser / MCP / tools
       ↓
ResearchResult
       ├── TTS summary
       └── visual result
               ↓
      ResearchWorkspaceView
```

La intención validada es que Ares pueda entregar una síntesis por TTS mientras la documentación, evidencia, fuentes e imágenes quedan materializadas visualmente en el workspace.

## Composición visual v1

La composición final utiliza una relación aproximada **58 / 42** entre brief y evidencia:

```text
WEB // RESEARCH
ENGINE // GPT API + BROWSER // MOCK

● COMPLETE      TTS // DELIVERED      SOURCES // 04      UPDATED // HH:mm

CURRENT_RESEARCH // QUERY
¿Qué modelos locales son adecuados para una RTX 4070 de 12 GB evitando CPU off-load?

┌─────────────────────────────────┐  ┌─────────────────────────────┐
│ RESEARCH // BRIEF               │  │ SOURCES // EVIDENCE         │
│ EXECUTIVE SUMMARY               │  │ 01 NVIDIA // DOCUMENTATION  │
│ ...                             │  │    GPU memory...            │
│ KEY FINDINGS                    │  │ 02 LM STUDIO // RUNTIME     │
│ 01 ...                          │  │    Local model runtime...   │
│ 02 ...                          │  │ 03 MODEL CARD // REFERENCE  │
│ 03 ...                          │  │ 04 LOCAL BENCHMARK // ARES │
│ VISUAL // EVIDENCE              │  │ SOURCE // CONTEXT           │
│ [ VISUAL 01 ] [ VISUAL 02 ]     │  │ selected source details...  │
└─────────────────────────────────┘  └─────────────────────────────┘
```

## Implementado / validado

### Status rail

- estado de research;
- `TTS // PENDING` / `TTS // SPEAKING` / `TTS // DELIVERED`;
- cantidad de sources;
- timestamp de actualización;
- pseudo-clases para estados visuales.

Estados modelados:

```text
IDLE
RESEARCHING
PARTIAL
COMPLETE
FAILED
```

### Current query

- `CURRENT_RESEARCH // QUERY`;
- pregunta visible durante todo el lifecycle;
- panel independiente del resultado;
- texto ajustable sin modificar la geometría global.

### Research brief

- `EXECUTIVE SUMMARY`;
- `KEY FINDINGS`;
- findings numerados;
- aparición progresiva;
- `VISUAL // EVIDENCE`;
- placeholders listos para ser reemplazados por imágenes reales.

### Sources / evidence

- source list compacta;
- selección táctil;
- pseudo-clase `selected`;
- dominio/origen;
- título con wrap;
- filas compactas sin truncado forzado a una línea;
- `ScrollPane` ajustado al ancho disponible;
- `SOURCE // CONTEXT`;
- contexto sincronizado con la source realmente seleccionada.

### Lifecycle mock

Se validó visualmente:

```text
RESEARCHING
    ↓
PARTIAL
    ↓
COMPLETE
```

#### RESEARCHING

```text
● RESEARCHING
TTS // PENDING
SOURCES // 00
UPDATED // --:--
```

- status con pulso;
- summary de actividad;
- sin findings;
- sin visuales;
- sin sources;
- `SOURCE // CONTEXT` muestra adquisición/validación de evidencia;
- contenido inexistente usa `managed=false` + `visible=false`.

#### PARTIAL

```text
● PARTIAL
TTS // PENDING
SOURCES // 02
```

- summary preliminar;
- primeros findings;
- primeras sources;
- source context utilizable;
- aparición progresiva mediante `FadeTransition`.

#### COMPLETE

```text
● COMPLETE
TTS // DELIVERED
SOURCES // 04
UPDATED // HH:mm
```

- executive summary final;
- findings completos;
- visual evidence;
- sources completas;
- source context;
- TTS marcado como entregado.

## Arquitectura / modelos

```text
ResearchWorkspaceSnapshot
├── ResearchState
├── query
├── summary
├── List<String> findings
├── List<ResearchSource>
├── List<ResearchVisual>
├── ResearchTtsState
└── completedAt
```

Modelos auxiliares:

```text
ResearchState
ResearchTtsState
ResearchSource
ResearchVisual
ResearchWorkspaceSnapshot
ResearchLifecycleListener
```

Contrato futuro:

```text
Research pipeline
       ↓
ResearchResult
       ↓
ResearchWorkspaceSnapshot
       ↓
ResearchWorkspaceView
```

La vista no debe conocer directamente GPT API, Qwen, Browser, MCP o el mecanismo que produjo la investigación.

## Decisiones visuales cerradas

- WEB es una superficie documental, no un simple launcher;
- composición `58 / 42`;
- query separada del resultado;
- sources como índice documental compacto;
- source context dedicado a la evidencia seleccionada;
- títulos de source pueden ocupar más de una línea;
- no truncar información útil sólo para ahorrar altura;
- visual evidence usa placeholders hasta disponer de imágenes reales;
- el workspace se adapta al shell y no modifica la altura global;
- Header/Footer permanecen invariantes entre DEV / MEDIA / FILES / SYSTEM / WEB.

## Pendiente / mejoras

Quedan fuera del mock v1:

- sustituir el `Timeline` mock por eventos reales del pipeline;
- mapear `ResearchResult → ResearchWorkspaceSnapshot`;
- TTS real y estado de entrega real;
- sources reales con URL/citación;
- acción para abrir/visualizar una source;
- imágenes reales;
- captions y metadata de imágenes;
- estados `FAILED`, retry y cancel;
- resultado parcial real;
- estado sin imágenes;
- estado sin sources;
- historial de investigaciones;
- persistencia/retención de resultados;
- notificación `RESEARCH COMPLETE`;
- navegación programática hacia WEB al terminar una investigación;
- posible browser/source preview embebido;
- evaluar convivencia con `VisualOutput`.

## Integración futura

```text
CURRENT_RESEARCH
       ↓
Research pipeline
       ├── GPT API
       ├── Qwen
       ├── Browser
       └── MCP / tools
       ↓
ResearchResultStore
       ├── TTS delivery
       ├── Core/Header state
       └── Workspace notification
               ↓
        WorkspaceNavigator
               ↓
      ResearchWorkspaceView
```

---
# 8. TelemetryPanel v2

## Estado

✅ **Visual v2 cerrada + Telemetry real integrada**

## Responsabilidad validada

Telemetry representa **actividad y utilización en tiempo real**, no configuración.

La separación conceptual queda:

```text
TELEMETRY
→ qué está ocurriendo ahora

SYSTEM WORKSPACE
→ cómo está configurada la máquina
```

## Composición final

```text
SYSTEM // TELEMETRY
LIVE // 1s

SYSTEM LOAD

CPU                     3 %
████░░░░░░░░░░░░

RAM              24,3 / 31 GB
████████████████░

GPU LOAD

GPU                    21 %
████░░░░░░░░░░░░

VRAM              2,0 / 12 GB
███░░░░░░░░░░░░░

GPU T°                 38 °C
██░░░░░░░░░░░░░░

NETWORK ACTIVITY

↓ 8,8 Mbps       ↑ 0,2 Mbps
LOCAL // 192.168.1.87
```

Los valores anteriores corresponden a una ejecución real validada en el dashboard.

## Arquitectura real integrada

```text
OSHI
 ↓
OshiHostTelemetryProvider
 ├── CPU usage
 ├── RAM used / total
 └── Network throughput + local IP

NVML / JNA
 ↓
NvidiaGpuTelemetryProvider
 ├── GPU utilization
 ├── VRAM used
 ├── VRAM total
 └── GPU temperature

        ↓
RealCoreVisualSource
        ↓
CoreVisualSnapshot
        ↓
CoreDashboardView
        ↓
TelemetryPanel
```

La UI permanece desacoplada de OSHI y NVML.

`TelemetryPanel` sólo consume snapshots de presentación; no conoce APIs nativas ni detalles del provider.

## Providers

### Host telemetry — OSHI

Implementado mediante:

```text
HostTelemetryProvider
HostTelemetrySnapshot
OshiHostTelemetryProvider
```

Responsabilidades validadas:

- CPU usage real;
- RAM usada / total real;
- interfaz de red activa;
- IPv4 local;
- download Mbps;
- upload Mbps;
- polling periódico sin bloquear JavaFX.

El throughput de red se obtiene mediante delta de bytes recibidos/enviados entre muestras.

### GPU telemetry — NVIDIA / NVML

Implementado mediante:

```text
GpuTelemetryProvider
GpuTelemetrySnapshot
NvidiaGpuTelemetryProvider
NvmlLibrary
NvmlLoader
```

Métricas reales validadas:

- GPU utilization;
- VRAM used;
- VRAM total;
- GPU temperature.

La integración utiliza NVML vía JNA y evita ejecutar `nvidia-smi` cada segundo.

## RealCoreVisualSource

`RealCoreVisualSource` consolida ambos providers y emite un `CoreVisualSnapshot` aproximadamente cada segundo.

```text
HostTelemetrySnapshot
        +
GpuTelemetrySnapshot
        ↓
CoreVisualSnapshot
```

`MockCoreVisualSource` se conserva como herramienta de regresión visual y escenarios controlados.

## Implementado / validado

### Jerarquía

- `SYSTEM // TELEMETRY`;
- cadence visible `LIVE // 1s`;
- sección `SYSTEM LOAD`;
- sección `GPU LOAD`;
- sección `NETWORK ACTIVITY`.

### Métricas reales

- CPU con barra;
- RAM usada / total;
- GPU load;
- VRAM usada / total;
- GPU temperature;
- download / upload;
- IP local.

### HudMetric v2

`HudMetric` usa:

```text
TITLE                      VALUE
████████████░░░░░░░░░░░░░
```

Se mantienen dos semánticas distintas:

```text
setValue(text, percentage)
→ recibe 0..100

setProgress(text, normalizedProgress)
→ recibe 0..1
```

### Temperatura GPU

La barra térmica usa una normalización explícita:

```java
private static double normalizeTemperature(double temperature) {
    double min = 30.0;
    double max = 95.0;

    return Math.clamp(
            (temperature - min) / (max - min),
            0.0,
            1.0
    );
}
```

Y luego:

```java
gpuTemp.setProgress(
        "%.0f °C".formatted(gpuSnapshot.temperature()),
        normalizeTemperature(gpuSnapshot.temperature())
);
```

### Network

La representación se mantiene compacta:

```text
↓ DOWNLOAD      ↑ UPLOAD
LOCAL // IP
```

No se duplican adapter/link/power-related metadata que pertenecen mejor a SYSTEM Workspace.

## Validación end-to-end

Se verificó que:

```text
CPU      → cambia con carga del sistema
RAM      → refleja uso real
NETWORK  → cambia con tráfico real
LOCAL IP → proviene de la interfaz activa

GPU      → refleja utilización NVML
VRAM     → refleja memoria usada/total NVML
GPU T°   → refleja temperatura NVML
```

También se verificó que Telemetry continúa actualizándose mientras el lifecycle global de Research mueve el dashboard entre estados como `PROCESSING`.

## Decisiones cerradas

- Telemetry es un panel de observabilidad, no un mini System Workspace;
- OSHI y NVML permanecen como providers separados;
- la UI no conoce directamente OSHI/NVML;
- `MockCoreVisualSource` se conserva para regresión visual;
- no agregar todavía CPU per-core, clocks, fan RPM, discos ni procesos;
- no agregar todavía sparklines;
- la cadence `LIVE // 1s` queda explícita;
- temperatura usa escala propia;
- network prioriza actividad instantánea;
- mantener una densidad compacta para convivir con Runtime en el lateral izquierdo.

## Pendiente / mejoras

La integración real principal ya está terminada.

Quedan mejoras de robustez/operación:

- thresholds visuales;
- warning térmico;
- estado `UNAVAILABLE` / `DEGRADED` por provider;
- manejo explícito de NVML no disponible;
- selección robusta de GPU si existen múltiples adapters;
- selección robusta de interfaz de red;
- hotplug / cambios de interfaz;
- cadence configurable;
- histórico opcional;
- micrográficas sólo si aportan valor real;
- logging/diagnóstico estructurado en vez de `System.err`.

---
# 9. RuntimePanel v2

## Estado

✅ **Visual v2 cerrada + Runtime real integrado**

## Responsabilidad validada

Runtime representa la **salud operacional de los componentes de Ares**.

La separación conceptual queda:

```text
TELEMETRY
→ recursos físicos y actividad de hardware

RUNTIME
→ disponibilidad/salud de servicios y componentes de Ares

CORE STATE
→ actividad actual del asistente
```

Esto evita mezclar, por ejemplo, `TTS READY` con `SPEAKING`: el primero es salud del componente y el segundo es actividad del asistente.

## Composición final

```text
ARES // RUNTIME
LOCAL // PIPELINE

INFERENCE
PHI ROUTER // ROUTING              ● READY
QWEN MAIN  // REASONING            ● READY

VOICE PIPELINE
STT // TRANSCRIPTION               ● READY
TTS // SPEECH OUTPUT               ● READY
```

## Arquitectura real integrada

```text
LmStudioStartupCoordinator
        │
        ├── PHI_ROUTER
        └── QWEN_MAIN
                │
                ▼
      RuntimeStatusCoordinator
                ▲
                │
     FasterWhisper lifecycle
     Piper lifecycle
                │
                ▼
      RuntimeSnapshot tipado
                │
                ▼
          RuntimePanel
```

## Contrato de estado

`CoreVisualSnapshot.RuntimeSnapshot` dejó de transportar strings arbitrarios y usa:

```text
RuntimeVisualState
├── CHECKING
├── LOADING
├── READY
├── RETRY_WAIT
├── FAILED
└── OFFLINE
```

`RuntimeStatusCoordinator` mantiene el último estado conocido de:

```text
PHI
QWEN
STT
TTS
```

## Implementado / validado

- `RuntimePanel` en `VBox`;
- secciones `INFERENCE` / `VOICE PIPELINE`;
- roles inline;
- estados alineados a la derecha;
- pseudo-clases por `RuntimeVisualState`;
- `PHI/QWEN` actualizados por eventos de `LmStudioStartupCoordinator`;
- `STT` pasa por `LOADING → READY/FAILED` durante el arranque real de Faster-Whisper;
- `TTS` pasa por `LOADING → READY/FAILED` durante el arranque real de Piper;
- Qwen puede permanecer `CHECKING/LOADING` mientras Phi ya habilita el voice runtime;
- la UI no hace polling adicional a LM Studio, Whisper ni Piper;
- `RealCoreVisualSource` incorpora el `RuntimeSnapshot` al snapshot visual periódico.

## Geometría del lateral izquierdo

Se mantiene la regla estabilizada:

```text
Telemetry → altura natural
Runtime   → absorbe el espacio restante
Workspace → ocupa altura completa del body
```

El borde inferior de Runtime y Workspace permanece alineado.

## Decisiones cerradas

- Runtime expresa **salud/disponibilidad**, no actividad;
- `TTS READY` permanece READY mientras el Core puede estar `SPEAKING`;
- `STT READY` permanece READY mientras el Core puede estar `LISTENING/PROCESSING`;
- no ejecutar health checks por segundo sólo para pintar la UI;
- reutilizar eventos/lifecycle existentes como fuente de verdad;
- mantener `RuntimeStatusCoordinator` desacoplado de JavaFX.

## Pendiente / mejoras

- detectar caída posterior al startup de Faster-Whisper/Piper;
- recovery/restart del worker y reflejo de `FAILED/OFFLINE`;
- startup duration;
- degradación si daemon/API server dejan de estar disponibles;
- detalles de error bajo demanda;
- provider unavailable;
- historial de transiciones sólo si aporta valor operacional;
- evolucionar el runtime de modelos desde el esquema fijo Phi/Qwen hacia un **orquestador on-demand consciente de capacidades y recursos**.

## Dirección futura — runtime de modelos on-demand

La semántica de `DEGRADED` no debe equivaler a “modelo no cargado”.

En la evolución prevista de Ares, varios modelos pesados se cargarán sólo cuando una capacidad los requiera. Por tanto, un modelo deliberadamente descargado para liberar RAM/VRAM representa un estado normal de disponibilidad bajo demanda, no una degradación del sistema.

Ejemplo esperado:

```text
QWEN unloaded / idle
→ capacidad no solicitada
→ estado normal
→ NO DEGRADED
```

`DEGRADED` debe activarse cuando Ares **necesita** una capacidad y el runtime no puede proporcionarla:

```text
consulta requiere Qwen
        ↓
load qwen-main
        ↓
FAILED / timeout / postcondition failed
        ↓
DEGRADED
```

o cuando una transición de lifecycle falla:

```text
modelo activo
   ↓
unload intencional
   ↓
nuevo load solicitado
   ↓
no puede volver a iniciarse
   ↓
DEGRADED
```

La misma regla deberá aplicarse al futuro conjunto de modelos especializados, por ejemplo:

```text
Phi        → routing / clasificación ligera / control
Qwen       → reasoning / research local
Flux       → image generation
MedGemma   → tareas médicas especializadas
Gemma      → capacidades generales/especializadas futuras
...
```

La arquitectura futura debe separar al menos tres conceptos:

```text
MODEL AVAILABILITY
→ el modelo existe y puede cargarse

MODEL RESIDENCY
→ el modelo está actualmente cargado en RAM/VRAM

CAPABILITY HEALTH
→ Ares pudo satisfacer la capacidad cuando fue requerida
```

Sólo el último concepto debe influir directamente en `DEGRADED`.

Esto permitirá que el Runtime Panel evolucione desde un inventario fijo de procesos residentes hacia una vista de **capacidades + residency dinámica**, evitando marcar como fallo el uso normal de carga/descarga on-demand.

---
# 10. Header / Footer v2

## Estado

✅ **Visual estable + estados/runtime/input reales**

## Header v2

```text
ARES // SYSTEM                         ● STATE // <STATE>   24 SEP 2026 // 22:22:29
VISUAL CORE
──────────────────────────────────────────────────────────────────────────────
```

### Estados productivos validados

```text
IDLE
LISTENING
PROCESSING
INTERACTING
EXECUTING
SPEAKING
DEGRADED
```

Header y Core comparten el mismo estado efectivo mediante `AssistantVisualStateCoordinator`.

## Footer v2

La rail inferior mantiene tres zonas geométricas:

```text
● ARES // READY          PHI // READY   QWEN // READY       [ MUTE ] ● VOICE + TOUCH
```

Con mute activo:

```text
● ARES // READY          PHI // READY   QWEN // READY   [ MUTE // ON ] ● TOUCH ONLY
```

### MUTE / TOUCH ONLY

`MUTE` afecta exclusivamente al input de voz:

```text
MUTE OFF
Mic → VAD → STT → comandos
Touch → disponible
TTS → activo

MUTE ON
Mic → descartado antes de VAD/STT
Touch → única modalidad de input
TTS → activo
```

Decisiones cerradas:

- no detener/reabrir `AudioCaptureService` al mutear;
- `VoiceInputController` usa estado thread-safe;
- si el usuario mutea durante una frase, se limpia el estado/buffer de escucha;
- no publicar silencio continuamente mientras TTS habla;
- `STT // READY` permanece READY: el componente está saludable aunque el input esté voluntariamente bloqueado;
- `MUTE` no implica `DEGRADED`;
- TTS y waveform real de `SPEAKING` no se ven afectados.

## Pendiente

- teclado interactivo/dinámico cuando el sistema esté en `TOUCH ONLY`;
- mantener Header/Footer sin crecimiento de metadata;
- añadir nuevas señales sólo si son operativamente útiles.

---
# 11. AresCoreView — integración real

## Estado

✅ **Core audiovisual + lifecycle visual completo**

## Estados productivos

```text
IDLE
LISTENING
PROCESSING
INTERACTING
EXECUTING
SPEAKING
DEGRADED
```

### Flujo base de voz

```text
VAD detecta habla
→ LISTENING

fin de segmento / STT / clasificación
→ PROCESSING

skill/action
→ EXECUTING

si requiere decisión humana
→ INTERACTING

TTS playback
→ SPEAKING

fin normal
→ IDLE
```

## INTERACTING real

`DefaultInteractionService` emite lifecycle sólo cuando la sesión queda realmente visible.

```text
InteractionPresenter visible
        ↓
InteractionLifecycleListener.onVisible(sessionId)
        ↓
JavaFxCoreVisual
        ↓
CoreDashboardView
        ↓
Source.INTERACTION = INTERACTING
```

La sesión mantiene ownership hasta:

```text
SUBMITTED
CANCELLED
EXPIRED
UNAVAILABLE
CLOSED
```

Al terminar, se limpia únicamente el override correspondiente al `sessionId` activo. Se validó tanto submit como cancel: ambos vuelven correctamente al estado base.

## EXECUTING real

`AssistantPipeline` publica un lifecycle de ejecución separado del procesamiento:

```text
AssistantExecutionLifecycleListener
        ↓
Source.EXECUTION = EXECUTING
```

Reglas:

- `Completed`: START → ejecución → END;
- `Async`: END ocurre cuando finaliza el `CompletionStage`;
- `AwaitingInteraction`: la ejecución termina antes de entrar a `INTERACTING`;
- al resolver la interacción, la continuación crea una nueva ejecución con otro `executionId`.

Flujo validado para una aplicación ambigua:

```text
PROCESSING
→ EXECUTING
→ INTERACTING
→ EXECUTING
→ SPEAKING / IDLE
```

## DEGRADED real

`RuntimeStatusCoordinator` calcula pérdida real de capacidad y notifica a `AssistantVisualStateStore`.

Provocan degradación persistente:

```text
model runtime → DEGRADED
STT → FAILED/OFFLINE
TTS → FAILED/OFFLINE
```

No provocan degradación por sí mismos:

```text
CHECKING
LOADING
RETRY_WAIT
MUTE voluntario
cancelación de Interaction
Telemetry provider unavailable aislado
error ordinario de un skill
```

La degradación funciona como estado persistente de reposo:

```text
DEGRADED + actividad
→ LISTENING / PROCESSING / INTERACTING / EXECUTING / SPEAKING

fin de actividad
→ DEGRADED
```

Se validó forzando `TTS = FAILED`: Runtime muestra `FAILED` y Header/Core muestran `DEGRADED`.

## Canal audiovisual real

```text
LISTENING
AudioCaptureService → VoicePipeline → Silero VAD
→ VoiceSignalSnapshot → VoiceSignalStore → AresCoreView

SPEAKING
Piper → TtsAudio → AudioPlaybackService
→ chunks reproducidos → AudioPipeline
→ VoiceSignalSnapshot → VoiceSignalStore → AresCoreView
```

Durante `LISTENING`:

```text
samples[]       → waveform real del micrófono
RMS             → amplitud/presencia
peak            → core pulse
VAD probability → halo
```

Durante `SPEAKING`:

```text
samples[] → waveform TTS real
RMS       → amplitud/presencia
peak      → core pulse
VAD       → no aplica / 0
```

RMS/peak/VAD utilizan attack/release smoothing.

## Threading

```text
audio/VAD thread       → AtomicReference.set()
TTS playback thread    → AtomicReference.set()
execution/lifecycle    → eventos desacoplados
JavaFX Application    → estado efectivo + AtomicReference.get() + render
Telemetry executor     → polling independiente a 1 s
```

No se renderiza desde threads de audio ni se generan `Platform.runLater()` por cada frame/chunk.

## Decisiones cerradas

- `INTERACTING` es semánticamente distinto de `PROCESSING`;
- `EXECUTING` representa ejecución efectiva de skill/action;
- `DEGRADED` representa pérdida de capacidad, no errores menores;
- Interaction/Execution usan IDs para evitar que una terminación tardía limpie una sesión/ejecución posterior;
- un único `VoiceSignalStore` alimenta input/output audiovisual;
- no añadir más geometría al Core por ahora.

## Pendiente

- calibrar thresholds visuales tras uso prolongado;
- health/liveness posterior al startup para STT/TTS/model runtime;
- detalles de degradación bajo demanda, no dentro del Core.

---
# 12. Iconography v1

## Estado

✅ **Cerrada para mockup**

## Arquitectura

La iconografía propia se centraliza en:

```text
HudIcon
   ↓
HudIconView
   ↓
Workspaces / Buttons / Headers
```

### `HudIcon`

Enumera el concepto visual requerido, por ejemplo:

```text
PREVIOUS
PLAY
PAUSE
NEXT
BACK
UP
HOME
SEARCH
AUDIO
NETWORK
DISPLAY
POWER
FOLDER
GLOBE
TERMINAL
MORE
```

### `HudIconView`

Es únicamente el renderer reutilizable del glyph.

Responsabilidades:

- dibujar el icono con primitivas JavaFX;
- mantener escala, stroke y color coherentes;
- permanecer `mouseTransparent`;
- no conocer acciones, controllers ni workspaces.

No es un `Button` ni ejecuta lógica.

## Aplicación validada

### MEDIA

```text
PREVIOUS
PLAY / PAUSE
NEXT
```

- transport migrado desde caracteres Unicode a `HudIconView`;
- `PAUSE` aparece cuando `MediaPlayerSnapshot.playing() == true`;
- el botón central puede alternar posteriormente a `PLAY` con estado real.

### FILES

```text
BACK
UP
HOME
```

- navegación migrada a iconos;
- tooltips `Back`, `Up`, `Home`;
- breadcrumb continúa textual;
- tipos de archivo continúan como metadata técnica:

```text
DIR
J
CSS
XML
TXT
{}
IMG
FILE
```

### SYSTEM

```text
AUDIO
NETWORK
DISPLAY
POWER
```

Los iconos acompañan a:

```text
AUDIO // DEVICES
NETWORK // LINK
DISPLAYS // TOPOLOGY
POWER // SYSTEM
```

como affordances secundarios.

### TOOLS

Los monogramas permanecen deliberadamente:

```text
IJ
VS
>_
GH
EX
...
```

No se reemplazan por logos de marca para conservar el lenguaje visual del Command Deck.

## Zonas deliberadamente sin iconos adicionales

- Header;
- Footer;
- Telemetry;
- Runtime;
- tabs de Workspace;
- Research;
- metadata documental.

## Regla visual

> Añadir un icono sólo cuando mejore reconocimiento o interacción. No usar iconografía como decoración.

## Pendiente / futuro

- glyphs adicionales únicamente para nuevas capacidades reales;
- `shuffle`, `repeat`, `mute` si MEDIA los incorpora;
- iconos de acciones contextuales cuando existan;
- assets/provider-specific sólo si una integración real los necesita;
- mantener siempre fallback textual/monograma.

---
# 13. Navegación programática

## Estado

🟡 Base disponible / abstracción pendiente

Debe estabilizarse algo equivalente a:

```java
interface WorkspaceNavigator {
    void showWorkspace(WorkspaceType type);
}
```

Casos futuros:

```text
Research complete → WEB
Media event       → MEDIA
System warning    → SYSTEM
```

sin acoplar servicios internos a JavaFX.

---

# 14. Modelo de estado

Mantener separados los snapshots de dominio:

```text
CoreVisualSnapshot
MediaWorkspaceSnapshot
MediaSessionSnapshot
AudioOutputSnapshot
MediaEnrichmentSnapshot
FilesWorkspaceSnapshot
SystemWorkspaceSnapshot
ResearchWorkspaceSnapshot
```

El estado global de Ares se compone en capas distintas:

```text
AssistantVisualStateStore
→ estado base real de actividad/health

AssistantVisualStateCoordinator
→ arbitra overrides de UI/lifecycle

VoiceSignalStore
→ señal audiovisual de alta frecuencia

RuntimeStatusCoordinator
→ salud de Phi/Qwen/STT/TTS en v1
→ futuro: salud de capacidades requeridas + residency dinámica de modelos
```

Prioridad actual de overrides:

```text
INTERACTION
   ↓
TTS
   ↓
EXECUTION
   ↓
RESEARCH
   ↓
VOICE
   ↓
base state
```

Semántica:

```text
IDLE         → sin actividad
LISTENING    → entrada de voz
PROCESSING   → STT/clasificación/routing/preparación
INTERACTING  → espera explícita de decisión humana
EXECUTING    → skill/action en ejecución
SPEAKING     → salida TTS activa
DEGRADED     → pérdida persistente de capacidad cuando no hay actividad prioritaria
```

Para Research, `ResearchResult` sigue perteneciendo al pipeline/integración y debe transformarse antes de entrar a la UI:

```text
ResearchResult
      ↓
ResearchWorkspaceSnapshot
      ↓
ResearchWorkspaceView
```

Evitar convertir `CoreVisualSnapshot` en un objeto global o introducir proveedores externos directamente en las vistas.

---
# 15. Integraciones reales — estado

| Dominio | Estado / integración |
|---|---|
| CPU / RAM | ✅ OSHI — `OshiHostTelemetryProvider` |
| GPU / VRAM / Temperature | ✅ NVIDIA NVML vía JNA — `NvidiaGpuTelemetryProvider` |
| Network | ✅ OSHI — throughput real + IPv4 local |
| Runtime | ✅ `RuntimeStatusCoordinator` + `LmStudioStartupCoordinator` + lifecycle Faster-Whisper/Piper; evolución futura hacia modelos on-demand/capability-aware |
| Core state base | ✅ Audio/VAD/TTS → `AssistantVisualStateStore` event-driven |
| Core waveform LISTENING | ✅ `VoiceSignalStore` + PCM real desde micrófono |
| Core waveform SPEAKING | ✅ PCM real del TTS por chunks durante playback |
| Core modulation | ✅ RMS / peak / VAD + smoothing |
| Interaction lifecycle | ✅ `DefaultInteractionService` → `Source.INTERACTION` → `INTERACTING` |
| Execution lifecycle | ✅ `AssistantPipeline` → `Source.EXECUTION` → `EXECUTING` |
| Degradation lifecycle | ✅ Runtime/model/STT/TTS → `AssistantVisualStateStore` → `DEGRADED` |
| Voice input mute | ✅ `VoiceInputController`; STT bypass + `TOUCH ONLY`, TTS intacto |
| Notification display policy | ✅ foreground monitor no reservado; Command Deck excluido; fallback primary |
| Passive Interaction mirror notification | ⏸ Diferida / no bloqueante |
| Dynamic touch keyboard | ⏳ Pendiente para `TOUCH ONLY` / free text |
| DEV | 🔴 Siguiente: OS Skill / application catalog / acciones táctiles reales |
| MEDIA | ✅ v1 real — GSMTC/mediainterface + Core Audio + TIDAL UI Automation; pendiente music-session filtering/pinning y volumen R&D |
| Media session / transport | ✅ `WindowsMediaSessionProvider` — metadata, artwork, position, playback state y transport |
| Audio output device | ✅ `WindowsAudioOutputProvider` — Core Audio / COM / Focusrite endpoint |
| TIDAL quality / queue | ✅ `TidalMediaEnrichmentProvider` — Raw UI Automation |
| Music-session selection | ⏳ Pendiente — filtrar/pinear TIDAL/Spotify y excluir WhatsApp/video/notificaciones |
| Media volume / level | ⏸ R&D — Exclusive Mode + Focusrite; no representar mixer software como volumen efectivo |
| FILES | ⏳ `java.nio.file` |
| SYSTEM | ⏳ providers Audio/Network/Displays/Power + APIs Windows |
| RESEARCH | ⏳ GPT API / Qwen / MCP / browser |
| Images | ⏳ research result assets |
| Provider-specific icons | Sólo si una integración real los requiere |

---
# 16. Window management / display policy

## Estado

🟡 **Política de overlays real cerrada; fullscreen/startup final postergados**

## Implementado

Las notificaciones/overlays de `JavaFxVisualOutput` ya no usan la posición del mouse como autoridad.

Política:

```text
foreground window en primary 1440p
→ overlay en primary

foreground window en secondary 1440p
→ overlay en secondary

foreground window en Command Deck 1080p
→ Command Deck excluido
→ overlay en primary

foreground no resoluble
→ primary
```

La pantalla reservada se identifica mediante `DefaultInteractionDisplayResolver` / `interaction-display.json`, no por resolución hardcodeada.

Esto mantiene separadas dos superficies:

```text
JavaFxVisualOutput
→ monitor de trabajo
→ nunca invade Command Deck

JavaFxInteractionPresenter
→ Command Deck dedicado
→ Choice / Confirmation / TextInput
```

## Diferido / no prioritario

Notificación pasiva espejo de una Interaction en el monitor 1440p activo:

```text
Command Deck
→ Interaction Surface completa e interactiva

monitor de trabajo
→ aviso pasivo: "Ares requiere una selección"
```

La Interaction real seguiría existiendo sólo en el Command Deck.

## Fase final pendiente

- fullscreen/bounds completos;
- ocultar taskbar en display dedicado;
- persistencia/validación de monitor identity;
- fallback productivo multi-monitor;
- always-on/background policy;
- startup automático de Windows;
- lifecycle de display ante hotplug/disconnect.

---
# 17. Trazabilidad de hitos

| Hito | Estado | Resultado |
|---|---|---|
| **Core Visual inicial** | ✅ | Dashboard persistente funcionando sobre display dedicado. |
| **AresCoreView v2** | ✅ | Core HUD refinado y visualmente cerrado para mockup. |
| **Workspace refactor** | ✅ | `QuickActionDeckView` generalizado a `AresWorkspaceView` con vistas heterogéneas. |
| **Shell geometry stabilization** | ✅ | Body flexible + Header/Footer invariantes; los workspaces ya no desplazan el footer global por su `prefHeight`. |
| **TOOLS Workspace v2** | ✅ | Command Deck estructuralmente cerrado para mockup. |
| **MEDIA Workspace v1 real** | ✅ | TIDAL validado con metadata/artwork/transport GSMTC, Focusrite vía Core Audio y quality/queue vía UI Automation. |
| **FILES Workspace v1** | ✅ | Explorer mock con navegación, búsqueda y selección validado. |
| **SYSTEM Workspace v1** | ✅ | Panel operativo 2×2 con Audio, Network, Displays y Power/Session validado visualmente. |
| **RESEARCH Workspace v1** | ✅ | Surface documental 58/42 con query, brief, findings, visual evidence, sources/context y lifecycle `RESEARCHING → PARTIAL → COMPLETE` validado. |
| **State synchronization** | ✅ | Research lifecycle sincronizado con Header/Core/TTS: `PROCESSING → SPEAKING → IDLE`, con ownership mediante `AssistantVisualStateCoordinator`. |
| **Telemetry v2** | ✅ | Observabilidad reorganizada en System/GPU/Network; `HudMetric` compacto, temperatura normalizada y network activity validado. |
| **Runtime v2** | ✅ | Runtime compacto con Inference/Voice Pipeline, roles inline, pseudo-estados y alineación vertical con Workspace. |
| **Header / Footer v2** | ✅ | Header con estado semántico + fecha/hora jerarquizadas; Footer dividido en tres zonas con centrado geométrico real. |
| **Iconography v1** | ✅ | `HudIcon`/`HudIconView` validados en MEDIA, FILES y SYSTEM; TOOLS conserva monogramas por diseño. |
| **Telemetry real — Host** | ✅ | CPU, RAM y Network integrados con OSHI; polling `LIVE // 1s` validado en UI. |
| **Telemetry real — GPU** | ✅ | GPU utilization, VRAM y temperatura integrados con NVML/JNA sobre RTX 4070. |
| **RealCoreVisualSource** | ✅ | Fuente real consolidada para Telemetry, manteniendo providers desacoplados y `MockCoreVisualSource` como regresión visual. |
| **Core audiovisual real** | ✅ | MIC real en `LISTENING`, TTS real en `SPEAKING`, RMS/peak/VAD reactivos con smoothing. |
| **Voice Input MUTE** | ✅ | `MUTE // ON` bloquea STT antes de VAD/procesamiento y conmuta Footer a `TOUCH ONLY` sin afectar TTS. |
| **Overlay display policy** | ✅ | Notificaciones excluyen el Command Deck y siguen la ventana foreground entre monitores de trabajo con fallback primary. |
| **INTERACTING real** | ✅ | Interaction Surface visible activa `INTERACTING`; submit/cancel/expire/close liberan el override. |
| **EXECUTING real** | ✅ | Lifecycle de skills/actions conectado mediante `AssistantExecutionLifecycleListener`, incluyendo continuaciones tras Interaction. |
| **DEGRADED real** | ✅ | Fallos reales de runtime/STT/TTS activan `DEGRADED` persistente; validado con TTS forzado a `FAILED`. |
| **Integraciones reales** | 🟡 En curso | Telemetry, Runtime, Core audiovisual, input mute y lifecycle completo de estados cerrados; siguiente bloque: conectar DEV con capacidades reales. |

---

# 18. Orden actualizado de trabajo

## Fase visual / coordinación — cerrada

1. ✅ Layout general
2. ✅ HudBackground
3. ✅ AresCoreView v2
4. ✅ Workspace architecture
5. ✅ TOOLS Workspace real v1
6. ✅ MEDIA Workspace real v1
7. ✅ FILES Workspace mock
8. ✅ SYSTEM Workspace mock
9. ✅ RESEARCH Workspace mock
10. ✅ Shell geometry stabilization
11. ✅ Research lifecycle mock → Core/Header/TTS
12. ✅ AssistantVisualStateCoordinator
13. ✅ Telemetry v2
14. ✅ Runtime v2
15. ✅ Left column / Workspace alignment
16. ✅ Header/Footer v2
17. ✅ Iconography v1

## Segunda fase — infraestructura/lifecycle real

18. ✅ Telemetry real
19. ✅ Runtime real
20. ✅ Core Visual integrado en `Main`
21. ✅ Audio/VAD → Core state
22. ✅ Waveform real `LISTENING`
23. ✅ RMS / peak / VAD reactivos + smoothing
24. ✅ Waveform real `SPEAKING`
25. ✅ Overlay display policy / Command Deck excluido de notificaciones
26. ✅ Voice Input MUTE / `TOUCH ONLY`
27. ✅ Interaction lifecycle → `INTERACTING`
28. ✅ Execution lifecycle → `EXECUTING`
29. ✅ Runtime degradation → `DEGRADED`

## Tercera fase — capacidades reales de workspaces

30. ✅ TOOLS → catálogo JSON / ApplicationRegistry / OS Skill / acciones táctiles reales
31. ✅ More Tools → Interaction Surface paginada
32. ✅ MEDIA → GSMTC / artwork / transport real
33. ✅ MEDIA → output device mediante Core Audio
34. ✅ MEDIA → TIDAL quality + queue mediante UI Automation
35. ⏳ MEDIA hardening → music-session filtering/pinning (TIDAL/Spotify)
36. ⏸ MEDIA volume/level → investigación separada Exclusive Mode / Focusrite
37. ⏳ Filesystem real
38. ⏳ System providers
39. ⏳ Research pipeline real
40. ⏳ TTS delivery / `ResearchResult` coordination
41. ⏳ Workspace navigation externa/contextual
42. ⏸ Passive Interaction mirror notification en 1440p
43. ⏳ Dynamic touch keyboard para `TOUCH ONLY`

## Evolución futura — model runtime

44. ⏳ Orquestador de modelos on-demand por capacidad
45. ⏳ Residency dinámica RAM/VRAM y políticas de unload/reload
46. ⏳ `DEGRADED` capability-aware: sólo ante fallo de load/reload/ejecución requerida
47. ⏳ Integración escalonada de modelos especializados (Qwen, Flux, MedGemma, Gemma y futuros modelos)

## Fase final

48. Output routing/contextual integration
49. Fullscreen / taskbar
50. Multi-monitor hardening / hotplug
51. Startup productivo Windows
52. Optimización CPU/RAM/GPU
53. Refactor/cleanup final

---
# 19. Estado resumido

```text
LAYOUT                      ✅
HUD BACKGROUND              ✅
ARES CORE V2                ✅
WORKSPACE SHELL             ✅
SHELL GEOMETRY              ✅

TOOLS WORKSPACE REAL         ✅
MORE TOOLS PAGINADO          ✅
MEDIA WORKSPACE REAL V1      ✅
FILES WORKSPACE MOCK         ✅
SYSTEM WORKSPACE MOCK        ✅
RESEARCH WORKSPACE MOCK      ✅

STATE COORDINATOR            ✅
REAL TELEMETRY               ✅
REAL RUNTIME                 ✅
CORE VISUAL IN MAIN          ✅

LISTENING REAL               ✅
PROCESSING REAL              ✅
SPEAKING REAL                ✅
INTERACTING REAL             ✅
EXECUTING REAL               ✅
DEGRADED REAL                ✅
IDLE REAL                    ✅

MIC WAVEFORM REAL            ✅
TTS WAVEFORM REAL            ✅
RMS / PEAK / VAD REACTIVE    ✅

VOICE INPUT MUTE             ✅
TOUCH ONLY MODE              ✅
OVERLAY DISPLAY POLICY       ✅
COMMAND DECK EXCLUDED        ✅

MEDIA GSMTC                  ✅
MEDIA ARTWORK                ✅
MEDIA TRANSPORT              ✅
MEDIA OUTPUT DEVICE          ✅
TIDAL QUALITY                ✅
TIDAL QUEUE / NEXT           ✅
MUSIC SESSION PINNING        ⏳
MEDIA VOLUME / LEVEL         ⏸ R&D

FILESYSTEM INTEGRATION       ⏳
SYSTEM INTEGRATION           ⏳
RESEARCH INTEGRATION         ⏳

DYNAMIC TOUCH KEYBOARD       ⏳
PASSIVE INTERACTION MIRROR   ⏸ DEFERRED

FULLSCREEN FINAL             ⏸
WINDOWS STARTUP FINAL        ⏸
OPTIMIZATION / REFACTOR      ⏸
```

## Próximos pasos

### 1. MEDIA hardening — selección exclusiva de música

Registrar como track posterior, sin bloquear el cierre v1:

```text
Windows active session
      ↓
MediaSessionSelectionPolicy
      ↓
permitidos:
  TIDAL
  Spotify
      ↓
selected music session
```

Objetivo: impedir que WhatsApp, sonidos de notificación o videos tomen el workspace cuando TIDAL queda pausado.

### 2. Filesystem real

Reemplazar el filesystem mock por controller/snapshots async sobre `java.nio.file`, conservando breadcrumb, search y selection context existentes.

### 3. SYSTEM real

Conectar Audio / Network / Displays / Power mediante providers Windows, reutilizando Telemetry cuando represente actividad y evitando duplicarla dentro de SYSTEM.

### 4. RESEARCH real

Reemplazar lifecycle/documentos mock por `ResearchResult` real, fuentes/citas/imágenes y navegación contextual.

### 5. MEDIA volume / level — investigación separada

No bloquear el roadmap principal con este punto.

Investigar, en orden:

```text
IAudioMeterInformation
↓
hardware meter disponible?
↓
peak / dBFS

si no:
Focusrite driver / vendor telemetry
↓
evaluar si existe una API limpia y estable
```

Evitar llamar “VOLUME” a una métrica de peak/level.

### Pendientes diferidos

```text
Dynamic touch keyboard
→ requerido más adelante para FREE_TEXT en TOUCH ONLY

Passive Interaction mirror notification
→ aviso pasivo en monitor 1440p mientras la Interaction completa permanece en Command Deck
→ no bloqueante por ahora
```

### Cierre de plataforma

```text
output routing contextual
fullscreen/taskbar
multi-monitor hardening
startup productivo Windows
optimización CPU/RAM/GPU
refactor/cleanup final
```

---
