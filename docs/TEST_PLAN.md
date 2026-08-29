# Plan de pruebas

## Objetivo

Verificar el flujo completo del asistente de voz sin hacer que la suite rápida dependa de micrófonos, altavoces,
procesos reales, credenciales, LM Studio, modelos ONNX ni workers pesados. La pirámide propuesta contiene:

1. pruebas unitarias deterministas ejecutadas en cada cambio;
2. pruebas de contrato para los protocolos Java/Python;
3. pruebas de integración etiquetadas, ejecutadas en un entorno preparado;
4. una prueba manual de extremo a extremo con el hardware objetivo.

## Cobertura implementada

| Área | Código cubierto | Casos principales |
|---|---|---|
| Activación | `ActivationResult`, `RuleBasedActivationDetector`, `WakeWordMatcher`, `WakeWordMatch`, `CandidateMatch`, `Token` | vacío, coincidencia exacta, puntuación, candidato ambiguo, resoluciones IA, frase de intención y fallback semántico |
| Conversación | `ConversationControlDetector`, `ConversationSession`, `ConversationControl`, `ConversationPolicy` | órdenes explícitas/naturales, normalización, errores STT, falsos positivos, activar, expirar, refrescar y cerrar |
| Routing | `Capability`, `AiSkillRouter`, `SkillRegistry`, `SkillRouter` | parseo externo, valor desconocido, selección de skill y registro incompleto |
| Skills | `Skill`, `GeneralSkill`, `SystemTimeSkill`, `UnsupportedSkill`, `AssistantPipeline`, `AssistantExecutionResult` | request al motor, política, formato de hora, fallback, activación inválida, ejecución y reset |
| Comandos OS | `ApplicationDefinition`, `ApplicationRegistry`, `OsCommandIntent`, `OsCommandSafetyGuard`, `OsCommandSkill` | lookup, copia defensiva, órdenes inmediatas/no inmediatas, intent no soportado, aplicación ausente, abrir/cerrar y excepciones |
| Audio y speech | `AudioFrame`, `SpeechSegment`, `TtsAudio`, `SpeechBuffer`, `BasicSpeechSegmentValidator`, `SpeechValidationResult` | duración, cantidad de muestras, concatenación, buffer vacío, RMS, peak y umbrales |
| Pipelines | `AudioPipeline`, `VoicePipeline`, `SpeechProcessingService` | exclusión, transiciones, guard post-playback, fallos TTS/playback, pre-roll, inicio/fin VAD, contexto, cierre y liberación ante error |
| Adaptadores | `FasterWhisperSttEngine`, `PiperTtsEngine`, precondiciones de ambos clientes | delegación, cierre y operación antes de iniciar worker |
| Workers Python | `whisper_worker.py`, `piper_worker.py` | lectura exacta, EOF, serialización binaria, sample rate, concatenación de audio y respuestas |

Las interfaces (`ActivationDetector`, clasificadores, engines, listeners, parsers y controllers) se ejercitan mediante
dobles que implementan sus contratos. Los DTOs generados por Lombok se cubren al atravesar los flujos que los consumen;
no se duplican pruebas de getters y setters sin lógica.

## Integraciones pendientes o deliberadamente separadas

### OpenAI y LM Studio

Clases: `GptAssistantEngine`, `GraniteSemanticRouter`, `GraniteSemanticActivationClassifier`,
`GraniteContextContinuationClassifier`, `GraniteWakeClassifier` y `GraniteOsCommandParser`.

Casos de contrato recomendados contra un servidor HTTP simulado:

- construir el modelo, instrucciones, entrada, temperatura y límite de tokens correctos;
- aceptar etiquetas válidas con espacios y diferencias de mayúsculas;
- rechazar etiquetas desconocidas;
- rechazar respuestas sin contenido o sin choices;
- propagar errores HTTP, timeout y JSON inválido;
- en `GptAssistantEngine`, concatenar múltiples bloques de texto, encadenar `previousResponseId` y eliminarlo tras reset.

Además debe existir una matriz de evaluación separada con ejemplos positivos, negativos y ambiguos para cada prompt.
No debe ejecutarse como unit test porque la salida de un modelo no es completamente determinista.

### Protocolos de workers

Clases: `FasterWhisperClient` y `PiperClient`.

Casos de integración mediante procesos worker falsos:

- ping, transcripción/síntesis y shutdown exitosos;
- magic, versión o request ID incorrectos;
- estado de error enviado por el worker;
- longitudes negativas, payload truncado y EOF;
- proceso que termina durante startup, timeout y cierre forzado;
- dos solicitudes secuenciales y contadores de request ID;
- UTF-8 en texto, idioma y mensajes de error;
- audio vacío, sample rate inválido y muestras IEEE-754 big-endian.

Los tests Python ya verifican el formato básico de respuesta desde el otro lado del contrato.

### ONNX y VAD

Clase: `SileroVadEngine`.

- cargar el modelo real y cerrar sesión;
- rechazar sample rate distinto de 16 kHz;
- rechazar frames que no tengan 512 muestras;
- probabilidad por debajo, igual y encima del threshold;
- confirmar que `reset()` elimina contexto y estado entre conversaciones;
- ejecutar varios frames para detectar incompatibilidades de nombres o shapes del modelo.

### Java Sound y sistema operativo

Clases: `AudioCaptureService`, `AudioPlaybackService`, `AudioDeviceManager` y
`WindowsApplicationController`.

- PCM16 mínimo, máximo, cero, endianness y selección del canal derecho;
- clamp float a PCM16 y cierre del `SourceDataLine` ante éxito o error;
- filtrar mixers sin líneas input/output y búsqueda case-insensitive;
- start/stop idempotente, thread de captura y error `LineUnavailableException`;
- construir el comando de apertura correcto;
- cerrar sólo procesos cuyo nombre coincide, usar cierre forzado cuando sea necesario y omitir procesos sin comando.

Estas pruebas requieren adaptadores inyectables alrededor de `AudioSystem`, `ProcessBuilder` y `ProcessHandle` para ser
unitarias. Hasta entonces deben ejecutarse sólo en una máquina o VM desechable; nunca se debe probar el cierre contra
procesos personales del desarrollador.

### Composición y extremo a extremo

Clase: `Main`.

- smoke test de creación de dependencias con configuración validada;
- error claro cuando falta API key, modelo, Python, LM Studio o dispositivo Focusrite;
- cierre parcial correcto cuando falla cualquier fase de startup;
- flujo E2E: audio -> VAD -> STT -> activación -> routing -> skill -> TTS -> playback;
- impedir que el audio sintetizado vuelva a activar el asistente.

Para automatizarlo conviene extraer la composición de `Main` a una fábrica y parametrizar dispositivos, rutas y URLs.

## Casos de regresión que deberían añadirse al corregir la implementación

- `SpeechProcessingService`: si `submit()` es rechazado después de `beginProcessing()`, liberar siempre el pipeline.
- `SpeechProcessingService`: `PRESERVE` no debe convertirse accidentalmente en `KEEP_OPEN` por un `refresh()` incondicional.
- `OsCommandSafetyGuard`: rechazar expresiones futuras aunque `mañana`, `después` o `luego` no estén al inicio.
- `ConversationControlDetector` y `OsCommandSafetyGuard`: definir formalmente el contrato para entrada `null`.
- `SpeechBuffer`: decidir si frames con sample rates diferentes deben rechazarse.
- objetos de audio: validar sample rate cero o negativo para evitar duraciones infinitas.

## Ejecución

Java, usando Maven con JDK 21:

```powershell
mvn test
```

Python:

```powershell
python -m unittest discover -s python/tests -v
```

Las integraciones futuras deberían etiquetarse (`integration`, `hardware`, `model-eval`) y quedar fuera de la suite
unitaria predeterminada.
