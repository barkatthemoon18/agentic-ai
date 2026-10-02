# Plan de pruebas y revisión de dev

## Alcance y evolución revisada

Revisión del historial de `dev` desde el inicio del proyecto hasta `042ab15` (2 de octubre de 2026),
con énfasis en los cambios recientes y en los tests que conservaban contratos anteriores. La revisión inicial
amplió tests y documentación. El seguimiento valida los fixes de producción de presentación, señales de playback,
balance COM y fallback de Tools, y añade las regresiones correspondientes. El provider de audio permite inyectar
Ole32 y simular la lectura del snapshot mediante miembros de paquete, conservando el constructor público existente.

| Periodo | Cambios contrastados con la suite | Resultado de la revisión |
|---|---|---|
| Agosto–5 septiembre | Activación, conversación, routing, skills, políticas de audio, VAD/STT/TTS y protocolos | Se conserva la cobertura válida de reglas, sesión y adaptadores; se amplían contratos de workers y playback. |
| 6–13 septiembre | Clasificadores locales, Qwen/GPT, research, selección de backend, catálogo y runtime OS | Se conservan tests de routing, clasificación y catálogo; se añaden contratos deterministas de wake/router, solicitudes GPT y research con contexto. |
| 14–16 septiembre | Carga de modelos, selección de aplicaciones, fonética, pantallas y foco | Se conserva la cobertura de startup/reintentos, identidad de procesos, aliases, geometría y soporte de ventanas; se refuerza el lifecycle de interacción. |
| 18–24 septiembre | Core visual, telemetría, señales STT/TTS, mute de entrada, ejecución/interacción y degradación | Se añaden pruebas de prioridades visuales, runtime, publicación de telemetría, fallbacks, mute, señales y ejecuciones asíncronas. |
| 27 septiembre–2 octubre | Reconocimiento de nombres, Tools, sesiones Media, dispositivo/volumen, Tidal y playback | Se corrige el doble de playback obsoleto, se elimina la dependencia de Tidal real y se cubren snapshots, composición, transporte, paginación y acciones directas. |

Baseline observado antes de actualizar: **475 tests Java, 15 fallidos; 10 tests Python correctos**.
Dos fallos Java procedían del doble que sobrescribía `play(device, audio, gain)` mientras producción
llamaba a `play(device, audio, gain, listener)`. Los otros trece coincidían con la reserva de audio
detectada mediante los procesos reales de Tidal: las respuestas se redirigían a pantalla.
El antiguo test de ganancia silenciada podía pasar sin interceptar playback; ahora exige una llamada capturada.

La revisión inicial terminó con **659 tests Java y 20 tests Python correctos**.
Validación final del seguimiento: **706 tests Java y 20 tests Python correctos**, sin fallos, errores ni tests omitidos.
Las cuatro clases afectadas ejecutan **102 casos**, incluidos **47 casos de regresión nuevos**.
Las evaluaciones etiquetadas de modelos reales permanecen fuera de la suite predeterminada.

## Cobertura automatizada

| área | Comportamientos verificados |
|---|---|
| Activación y clasificación | Wake exacto/ambiguo, intención, reglas de contexto, discurso atribuido, navegación de catálogo, etiquetas locales válidas/inválidas, parámetros del modelo y bypass de reglas cuando corresponde. |
| Conversación y routing | Expiración, cierre, tokens por sesión, petición nueva frente a follow-up, escalado general→research, políticas `PRESERVE`/`KEEP_OPEN` y selección de backend. |
| GPT y research | Entrada/instrucciones/budget, concatenación de bloques de texto, rechazo de salida vacía, propagación de error, token explícito sin estado heredado y búsqueda QUICK/DEEP; historial visible cuando falta un token usable. |
| OS | Catálogo, aliases y colisiones, identidad fuerte, observaciones WMI, firmas de ventanas/command line, operaciones con dobles, selección ambigua, reintento de parser y acciones de workspace sin pasar por el parser. |
| Ejecución e interacción | UUID/capability de inicio y completado, errores síncronos y asíncronos, suspensión por interacción, nuevo lifecycle al continuar, timeout manual, callbacks tardíos, cierre e independencia ante fallos de listeners. |
| Speech asíncrono | Reanudación en el executor del asistente, liberación de audio mientras hay un turno suspendido, rechazo de nuevos turnos/STT mientras está pendiente, cancelación/error de interacción, recuperación y rechazo después del cierre. |
| Voz y playback | Pre-roll y umbrales VAD, mute/unmute, descarte de audio incompleto, actividad sin sobreescribir procesamiento, RMS/peak/probabilidad, copia de muestras, una señal completa por chunk no vacío con métricas independientes, silencio final, guard y limpieza de estado ante error. |
| Java Sound simulado | Conversión PCM16 little-endian, clipping/ganancia, límites de chunks 0/1/512/513/1025, muestras visuales antes de ganancia, orden open/start/write/drain/stop/close y fallo al obtener la línea. |
| Presentación | Matriz de volumen normal/bajo/cero/mute y listas de 0/1/5/6 aplicaciones, reserva de salida con texto/catálogo/listas, payload e indicador `OUTPUT_RESERVED` conservados, recuperación al liberarse, mute/cero sin consultar reserva y fallos visuales sin audio no autorizado. |
| Media | Defaults, normalización de nombre, posición y metadata, cola/artistas inmutables, volumen desconocido frente a cero, playback activo distinto del específico de Tidal, composición de proveedores, fallo independiente y recuperación, transporte y cierre. |
| Sesiones Windows | Interfaz multimedia simulada: metadatos y segundos fraccionales, falta de track/sesión, nombres normalizados, prioridad PLAYING→UNKNOWN→PAUSED→STOPPED entre sesiones y delegación a controles. |
| Lifecycle COM de salida | Ole32 simulado: una desinicialización por inicialización exitosa (`S_OK`/`S_FALSE`), incluso ante error de lectura o LinkageError; inicialización fallida sin lectura ni desinicialización y balance independiente por polling. La lectura nativa de dispositivos se sustituye con un spy. |
| Reserva de Tidal | Procesos simulados: ausencia/command no observable, helper que no coincide, mayúsculas, PLAYING/UNKNOWN/estado ausente reservado, PAUSED/STOPPED disponible y cambios sin caché. |
| Core y telemetría | Prioridad INTERACTION→TTS→EXECUTION→RESEARCH→VOICE→base, restauración y deduplicación, degradación solo en IDLE, mapping del panel runtime, recuperación de dependencias, datos host/GPU/red y fallbacks ante excepción/LinkageError. |
| Tools | Configuración válida/inválida/ausente y JSON literal `null` con fallback vacío, referencias y duplicados, featured en orden, restantes sin featured, configuración real parseada sin fallback, paginación, cancelación, selección por resolverTarget y handlers diferidos. |
| Workers Python | Parámetros actuales de Whisper en español y nombres de aplicaciones, segmentos vacíos, muestras big-endian, sample count inválido/truncado, longitud UTF-8, trim de TTS, ping/shutdown y recuperación tras errores de solicitud. |
| Recursos | Orden explícito de cierre actualizado para Media/Core, startup parcial, transferencia de ownership y continuidad ante fallos/interrupción. |

Los tests usan Mockito, dobles y servidores HTTP locales ya existentes. Los mocks estáticos de `ProcessHandle`
y `AudioSystem` se cierran en cada caso. Los tests Python sustituyen las importaciones de inference por dobles;
solo necesitan NumPy además de la biblioteca estándar, sin descargar modelos ni iniciar workers pesados.

Las pruebas asíncronas utilizan futuros, colas y latches con timeouts. Donde el scheduler de producción no es
inyectable, se espera el estado publicado con polling acotado a tres segundos, sin esperas largas arbitrarias.
Los recursos y executors creados por los tests se cierran al finalizar cada caso.

## Límites e integraciones pendientes

- **Modelos reales:** los corpus y benchmarks existentes llevan `@Tag("model-evaluation")` y están excluidos
  por defecto. Los tests unitarios verifican contratos; no demuestran precisión de inferencia ni de reconocimiento
  de voz real. Esta revisión no ejecuta inferencia contra LM Studio o OpenAI.
- **Java/Python entre procesos:** los clientes Java mantienen pruebas de delegación/precondiciones y los workers
  tienen pruebas de protocolo. Queda pendiente una integración con un worker falso para startup, ping, correlación
  de request ID, headers inválidos, timeout, EOF y shutdown desde el cliente Java.
- **Windows nativo:** COM/Core Audio, UI Automation de Tidal, OSHI y NVML reales requieren integración aparte.
  Los tests de sesión multimedia y telemetría actuales usan interfaces simuladas y no prueban las bibliotecas nativas.
- **UI:** las pruebas existentes de geometría, foco/estilos nativos y helpers JavaFX no equivalen a recorrer las vistas
  reales. Files/System y partes de Research/Dev todavía contienen datos o composición visual que requieren inspección manual.
- **Audio/ONNX:** falta validar captura, selección de dispositivos, canal derecho, Silero real, latencia y prevención
  de autoactivación con el equipo objetivo. Java Sound está simulado en las pruebas de reproducción.
- **Main:** no se inicia la aplicación completa en los tests. El cierre se prueba mediante `ResourceCleanup`,
  pero el ensamblaje de dependencias, los recursos externos y el flujo completo requieren un smoke test manual.

### Verificación manual con entorno preparado

1. Iniciar Ares con los modelos, workers y dispositivos configurados; comprobar panel runtime y degradación/recuperación.
2. Validar audio→VAD→STT→routing→skill→presentación; confirmar que TTS no vuelve a activar el asistente.
3. Silenciar y reactivar STT durante una frase; verificar que no se reenvía el segmento incompleto.
4. Abrir una aplicación desde Tools, paginar More Tools y cancelar/seleccionar un objetivo ambiguo con touch y voz.
5. Comprobar Media con sesiones ausentes y presentes, track/posición, cola/calidad y salida con volumen conocido o desconocido.
6. Cambiar Tidal entre PLAYING/PAUSED/STOPPED y comprobar la decisión audio/pantalla; incluir catálogo y listas de aplicaciones para contrastar las regresiones automatizadas con el entorno real.
7. Revisar dashboards, overlays, selección de pantalla, foco y estado Research/TTS; cerrar y verificar la liberación de recursos.

## Fixes revisados y regresiones automatizadas

Los cuatro hallazgos de la revisión inicial están corregidos y cubiertos por tests activos. El fallback del loader
ya estaba completado al iniciar la implementación del seguimiento; se conservó y se ajustó su diagnóstico de JSON `null`.

| Hallazgo original | Corrección validada | Regresión automatizada |
|---|---|---|
| Catálogo/listas podían llamar a TTS antes de consultar reserva. | Resolver la política y la reserva antes de ambas ramas, conservando payload y aviso visual. | 35 casos nuevos: catálogo y listas 0/1/5/6 con volúmenes 19/20/40 reservados, mute/cero, fallo visual y recuperación. |
| Un chunk de N muestras publicaba N snapshots con métricas parciales. | Calcular RMS/peak del chunk completo y publicar una vez después del bucle. | 3 casos nuevos: cuatro muestras con RMS `sqrt(0.21875)`/peak `0.75`, múltiples chunks con métricas independientes y chunk vacío; silencio final en todos. |
| La inicialización COM exitosa tenía dos llamadas a `CoUninitialize`. | Una única desinicialización en el cleanup de `current`, después de liberar las interfaces de la lectura. | 8 casos nuevos: `S_OK`/`S_FALSE` en éxito, RuntimeException y LinkageError; inicialización fallida y dos polls independientes. |
| El JSON literal `null` devolvía una configuración nula. | Devolver `ToolsWorkspaceConfig.empty()` e informar configuración inválida. | 1 caso nuevo: archivo temporal con `null` que exige igualdad con el workspace vacío. |

## Ejecución

### Modernización Java

La migración de 26 objetos de datos a records elimina Lombok y actualiza sus consumidores.
OutputPresentationPolicy permanece como clase final. Los buffers de audio conservan su referencia
sin copias defensivas. Validación limpia: **709 tests Java y 20 Python**, incluyendo contratos de
buffer, campos obligatorios de AssistantRequest y contextAvailable ausente en JSON.
La base incluye dos estabilizaciones de tests asíncronos: esperar el estado agregado de modelos y
terminar la configuración de mocks de Media antes de ejecutar acciones. No cambian producción.

Java con Maven y **JDK 21**, verificando también que `JAVA_HOME` apunte a ese JDK:

```powershell
mvn test
```

En el equipo revisado Maven no está en PATH. Se usó la instalación incluida en IntelliJ:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot'
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd' test
```

Python:

```powershell
python -m unittest discover -s python/tests -v
```

Evaluaciones reales, únicamente con el entorno de modelos preparado:

```powershell
mvn -Pmodel-evaluation test
```

La exclusión implementada en el POM es **`model-evaluation`**. Las etiquetas propuestas `integration` o `hardware`
no tienen una exclusión configurada: añadir solo esas etiquetas a un test no lo separaría de la suite predeterminada.
