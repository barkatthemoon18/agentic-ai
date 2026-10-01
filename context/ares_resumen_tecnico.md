# Ares — Resumen técnico de arquitectura, lógica y decisiones

> Estado contrastado al 14 de septiembre de 2026. La línea de Research se
> revisó desde `52beb51` hasta el merge `4109320` de `feature/current-research`
> en `dev`; OS Skills se revisó desde `4109320` hasta `7805b4d` en
> `feature/os-skills`.

| Línea de trabajo | Rango revisado | Contenido principal |
|---|---|---|
| `dev` / PR #2 `feature/current-research` | `52beb51..4109320` | Current Research con GPT/Qwen, General local/GPT, ramas conversacionales, escalamiento y evaluación |
| `feature/os-skills` | `4109320..7805b4d` | Catálogo dinámico de Windows, identidad runtime segura, foco/estado/listados y presentación estructurada |
| Supervisor de LM Studio | árbol de trabajo al 13 de septiembre | arranque del daemon y servidor, carga de Phi/Qwen, recuperación y estado JavaFX |
| Superficie interactiva | árbol de trabajo al 14 de septiembre | suspensión de Skills, input táctil/voz, lifecycle de sesión y selección de monitor |

## 1. Objetivo de la iteración

Esta conversación consolidó varias piezas de Ares:

- routing semántico de capacidades;
- `OS_COMMAND` para descubrir, abrir, cerrar, enfocar, consultar y listar aplicaciones;
- protección determinista antes de ejecutar comandos;
- conversación contextual, timeout y cancelación forzada;
- política por skill para mantener o preservar contexto;
- clasificador unificado `NEW_REQUEST` / `FOLLOW_UP` / `OTHER`;
- ownership del contexto por `Capability`;
- implementación inicial completa de `AUDIO_CONTROL`, limitada a la voz del asistente;
- selección implementada entre Qwen local y GPT para `GENERAL` y `CURRENT_RESEARCH`;
- arranque supervisado de LM Studio, con recuperación independiente del daemon,
  servidor HTTP, Phi y Qwen;
- superficie interactiva para suspender y reanudar acciones que necesitan input
  humano, con un primer vertical slice para apertura ambigua de aplicaciones.

Arquitectura general:

```text
Mic
→ VAD
→ STT
→ ConversationControlDetector
→ Activation
→ Capability Router
→ Skill
→ SkillExecution
  ├─ Completed / Async → AssistantResult
  └─ AwaitingInteraction → InteractionService → toque o voz → continuación
→ ConversationPolicy
→ TTS
→ playback
```

---

## 2. Routing semántico por capacidades

Capacidades actuales:

```java
SYSTEM_TIME
AUDIO_CONTROL
OS_COMMAND
CURRENT_RESEARCH
GENERAL
```

`LocalSemanticRouter` decide **qué tipo de tarea** representa la frase. No ejecuta acciones.

Contrato conceptual:

```text
system-time
→ hora, fecha o día actual

audio-control
→ modificación directa del audio

os-command
→ acciones sobre aplicaciones, procesos o recursos locales

current-research
→ información externa que requiere actualidad

general
→ conocimiento general, explicaciones, análisis y conversación
```

### Separación crítica: audio vs estado de una aplicación

En pruebas históricas con el modelo anterior se detectó una asociación de Spotify con audio que producía esta clasificación; este resultado no corresponde a una evaluación de Phi-3.5 Mini Instruct:

```text
"Cierra Spotify"
→ AUDIO_CONTROL  ❌
```

La regla correcta quedó:

```text
si modifica el AUDIO
→ AUDIO_CONTROL

si modifica el ESTADO DE UNA APLICACIÓN
→ OS_COMMAND
```

Ejemplos:

```text
"Cierra Spotify"
→ OS_COMMAND

"Abre Spotify"
→ OS_COMMAND

"Baja el volumen de Spotify"
→ AUDIO_CONTROL

"Silencia Spotify"
→ AUDIO_CONTROL
```

El router clasifica el **dominio** de la solicitud, no si el target está
soportado. Por eso una orden de volumen dirigida a Windows o Spotify continúa
siendo `AUDIO_CONTROL`; `AudioControlSkill` autoriza después únicamente el
scope `ASSISTANT` y rechaza los demás sin alterar el estado de Ares.

Prioridad conceptual:

```text
1. Hora/fecha/día actual
   → SYSTEM_TIME

2. Abrir/cerrar/iniciar/terminar/reiniciar aplicaciones
   → OS_COMMAND

3. Modificar volumen/mute/unmute
   → AUDIO_CONTROL

4. Consultar recursos locales
   → OS_COMMAND

5. Información externa actual
   → CURRENT_RESEARCH

6. Resto
   → GENERAL
```

---

## 3. `OS_COMMAND`: arquitectura segura

La arquitectura inicial descrita en esta sección usaba una definición fija de
Spotify y `ProcessHandle` con `processName`. Esa versión fue reemplazada por OS
Skills v2 en `ce5f59a` y `7805b4d`. La arquitectura vigente es:

```text
OsCommandSkill
    ↓
OsCommandSafetyGuard
    ↓
LocalOsCommandParser
    ↓
ApplicationRegistry
    ↓
ApplicationCatalog ← WindowsApplicationDiscovery + os-applications.json
    ↓
ApplicationRuntimeResolver ← Win32_Process
    ↓
WindowsApplicationController ← JnaWindowService + ProcessHandle
```

Principio:

> El modelo no genera comandos shell arbitrarios. Produce una intención
> estructurada; Java autoriza el target y ejecuta únicamente el comando de
> apertura o la acción derivada del catálogo.

### `ApplicationDefinition` vigente

```java
public class ApplicationDefinition {
    private final String id;
    private final String displayName;
    private final Set<String> aliases;
    private final List<String> openCommand;
    private final ApplicationProcessIdentity processIdentity;
}
```

Ya no existe una whitelist construida manualmente para Spotify en `Main`.
`ApplicationCatalog` descubre aplicaciones lanzables con `Get-StartApps`,
accesos directos del menú Inicio y paquetes AppX. Después aplica overrides desde
`config/os-applications.json`. El AppID, el comando de apertura y la identidad
runtime nunca proceden del modelo.

```text
Get-StartApps / shortcuts / AppX
→ ApplicationDefinition
→ aliases y evidencia runtime configurada
→ ApplicationCatalog
→ ApplicationRegistry
```

### Parser

Salida restringida vigente:

```text
open_application|target
close_application|target
focus_application|target
list_applications|filter_or_none
list_running_applications|none
check_application_installed|target
get_application_status|target
unsupported|unknown
```

El parser exige una sola línea. Una primera salida vacía, multilínea o fuera de
contrato provoca un retry con instrucciones de corrección; un segundo fallo se
convierte en `InvalidOsCommandOutputException`, se contiene dentro de
`OsCommandSkill` y no ejecuta acciones.

### Apertura

```java
new ProcessBuilder(
        applicationDefinition.getOpenCommand()
).start();
```

### Cierre

El cierre ya no se autoriza por una coincidencia simple de nombre. WMI captura
PID, `ParentProcessId`, `CreationDate`, ruta ejecutable, nombre y command line en
un mismo snapshot. El nombre sólo localiza candidatos; la identidad se demuestra
mediante ruta, package root, argumentos exclusivos o una excepción explícita de
configuración.

Antes de enfocar una ventana, publicar `WM_CLOSE` o terminar un proceso se
vuelven a comprobar PID, `CreationDate` e identidad. `ProcessHandle` sólo se usa
para comprobar vida y solicitar terminación de procesos previamente verificados.
Timeout, acceso denegado, error COM o una respuesta WMI incompleta producen
`OBSERVATION_FAILED`; nunca se interpretan como aplicación cerrada.

En procesos host compartidos, las relaciones host y firmas HWND son evidencia
auxiliar opt-in. Foco y cierre operan sólo sobre ventanas revalidadas y nunca
autorizan terminar el proceso host completo.

---

## 4. `OsCommandSafetyGuard`

En pruebas históricas, el modelo anterior mostró tendencia a asociar `cerrar/cerrado` con `CLOSE_APPLICATION` incluso cuando no había una orden. Esta observación no constituye un resultado medido de Phi-3.5 Mini Instruct.

Ejemplos incorrectos del parser:

```text
"Spotify se cerró solo"
→ CLOSE_APPLICATION ❌

"Spotify está cerrado"
→ CLOSE_APPLICATION ❌

"Mañana voy a cerrar Spotify"
→ CLOSE_APPLICATION ❌
```

Se implementó defensa en profundidad:

```text
OS_COMMAND
→ SafetyGuard determinista
→ parser LLM
→ registry
→ controller
```

### Bug encontrado en regex

Incorrecto:

```java
Pattern.compile("\\\\b(...)\\\\b");
```

Correcto en Java:

```java
Pattern.compile("\\b(...)\\b");
```

Igualmente:

```java
"\\s+"
```

en el source Java, no un doble escape adicional.

### Resultado validado

```text
Abre Spotify                  → ALLOW
Cierra Spotify                → ALLOW
¿Puedes cerrar Spotify?       → ALLOW
Quiero que cierres Spotify    → ALLOW

Spotify se está cerrando solo → REJECT
Spotify se cerró solo         → REJECT
Spotify está cerrado          → REJECT
Mañana voy a cerrar Spotify   → REJECT
Después voy a cerrar Spotify  → REJECT
Más tarde cerraré Spotify     → REJECT
Mañana voy a abrir Spotify    → REJECT
Ayer abrí Spotify             → REJECT
```

---

## 5. Validación end-to-end histórica de Spotify

Esta validación corresponde a la primera implementación fija. Sigue siendo
evidencia del flujo de routing y seguridad, pero no describe el catálogo ni la
identidad runtime vigentes.

Apertura:

```text
STT: ¡Abre Spotify!
ACTIVATION AI -> ACTIVATE
CAPABILITY -> OS_COMMAND
SKILL -> OsCommandSkill
ASSISTANT: Abriendo: Spotify.
```

Cierre:

```text
STT: Cierra Spotify
ACTIVATION AI -> ACTIVATE
CAPABILITY -> OS_COMMAND
SKILL -> OsCommandSkill
ASSISTANT: Cerrando: Spotify.
```

Esto validó:

```text
STT
→ Activation
→ Capability
→ SafetyGuard
→ parser
→ registry
→ controller
```

---

## 6. Problema contextual: cualquier skill abría 30 segundos

Antes se hacía:

```java
conversationSession.activate();

AssistantResult response =
        assistantPipeline.process(activationResult);

audioPipeline.speak(response.getText());

conversationSession.refresh();
```

Por lo tanto incluso:

```text
"Abre Spotify"
```

dejaba una sesión contextual activa.

Consecuencia:

```text
Abre Spotify
→ sesión activa

"que era eso"
→ CONTEXT AI -> CONTINUE
→ GENERAL
```

aunque GPT no tuviera contexto útil sobre la acción local.

---

## 7. `ConversationPolicy`

Se introdujo una política por skill:

```java
public enum ConversationPolicy {
    KEEP_OPEN,
    PRESERVE
}
```

`Skill`:

```java
public interface Skill {

    AssistantResult execute(String command);

    default ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.PRESERVE;
    }
}
```

Política:

```text
GENERAL
→ KEEP_OPEN

CURRENT_RESEARCH
→ KEEP_OPEN

OS_COMMAND
→ PRESERVE por defecto
→ KEEP_OPEN sólo al devolver un catálogo navegable

AUDIO_CONTROL
→ PRESERVE

SYSTEM_TIME
→ PRESERVE
```

`PRESERVE` significa:

```text
si no había conversación
→ no crea una

si ya había conversación
→ no la destruye

tampoco extiende el timeout
```

Ejemplo:

```text
Ares, explícame RSA
→ GENERAL
→ contexto activo

Abre Spotify
→ OS_COMMAND
→ PRESERVE

¿Y para qué se usa?
→ CONTEXTUAL
→ continúa RSA
```

---

## 8. Cancelación forzada de conversación

Se diseñó un kill-switch lógico que debe ejecutarse antes del routing LLM:

```text
STT
→ ConversationControlDetector
→ ActivationDetector
```

Comandos:

```text
Olvídalo
Nada, olvídalo
Cancela conversación
Cancelar conversación
Termina conversación
Cierra conversación
Eso es todo
```

Acción:

```java
conversationSession.close();
audioPipeline.speak("Conversación terminada.");
```

El token de continuación pertenece al `ConversationSnapshot`. Al cerrar la
sesión desaparece el snapshot y, con él, el único estado conversacional; el
pipeline y el engine no necesitan un reset adicional.

La cancelación debe modificar el estado real, evitando:

```text
"cancelar conversación"
→ GENERAL
→ GPT responde "Conversación cancelada"
→ sesión sigue activa ❌
```

---

## 9. Fuzzy matching para errores de Whisper

Whisper produjo variantes como:

```text
"Olígalo"
"Oligalo"
```

Se permitió fuzzy matching sólo para frases cortas de cancelación.

La similitud Levenshtein normalizada aproximada:

```text
oligalo
vs
olvidalo
≈ 0.75
```

Threshold usado conceptualmente:

```java
private static final double FORGET_THRESHOLD = 0.72;
```

Resultados:

```text
STT: nada, olvídalo
CONVERSATION -> FORCE CLOSE
```

```text
STT: Oligalo.
CONVERSATION -> FORCE CLOSE
```

```text
STT: cancelar conversación
CONVERSATION -> FORCE CLOSE
```

No se usa fuzzy general para ejecutar comandos sensibles.

---

## 10. `SEMANTIC_INTENT` vs `CONTEXTUAL`

Contrato deseado:

```text
SEMANTIC_INTENT
= solicitud que puede entenderse por sí sola

CONTEXTUAL
= solicitud que necesita información de turnos anteriores
```

Ejemplos autocontenidos:

```text
¿Quién fue Alan Turing?
→ SEMANTIC_INTENT

¿Por qué Spotify consume tanta memoria?
→ SEMANTIC_INTENT

¿En qué año murió Turing?
→ SEMANTIC_INTENT
```

Ejemplos contextuales:

```text
¿Y cuándo murió?
→ CONTEXTUAL

¿Por qué pasó eso?
→ CONTEXTUAL

¿Y por qué?
→ CONTEXTUAL

Explícame eso mejor
→ CONTEXTUAL

Dame otro ejemplo
→ CONTEXTUAL
```

---

## 11. Clasificación unificada de utterances

Los clasificadores separados de activación semántica y continuación contextual se sustituyeron por un único contrato:

```text
NEW_REQUEST
→ petición autocontenida; utiliza routing semántico normal

FOLLOW_UP
→ continuación dependiente del contexto; utiliza el owner de la sesión

OTHER
→ ruido, comentario ambiental o texto no dirigido al asistente
```

`RuleBasedActivationDetector` conserva la precedencia para wake word y frases explícitas. Sólo cuando no hay activación determinista se invoca `UtteranceClassifier`.

La orquestación posterior a cada decisión está cubierta por tests deterministas. La precisión lingüística real de Phi-3.5 Mini Instruct se evalúa por separado contra corpus estables de desarrollo y holdout.

### 11.1. Corpus JSONL de evaluación

Se incorporaron dos corpus bajo recursos de test:

```text
evaluation/utterance-development.jsonl
→ ajuste iterativo del prompt y diagnóstico de errores

evaluation/utterance-holdout.jsonl
→ validación final sin contaminar el ajuste
```

Cada archivo contiene 36 casos con una distribución balanceada:

```text
NEW_REQUEST  12
FOLLOW_UP    12
OTHER        12
Total        36
```

Los casos convierten en datos las condiciones documentadas del clasificador:

```text
NEW_REQUEST
→ pregunta o acción autocontenida
→ sigue siendo nueva aunque exista contexto si se entiende por sí sola
→ incluye SYSTEM_TIME, OS_COMMAND, AUDIO_CONTROL, CURRENT_RESEARCH y GENERAL

FOLLOW_UP
→ siempre requiere contexto disponible
→ depende de una referencia, elipsis o pronombre del turno anterior
→ puede pedir aclaración, expansión, reformulación, corrección o verificación
→ debe ser compatible con el tema previo

OTHER
→ comentario ambiental o afirmación sin solicitud
→ descripción de estado, evento pasado o plan futuro
→ habla reportada o instrucción atribuida a un tercero
→ referencia dependiente cuando no existe contexto
→ continuación incompatible con el tema anterior
```

Se incluyeron explícitamente las fronteras sensibles observadas durante el desarrollo:

```text
"Cierra Spotify"
→ NEW_REQUEST

"Spotify se cerró solo"
→ OTHER

"Mañana voy a cerrar Spotify"
→ OTHER

"¿En qué año murió Alan Turing?" con otro contexto activo
→ NEW_REQUEST

"¿Y cuándo murió?" con contexto sobre Alan Turing
→ FOLLOW_UP

"¿Y cuándo murió?" con contexto sobre RSA
→ OTHER
```

Los IDs son únicos dentro de cada archivo. Todos los casos incluyen `tags` y `rationale`; entre 15 y 16 casos por corpus llevan el tag `critical` para exigir exactitud del 100 % en las fronteras de mayor riesgo.

El contrato estructural validado es:

```text
si contextAvailable = false
→ previousUserText, previousAssistantText y owner deben omitirse

si contextAvailable = true
→ previousUserText, previousAssistantText y owner son obligatorios

si expected = follow_up
→ contextAvailable debe ser true
```

La suite normal carga los recursos pero excluye las llamadas reales al modelo. La evaluación con Phi-3.5 Mini Instruct continúa aislada detrás del perfil `model-evaluation` y genera accuracy, macro-F1, métricas por etiqueta, matriz de confusión, falsas activaciones y latencias.

---

## 12. Semántica híbrida — propuesta histórica implementada

Se propuso:

```text
Utterance
   ↓
UtteranceShapeDetector
   ├─ SELF_CONTAINED_REQUEST
   ├─ CONTEXT_DEPENDENT
   └─ UNKNOWN
          ↓
        Phi-3.5 Mini Instruct
```

Java resolvería sólo casos de alta confianza.

Ejemplos:

```text
¿Quién fue Alan Turing?
→ SELF_CONTAINED_REQUEST

¿Por qué Spotify consume tanta memoria?
→ SELF_CONTAINED_REQUEST

¿Y por qué?
→ CONTEXT_DEPENDENT

Explícame eso mejor
→ CONTEXT_DEPENDENT
```

Esta sección conserva la propuesta original. Desde el 7 de septiembre,
`LocalUtteranceClassifier` ejecuta `UtteranceShapeDetector` antes del modelo:
Java resuelve los casos de alta confianza y Phi recibe sólo los casos
`UNKNOWN`. La sección 30 contiene la evaluación comparativa del diseño ya
implementado.

---

## 13. Roles actuales y evaluación futura de modelos locales

Phi-3.5 Mini Instruct, servido normalmente como `phi-router`, continúa a cargo
de clasificadores estructurados mediante `ares.local-model`. Qwen es un backend
generativo distinto para respuestas de `GENERAL` y `CURRENT_RESEARCH`, configurado
mediante `ares.local-qwen-model` y `ares.local-qwen-base-url`. Las evaluaciones de
clasificadores permiten sobrescribir Phi mediante `evaluation.model`.

Se propone compararlo con otros modelos locales:

```text
Phi-3.5 Mini Instruct
Gemma 3 1B IT
Qwen3 1.7B
```

Usando:

- mismo prompt;
- mismo corpus;
- temperatura 0;
- varias repeticiones;
- latencia;
- tasa de acierto;
- estabilidad.

Criterio:

```text
si otro modelo pequeño es estable
→ mantener semántica en LLM

si todos fallan de forma similar
→ ajustar las fronteras entre reglas deterministas y modelo
```

---

## 14. Separación local vs GPT — propuesta parcialmente implementada

Se propuso distinguir:

```text
SemanticRouter
→ qué tipo de tarea es

ModelRouter / InferenceRouter
→ qué modelo debe resolverla
```

Posible jerarquía:

```text
LOCAL_FAST
→ modelo pequeño
→ preguntas simples / clasificación

LOCAL_STRONG
→ modelo local mayor
→ razonamiento medio / coding

CLOUD
→ GPT
→ herramientas, actualidad o razonamiento complejo
```

Ejemplos:

```text
¿Qué es RSA?
→ GENERAL
→ LOCAL

Compara RSA-PSS con ECDSA para este diseño
→ GENERAL
→ LOCAL_STRONG o GPT

Analiza esta vulnerabilidad y compárala con técnicas recientes
→ CURRENT_RESEARCH
→ GPT / web
```

Objetivos:

- menor latencia;
- menor coste API;
- reservar GPT para donde agrega más valor.

La separación se concretó en dos selectores de dominio, no en un `ModelRouter`
global. `GeneralSkill` elige entre `QWEN_LOCAL` y `GPT`; `CurrentResearchSkill`
elige entre `QWEN_LOCAL` y `GPT_WEB`, y clasifica por separado la profundidad
`QUICK`/`DEEP`. `SemanticRouter` sigue decidiendo la capability y no el backend.

---

# 15. `AUDIO_CONTROL`: cambio de enfoque por Focusrite

Inicialmente se planteó controlar el volumen del sistema mediante Windows Core Audio.

Se descartó como primera implementación por las particularidades del entorno:

```text
audio digital
    ↓
Windows/shared mixer o aplicación
    ↓
Focusrite DAC
    ↓
ganancia física analógica
    ↓
audífonos/monitores
```

Además:

```text
ASIO
exclusive mode
bit-perfect
direct monitoring
```

pueden saltarse parcial o totalmente el mixer de Windows.

Por ello se decidió implementar primero:

```text
AUDIO_CONTROL
→ volumen de la propia voz de Ares
```

No:

```text
→ volumen global de Windows
→ Focusrite
→ TIDAL exclusive
→ ASIO
```

---

## 16. Arquitectura de volumen del asistente

Ruta de interpretación y autorización:

```text
LocalSemanticRouter
  ↓ AUDIO_CONTROL
LocalAudioControlParser
  ↓ AudioControlIntent
AudioControlSkill
  ↓ autoriza sólo ASSISTANT
AssistantAudioController
```

Ruta de señal:

```text
Piper
  ↓
float[]
  ↓
AssistantAudioController
  ↓
software gain
  ↓
PCM16
  ↓
AudioPlaybackService
  ↓
Focusrite
```

Esto sólo modifica la señal producida por Ares.

---

## 17. `AssistantAudioController`

```java
public class AssistantAudioController {
    private static final int DEFAULT_STEP = 10;
    private int volume = 100;
    private int lastAudibleVolume = volume;
    private boolean muted = false;

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    public synchronized int setVolume(int volume) {
        this.volume = Math.clamp(volume, 0, 100);
        if (this.volume == 0) {
            muted = true;
        } else {
            lastAudibleVolume = this.volume;
            muted = false;
        }
        return this.volume;
    }

    public synchronized int increaseVolume() {
        return increaseVolume(DEFAULT_STEP);
    }

    public synchronized int increaseVolume(int step) {
        requirePositiveStep(step);
        volume = Math.clamp((long) volume + step, 0, 100);
        lastAudibleVolume = volume;
        muted = false;
        return volume;
    }

    public synchronized int decreaseVolume() {
        return decreaseVolume(DEFAULT_STEP);
    }

    public synchronized int decreaseVolume(int step) {
        requirePositiveStep(step);
        volume = Math.clamp((long) volume - step, 0, 100);
        if (volume == 0) {
            muted = true;
        } else {
            lastAudibleVolume = volume;
        }
        return volume;
    }

    public synchronized void mute() {
        muted = true;
    }

    public synchronized int unmute() {
        if (volume == 0) {
            volume = lastAudibleVolume;
        }
        muted = false;
        return volume;
    }

    public synchronized float getGain() {
        return muted ? 0.0f : volume / 100.0f;
    }

    private void requirePositiveStep(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Volume step must be positive");
        }
    }
}
```

Invariantes actuales:

```text
volume siempre está entre 0 y 100
step debe ser estrictamente positivo
volume = 0 implica muted = true
SET_VOLUME > 0 e INCREASE_VOLUME quitan mute
DECREASE_VOLUME conserva mute mientras el resultado sea mayor que 0
unmute desde 0 recupera lastAudibleVolume
```

Las operaciones continúan sincronizadas. La suma y la resta usan `long` antes
de aplicar `Math.clamp`, por lo que tampoco se desbordan con pasos extremos.

---

## 18. Bug histórico de `setVolume` — cerrado

Versión incorrecta:

```java
public synchronized void setVolume(int volume) {
    volume = Math.clamp(volume, 0, 100);

    if (volume > 0) {
        muted = false;
    }
}
```

El parámetro `volume` oculta al atributo.

Correcto:

```java
public synchronized void setVolume(int value) {
    volume = Math.clamp(value, 0, 100);

    if (volume > 0) {
        muted = false;
    }
}
```

o:

```java
public synchronized void setVolume(int volume) {
    this.volume = Math.clamp(volume, 0, 100);

    if (this.volume > 0) {
        muted = false;
    }
}
```

La implementación actual además retorna el volumen efectivo, de modo que el
skill confirma el valor realmente aplicado y no el valor original solicitado.

### Semántica validada

Resultado:

```text
1.0
0.5
0.4
0.0
0.4
```

Interpretación:

```text
100% → 1.0
50%  → 0.5
40%  → 0.4
mute → 0.0
unmute → 0.4
```

`mute()` conserva el volumen previo. Si el volumen se lleva explícitamente a
cero, `lastAudibleVolume` permite que `unmute()` vuelva a un nivel audible.

---

## 19. Aplicación de ganancia al PCM

La ganancia debe aplicarse antes de convertir los samples de Piper a PCM16.

```java
public void play(
        AudioDeviceInfo device,
        TtsAudio audio,
        float gain
)
```

Conversión:

```java
private byte[] floatToPcm16(
        float[] samples,
        float gain
) {
    byte[] pcm =
            new byte[samples.length * 2];

    for (int i = 0; i < samples.length; i++) {

        float sample =
                samples[i] * gain;

        sample = Math.clamp(
                sample,
                -1.0f,
                1.0f
        );

        short value =
                (short) (sample * 32767.0f);

        pcm[i * 2] =
                (byte) (value & 0xFF);

        pcm[i * 2 + 1] =
                (byte) ((value >> 8) & 0xFF);
    }

    return pcm;
}
```

Punto crítico:

```java
float sample = samples[i] * gain;
```

---

## 20. Integración en `AudioPipeline`

`AudioPipeline` debe consumir **la misma instancia** de `AssistantAudioController`.

```java
public AudioPipeline(
        TtsEngine ttsEngine,
        AudioPlaybackService playbackService,
        AudioDeviceInfo outputDevice,
        AssistantAudioController assistantAudioController
) {
    this.ttsEngine = ttsEngine;
    this.playbackService = playbackService;
    this.outputDevice = outputDevice;
    this.assistantAudioController = assistantAudioController;
}
```

Playback:

```java
playbackService.play(
        outputDevice,
        audio,
        assistantAudioController.getGain()
);
```

---

## 21. Integración de volumen y composición — resuelta

La conexión de la ganancia con el playback PCM y el uso de una única instancia
de `AssistantAudioController` quedaron corregidos. `Main` crea el controlador
una vez y lo comparte entre `AudioControlSkill` y `AudioPipeline`:

```java
AssistantAudioController audioController =
        new AssistantAudioController();

AudioControlSkill audioControlSkill =
        new AudioControlSkill(audioControlParser, audioController);

AudioPipeline audioPipeline =
        new AudioPipeline(
                tts,
                playbackService,
                deviceOutFocusrite,
                audioController
        );
```

Las hipótesis siguientes se conservan como registro del diagnóstico realizado.

Hipótesis principales:

### A. La ganancia no llega al playback

Debe existir:

```java
playbackService.play(
        outputDevice,
        audio,
        audioController.getGain()
);
```

y no:

```java
playbackService.play(
        outputDevice,
        audio
);
```

### B. Hay dos instancias distintas del controlador

Incorrecto:

```java
AssistantAudioController testController =
        new AssistantAudioController();

AudioPipeline pipeline =
        new AudioPipeline(
                ...,
                new AssistantAudioController()
        );

testController.setVolume(10);
```

Correcto:

```java
AssistantAudioController audioController =
        new AssistantAudioController();

AudioPipeline pipeline =
        new AudioPipeline(
                tts,
                playbackService,
                deviceOutFocusrite,
                audioController
        );

audioController.setVolume(10);
```

Prueba recomendada:

```java
audioController.setVolume(10);

System.out.println(
        "PLAYBACK GAIN -> " +
        audioController.getGain()
);

audioPipeline.speak(
        "Esta debería sonar mucho más bajo."
);

audioController.setVolume(100);

audioPipeline.speak(
        "Esta debería sonar al volumen normal."
);
```

Esperado:

```text
PLAYBACK GAIN -> 0.1
```

con diferencia audible evidente.

---

# 22. `AudioControlIntent` y contrato estructural

Acciones implementadas:

```java
public enum AudioAction {
    SET_VOLUME,
    INCREASE_VOLUME,
    DECREASE_VOLUME,
    MUTE,
    UNMUTE,
    UNSUPPORTED
}
```

Scopes implementados:

```java
public enum AudioScope {
    ASSISTANT,
    SYSTEM,
    APPLICATION,
    UNKNOWN
}
```

`AudioControlIntent` valida sus invariantes durante la construcción:

```java
@Getter
public class AudioControlIntent {
    private final AudioAction audioAction;
    private final AudioScope audioScope;
    private final Integer value;

    public AudioControlIntent(
            AudioAction audioAction,
            AudioScope audioScope,
            Integer value
    ) {
        this.audioAction = Objects.requireNonNull(audioAction);
        this.audioScope = Objects.requireNonNull(audioScope);
        validateValue(audioAction, value);
        this.value = value;
    }
}
```

Reglas:

```text
SET_VOLUME
→ valor obligatorio entre 0 y 100

INCREASE_VOLUME / DECREASE_VOLUME
→ valor null para el paso por defecto
→ o entero entre 1 y 100

MUTE / UNMUTE / UNSUPPORTED
→ no aceptan valor

acción y scope
→ nunca pueden ser null
```

Esto impide que valores negativos inviertan accidentalmente una operación y
evita que un intent malformado llegue al controlador.

---

## 23. `LocalAudioControlParser`

El parser local usa una salida cerrada de tres campos:

```text
action|scope|value
```

Ejemplos admitidos:

```text
"Pon tu volumen al 40%"
→ set_volume|assistant|40

"Sube tu volumen al 40%"
→ set_volume|assistant|40

"Sube tu volumen en 15%"
→ increase_volume|assistant|15

"Sube tu volumen 15%"
→ increase_volume|assistant|15

"Habla más fuerte"
→ increase_volume|assistant|default

"Silencia tu voz"
→ mute|assistant|none

"Silencia Spotify"
→ mute|application|none

"Baja Windows al 30%"
→ set_volume|system|30
```

La distinción lingüística acordada es:

```text
al / a / hasta N
→ nivel absoluto

en / por / N puntos
→ variación relativa

subir o bajar seguido de N sin preposición
→ variación relativa
```

Java vuelve a validar la respuesta del modelo mediante una allowlist de
acciones, scopes y valores. Una respuesta con campos adicionales, Markdown,
acciones desconocidas, números fuera de rango o combinaciones inválidas se
convierte en:

```text
UNSUPPORTED | UNKNOWN | null
```

---

## 24. `AudioControlSkill`: autorización y ejecución

Flujo:

```text
LocalAudioControlParser
→ AudioControlIntent validado
→ AudioControlSkill
→ autorización de scope
→ AssistantAudioController
```

En V1 sólo se autoriza:

```text
ASSISTANT
→ ejecutar

SYSTEM
→ rechazar sin mutar estado

APPLICATION
→ rechazar sin mutar estado

UNKNOWN
→ rechazar sin mutar estado
```

El orden de validación es importante:

```text
1. UNSUPPORTED
   → respuesta genérica

2. scope distinto de ASSISTANT
   → respuesta específica de alcance

3. acción soportada sobre ASSISTANT
   → ejecución
```

El skill usa la política heredada `ConversationPolicy.PRESERVE`, por lo que un
control de volumen no abre, cierra ni refresca una conversación contextual.

---

## 25. Semántica de `MUTE`, cero y confirmación

`MUTE` se aplica dentro del skill antes de construir la respuesta:

```java
audioController.mute();

return new AssistantResult(
        "Mi voz quedó silenciada."
);
```

Cuando `SpeechProcessingService` entrega esa respuesta al `AudioPipeline`, el
controlador ya devuelve `gain = 0`. La frase queda disponible para logs, pero
la confirmación no es audible.

Ésta es la decisión explícita para V1:

```text
Ares, silencia tu voz
→ silencio inmediato

Ares, pon tu volumen al 0%
→ volume = 0
→ muted = true
→ silencio inmediato
```

`UNMUTE` se ejecuta antes de generar su confirmación. Si el volumen era cero,
recupera `lastAudibleVolume`, por lo que la respuesta sí puede oírse:

```text
Ares, vuelve a hablar
→ restaura el último volumen audible
→ confirmación audible
```

Si en el futuro se desea una confirmación audible antes de silenciar, deberá
introducirse una acción diferida después del playback. No se inyectó
`AudioPipeline` dentro del skill para evitar acoplar dominio y reproducción.

---

# 26. Estado actualizado — 5 de septiembre de 2026

## Validado

```text
Wake word
Clasificación unificada NEW_REQUEST / FOLLOW_UP / OTHER
Precedencia de activación explícita
Context continuation dirigido al owner
SkillRoute con Capability + Skill
Routing directo routeTo(owner)
Transferencia de ownership mediante KEEP_OPEN
Conversation timeout
Conversation kill-switch
Fuzzy cancel para errores de Whisper
ConversationPolicy KEEP_OPEN / PRESERVE
OS_COMMAND Spotify OPEN (implementación inicial)
OS_COMMAND Spotify CLOSE (implementación inicial)
OsCommandSafetyGuard
Application whitelist fija (reemplazada el 11–13 de septiembre por catálogo dinámico)
ProcessHandle por nombre para cierre (reemplazado por identidad WMI revalidada)
AssistantAudioController lógico
mute/unmute con recuperación del último volumen audible
volumen cero implica mute
pasos negativos o cero rechazados
Ganancia aplicada al playback PCM
AudioControlIntent con invariantes validadas
LocalAudioControlParser con salida cerrada
distinción entre nivel absoluto y variación relativa
AudioScope ASSISTANT / SYSTEM / APPLICATION / UNKNOWN
autorización exclusiva de ASSISTANT en AudioControlSkill
SYSTEM y APPLICATION rechazados sin mutar estado
mute inmediato con confirmación silenciosa
AudioControlSkill registrado en SkillRegistry
misma instancia de AssistantAudioController en skill y playback
Token de continuación almacenado exclusivamente en ConversationSession
GptAssistantEngine sin estado conversacional
Cada FOLLOW_UP recibe explícitamente el token de su ConversationSnapshot
Corpus JSONL de desarrollo: 36 casos balanceados
Corpus JSONL de holdout: 36 casos balanceados
Condiciones estructurales de ambos corpus validadas
```

Validación focalizada del ownership y del token:

```text
GptAssistantEngineTest       1/1
AssistantComponentsTest     10/10
ConversationSessionTest      5/5
SpeechProcessingServiceTest 18/18
Total                       34/34
```

Validación completa con JDK 21:

```text
167 tests ejecutados
167 aprobados
0 fallos
0 errores
```

Validación focalizada de audio:

```text
AssistantAudioControllerTest      4/4
AudioControlIntentTest            2/2
LocalAudioControlParserTest     3/3
AudioControlSkillTest             5/5
AudioPipelineTest                 5/5
Total                            19/19
```

## Cerrado en esta iteración

```text
ConversationSession rechaza snapshot null.
ConversationSnapshot exige owner y textos no nulos mediante Lombok @NonNull.
UtteranceClassificationRequest normaliza y valida sus argumentos.
FOLLOW_UP exige un owner activo.
AssistantPipeline quedó sin el bloque comentado obsoleto.
validateActivation ya no retorna un boolean sin uso.
AssistantPipeline ya no contiene ni resetea un AssistantEngine.
GptAssistantEngine sólo conserva la dependencia OpenAIClient.
NEW_REQUEST inicia sin token, aunque exista una sesión anterior.
FOLLOW_UP consume el token de su ConversationSnapshot y KEEP_OPEN lo reemplaza.
Dos sesiones que comparten engine no intercambian sus tokens.
El corpus JSONL tiene DTO, carga validada, métricas, reporte y 72 casos etiquetados.
Development y holdout cubren por separado NEW_REQUEST, FOLLOW_UP y OTHER.
Las fronteras críticas están etiquetadas para exigir exactitud del 100 %.
La evaluación real de Phi-3.5 Mini Instruct se ejecuta sólo con el perfil model-evaluation.
AudioControlIntent rechaza combinaciones inválidas de acción y valor.
El parser transforma salidas desconocidas o fuera de rango en UNSUPPORTED.
AudioControlSkill nunca ejecuta scopes SYSTEM, APPLICATION o UNKNOWN.
Main sustituyó UnsupportedSkill por el AudioControlSkill real.
Las pruebas manuales de volumen fueron retiradas del arranque.
```

## Pendiente cercano — limpieza y robustez

```text
1. Inyectar ConversationControlDetector en lugar de crearlo por segmento.

2. Sustituir System.out/printStackTrace por logging estructurado.

3. Resuelto el 7 de septiembre: onSpeechSegment libera AudioPipeline si submit
   rechaza la tarea después de beginProcessing(), sin propagar el rechazo.

4. Validar dependencias obligatorias en constructores.

5. Medido el 7 de septiembre con el alias phi-router: comparar híbrido y sin
   reglas, tres repeticiones por corpus. Véase el informe de evaluación enlazado abajo.
```

## Funcional pero pendiente de evaluación

```text
LocalUtteranceClassifier
→ contrato unificado implementado
→ orquestación cubierta por tests
→ híbrido: 36/36 en development y holdout, tres repeticiones por corpus
→ medir por separado las reglas y el modelo: los aciertos híbridos no son precisión del LLM

LocalSemanticRouter
→ funcional tras reforzar OS_COMMAND vs AUDIO_CONTROL

LocalAudioControlParser
→ contrato cerrado y validación determinista implementados
→ interpretación lingüística real pendiente de evaluación sistemática
```

## Registro histórico de pendientes

```text
CURRENT_RESEARCH (implementado en el PR #2 de `dev`)
catálogo dinámico y más aplicaciones OS (implementado en `feature/os-skills`)
corpus específico para evaluar LocalAudioControlParser
persistencia del volumen entre ejecuciones
evaluar curva perceptual de ganancia frente a la curva lineal actual
posible soporte futuro para scopes SYSTEM y APPLICATION
comparar Phi-3.5 Mini Instruct / Gemma / Qwen
ampliar reglas del UtteranceShapeDetector híbrido sólo con nueva evidencia
posible ModelRouter global; actualmente la selección vive dentro de cada skill
métricas de coste/latencia por backend
```

---

# 27. Principios arquitectónicos consolidados

```text
1. Java decide lo determinista.
2. Phi-3.5 Mini Instruct interpreta lenguaje, no ejecuta comandos.
3. GPT se reserva para razonamiento/contenido donde aporta valor.
4. Los modelos nunca generan shell arbitrario.
5. Los targets OS deben proceder del catálogo autorizado.
6. La conversación contextual debe administrarse explícitamente.
7. Un skill transaccional no debe abrir contexto innecesariamente.
8. Los kill-switches deben funcionar antes del routing LLM.
9. El audio debe distinguir voz del asistente, sistema y hardware.
10. Focusrite/ASIO/exclusive mode se trata como una ruta separada.
11. El router identifica el dominio; el skill autoriza el scope ejecutable.
12. Una salida LLM malformada debe fallar cerrada sin modificar estado.
```

Arquitectura consolidada:

```text
Mic
 ↓
VAD
 ↓
STT
 ↓
ConversationControlDetector
 ↓
Activation
 ↓
Capability Router
 ↓
Skill
 ├─ Java determinista
 ├─ LLM local
 └─ GPT
 ↓
AssistantResult
 ↓
ConversationPolicy
 ↓
TTS
 ↓
Assistant software gain
 ↓
Playback
 ↓
Focusrite
```

La separación de responsabilidades se mantiene entre:

```text
detección
interpretación
autorización
ejecución
contexto
razonamiento
audio
```

---

# 28. Text-to-UI con JavaFX

Se incorporó una capa de presentación multimodal que selecciona el canal de
salida de Ares según el estado efectivo de su voz:

```text
AssistantResult
→ AssistantOutputCoordinator
→ OutputPresentationPolicy
   ├─ AUDIO_ONLY      (volumen >= 20 y sin mute)
   ├─ AUDIO_AND_TEXT  (volumen entre 1 y 19)
   └─ TEXT_ONLY       (mute o volumen cero)
```

`AssistantAudioSnapshot` captura volumen y mute. La ganancia se deriva de esos
valores y el umbral visual se configura mediante
`AppConfig.TEXT_UI_VOLUME_THRESHOLD`.

`VisualOutput` mantiene la presentación desacoplada de la lógica del asistente.
Las implementaciones actuales son:

```text
ConsoleVisualOutput
→ fallback y diagnóstico sin interfaz gráfica

JavaFxVisualOutput
→ overlay visual temporal, transparente y siempre visible
```

## 28.1. Overlay JavaFX

El overlay reutiliza una única instancia de `Stage` transparente. Se ubica a
24 px de la esquina inferior derecha del monitor donde está el puntero y usa
`visualBounds` para no cubrir la barra de tareas.

```text
ancho                         560 px
alto mínimo                   132 px
alto máximo                   80 % del área visible, acotado por márgenes
cortes diagonales              14 px
entrada                       fade + desplazamiento, 180 ms
salida                        fade + desplazamiento, 140 ms
permanencia                   6 a 30 segundos según longitud
estado                        MUTED o VOL N%
```

La apariencia reside en `src/main/resources/ui/response.css`: fondo azul-negro,
borde cian, brillo exterior moderado, encabezado monoespaciado y scroll vertical
para respuestas extensas.

Toda mutación gráfica se agenda en el JavaFX Application Thread. En
`TEXT_ONLY` no se invoca Piper ni playback. En `AUDIO_AND_TEXT`, la presentación
visual se solicita antes de comenzar el TTS.

## 28.2. Validación

`JavaFxVisualOutputTest` valida sin abrir ventanas:

```text
formato MUTED / VOL N%
cálculo acotado del tiempo visible
geometría de las esquinas recortadas
rechazo de argumentos inválidos
empaquetado del stylesheet
```

`JavaFxVisualOutputDemo` es una prueba manual interactiva ejecutable desde IntelliJ,
con la raíz del proyecto como directorio de trabajo y el entorno Python de Piper.
Requiere la salida `Altavoces` de Focusrite; si no está disponible o Piper falla,
informa el motivo y termina. Comparte el controlador de volumen entre
`AssistantOutputCoordinator` y `AudioPipeline`, con el umbral configurado en Ares.
No utiliza micrófono, STT, modelos de lenguaje ni skills.

El menú permite repetir mute al 40 %, volumen al 10 %, recuperación al 40 %,
cero seguido de unmute, volumen exactamente en el umbral, texto extenso y texto
corto. Cada presentación informa el modo esperado en consola. Las opciones 8 y 9
demoran cinco segundos para comprobar foco/monitor y ocultamiento explícito con
el cursor dentro, respectivamente. La opción 0 o EOF cierra el overlay y Piper;
ambos recursos se liberan también ante errores. La síntesis y reproducción se
ejecutan fuera del hilo JavaFX.

El temporizador se pausa con el cursor dentro del panel y continúa el tiempo
restante al salir. Un mensaje nuevo reinicia la duración y mantiene la pausa si
el cursor sigue dentro. El ocultamiento explícito y el cierre no esperan al cursor.

Validación manual: ejecutar 1 → 2 → 3 para comprobar texto → audio y texto → audio;
4 para recuperar el último volumen audible desde cero; 5 para comprobar el umbral;
6 para leer y usar scroll durante más de treinta segundos con el cursor dentro,
luego retirarlo y comprobar el cierre; 7 para comprobar reducción tras texto largo.
Con 8, volver al editor y escribir antes de aparecer el panel; repetir moviendo
el puntero a cada monitor. Registrar si roba foco. Con 9, volver al panel antes de
los cinco segundos y comprobar que el ocultamiento explícito funciona.

La suite automática no crea ventanas ni depende de una pantalla disponible. La
posición, animación, legibilidad, foco y comportamiento multimonitor se validan
manualmente mediante el demo.

## 28.3. Limitación conocida

JavaFX no garantiza por sí solo que un `Stage` transparente nunca tome foco en
Windows. Si el demo evidencia robo de foco, una iteración posterior deberá
aplicar la bandera nativa `WS_EX_NOACTIVATE`. Esto no modifica la política de
salida ni el coordinador.

## 29. Robustez del ciclo de vida — 7 de septiembre de 2026

`SpeechProcessingService.onSpeechSegment()` captura `RejectedExecutionException`,
libera el estado de audio y registra el segmento descartado sin reintentar ni
propagar el rechazo al hilo de captura. Las tareas aceptadas mantienen su
liberación en el `finally` de `process()`; si el audio ya está ocupado, no se
libera el estado perteneciente a otra tarea.

`Main` protege ahora la inicialización con `ResourceCleanup`, una utilidad interna
que registra los recursos conforme se crean. Intenta los cierres en este orden:
captura → procesador de segmentos → TTS → salida visual → STT → VAD. Cada excepción
identifica el recurso y no impide intentar los cierres siguientes, incluso cuando
el arranque quedó incompleto. El coordinador sustituye el cierre directo de la
salida visual al construirse, evitando cierres duplicados. Las interrupciones de
la espera principal y las excepciones de interrupción durante el cierre conservan
la marca de interrupción del hilo.

Las pruebas deterministas cubren rechazo tras cerrar el executor con estado real
de audio, liberación única, rechazo por audio ocupado, orden de cierre, uno o
varios fallos, arranque parcial, transferencia de ownership visual e interrupción.
Se mantienen los tiempos de espera existentes; no se agregan timeouts a los
workers ni un shutdown hook de la JVM. Estas pruebas no necesitan hardware.

## 30. Evaluación comparativa de utterances — 7 de septiembre de 2026

Se evaluó el alias local `phi-router` con tres repeticiones por variante y corpus.
El clasificador actual ya usa `UtteranceShapeDetector` antes del LLM; la propuesta
híbrida de la sección 12 es un registro histórico, no el estado actual del código.

El híbrido obtuvo 36/36 en development y holdout en cada repetición, sin errores
ni falsas activaciones. Java resolvió 26 casos de development y 15 de holdout.
La variante sin reglas previas obtuvo 27/36 y 25/36, respectivamente. Conserva
la validación de salida y la protección contra FOLLOW_UP sin contexto.

Se probó una revisión más breve del prompt exclusivamente con development:
redujo latencia, pero degradó el híbrido a 33/36. Se descartó y se congeló el
original antes de holdout. No se cambiaron reglas, corpus ni etiquetas.

El evaluador admite `evaluation.mode=hybrid|model-only|both` y
`evaluation.repetitions`, con valores predeterminados `hybrid` y `1`. Guarda
reportes únicos en `target/model-evaluation`, con huellas del prompt/corpus,
resultados por caso, rutas Java/modelo, métricas, latencias y estabilidad.
Los umbrales se aplican a cada repetición híbrida; sin reglas es diagnóstico.

Resultados, límites y ubicación de artefactos:
[Informe de evaluación](utterance_evaluacion_2026-09-07.md).

## 31. Qwen local y aislamiento del proveedor — 9 de septiembre de 2026

La investigación local incorporó `QwenLocalResearchEngine` para responder con
conocimiento interno cuando el usuario pide buscar localmente. La primera
integración usaba `/v1/chat/completions` y limitaba las respuestas rápidas a
500 tokens. Con Qwen 3.5, el modelo consumía ese presupuesto completo en
`reasoning_content`, terminaba con `finish_reason=length` y devolvía `content`
vacío. El modelo estaba cargado y la solicitud HTTP había sido procesada; el
fallo se producía al no existir texto final para Ares.

La comunicación quedó separada por motores de dominio y un cliente compartido:

```text
GeneralSkill → QwenGeneralEngine ───────────────┐
                                                ├→ LocalQwenChatClient → proveedor local de Qwen
CurrentResearchSkill → QwenLocalResearchEngine ┘
```

`QwenLocalResearchEngine` conoce las instrucciones, la consulta y el historial
de investigación. Convierte `ResearchMessage` al contrato neutral del cliente,
solicita la respuesta y agrega el nuevo turno a `ResearchBranchState`. No conoce
HTTP, JSON, endpoints, autenticación ni controles de razonamiento.

`QwenGeneralEngine` realiza la misma adaptación para el historial propio de
`GENERAL`, sin reutilizar los tipos ni el estado de Research. El cliente se movió
al paquete neutral `com.fuad.assistant.local`, de modo que ambos motores comparten
el acceso al proveedor sin acoplar sus políticas conversacionales.

`LocalQwenChatClient` representa el acceso de Ares al modelo Qwen local. Su
contrato recibe un prompt de sistema, mensajes con roles `USER` y `ASSISTANT`,
y el máximo de tokens; devuelve texto o informa un error descriptivo. La
implementación actual adapta ese contrato a LM Studio mediante
`POST /api/v1/chat`, con `reasoning: "off"` y `store: false`. De esta forma Qwen
genera directamente la respuesta apta para voz y el estado conversacional sigue
perteneciendo a Ares. Los bloques de razonamiento nunca se exponen al usuario.

La URL del proveedor se configura con `ares.local-qwen-base-url`, cuyo valor
predeterminado es `http://localhost:1234`. `ares.local-base-url` conserva el
endpoint OpenAI-compatible usado por los clasificadores locales. Cuando Qwen se
sirva con llama.cpp, la adaptación podrá cambiar dentro de
`LocalQwenChatClient` sin modificar `QwenLocalResearchEngine` ni el skill.
El modelo compartido por General y Research se configura con
`ares.local-qwen-model`; `ares.local-research-model` se conserva como alias
compatible.

Las pruebas del cliente usan un servidor HTTP embebido y validan el payload de
LM Studio, la selección exclusiva de bloques `message`, los estados no exitosos
y las respuestas inválidas o vacías. Las pruebas del motor inyectan un cliente
mock y cubren solamente la traducción de mensajes y la actualización inmutable
del historial de investigación.

## 32. General local, ramas de backend y escalamiento a Research — 10 de septiembre de 2026

`GENERAL` selecciona ahora entre Qwen local y GPT sin confundirse con
`CURRENT_RESEARCH`. GPT dentro de `GeneralSkill` continúa usando
`ResearchDepth.NONE`; no dispone de `web_search` y no equivale a GPT Web Research.

### 32.1 Selección inicial de backend

Una conversación nueva sigue este flujo:

```text
GENERAL
  ↓
override explícito Qwen/GPT
  ├─ existe → backend solicitado / EXPLICIT
  └─ no existe
       ↓
clasificador local de complejidad
  ├─ local → Qwen / AUTOMATIC
  ├─ gpt   → GPT / AUTOMATIC
  └─ error → Qwen / AUTOMATIC
```

Los overrides se reconocen mediante lenguaje de preferencia o cambio de modelo,
por ejemplo «usa Qwen», «respóndeme con GPT» o «vuelve a Qwen». Mencionar un
modelo como tema —«¿qué es GPT?»— no constituye un override.

El clasificador usa el modelo ligero configurado para clasificación, devuelve
únicamente `local` o `gpt`, trabaja con temperatura cero y favorece `local` ante
la duda. Sólo se ejecuta al abrir una conversación `GENERAL` sin override.

### 32.2 Estado y ramas independientes

`GeneralConversationState` contiene:

```text
activeBackend
selectionOrigin     AUTOMATIC | EXPLICIT
branches
  ├─ QWEN_LOCAL → historial de GeneralMessage
  └─ GPT        → continuationToken + historial auxiliar
```

Cada respuesta válida actualiza únicamente la rama utilizada y la convierte en
activa. Un cambio explícito a una rama inexistente la inicia con el último
intercambio visible. Si la rama ya existe, se retoma su contexto propio.

La actualización es transaccional: seleccionar o intentar un backend no modifica
el snapshot. `activeBackend`, `selectionOrigin` y la rama se confirman únicamente
cuando ese backend devuelve una respuesta válida.

### 32.3 Resolución de follow-ups

```text
FOLLOW_UP
  ├─ señal determinista de Research
  │    → GENERAL → CURRENT_RESEARCH
  ├─ override explícito Qwen/GPT
  │    → conserva GENERAL
  │    → cambia de rama transaccionalmente
  └─ follow-up normal
       → conserva capability, backend y origen
       → no ejecuta el clasificador de complejidad
```

`ResearchEscalationDetector` respalda la transición de capability con señales
fuertes: buscar en Internet, investigar, pedir fuentes, verificar vigencia,
consultar precios actuales o solicitar noticias recientes. También rechaza
negaciones como «no lo busques en Internet» y menciones conceptuales como
«¿qué fuentes de energía existen?».

Una decisión probabilística del router semántico no puede cambiar por sí sola el
owner durante un follow-up. `AiSkillRouter.routeFollowUp(...)` sólo permite por
ahora la transición contextual `GENERAL → CURRENT_RESEARCH` cuando el detector
determinista la confirma.

Ejemplo:

```text
¿Quién fue Alan Turing?
→ GENERAL / QWEN_LOCAL

Ahora búscalo en Internet y dime qué fuentes encuentras
→ CURRENT_RESEARCH / GPT_WEB
```

Al escalar, `CurrentResearchSkill` crea estado de Research y siembra la rama con
el último intercambio visible. Los tokens y estados de General se ignoran; un
token de General/GPT nunca se interpreta como continuación de GPT Web Research.
Después de una respuesta válida, el owner pasa a ser `CURRENT_RESEARCH`.

### 32.4 Política por ejecución

La política declarada por un skill sigue siendo el valor predeterminado, pero
`AssistantResult` puede sobrescribirla para una ejecución concreta.
`AssistantResult.preserveConversation(text)` entrega una respuesta al usuario con
política efectiva `PRESERVE`.

En este contrato, `PRESERVE` significa:

- no crear un snapshot;
- no reemplazar el snapshot existente;
- no refrescar su timeout;
- no cerrar la conversación.

Esto permite informar que Qwen, solicitado explícitamente, no está disponible
sin perder ni alterar el contexto anterior.

### 32.5 Errores y fallback

`LocalQwenException` clasifica los errores del proveedor local como
`UNAVAILABLE` o `FAILURE`. Conexión, timeout, modelo no cargado y HTTP
502/503/504 representan indisponibilidad. Errores de parsing, contrato, respuesta
inválida o fallos internos representan `FAILURE`.

| Caso | Resultado | Estado conversacional |
|---|---|---|
| Qwen `AUTOMATIC` + `UNAVAILABLE` | fallback a GPT | GPT activo con origen `AUTOMATIC` después de responder |
| Qwen `EXPLICIT` + `UNAVAILABLE` | mensaje específico, sin fallback | `PRESERVE`; snapshot idéntico |
| Qwen + `FAILURE` | propagar y registrar | sin commit parcial |
| Qwen no disponible y GPT fallback falla | propagar el fallo de GPT | snapshot original intacto |

El fallback automático utiliza la rama GPT existente o la siembra desde el
último intercambio visible. El usuario nunca queda marcado como si hubiera pedido
GPT explícitamente cuando el cambio fue una decisión automática del sistema.

### 32.6 Configuración, validación y límite conocido

Qwen para General y Research usa `ares.local-qwen-model`. La propiedad anterior
`ares.local-research-model` permanece como alias compatible. El clasificador de
complejidad continúa usando el modelo ligero configurado mediante
`ares.local-model`.

La suite determinista cubre selección, overrides, ramas, fallback transaccional,
`PRESERVE`, escalamiento y aislamiento entre General y Research. En la validación
registrada el 10 de septiembre se ejecutaron 285 pruebas sin fallos. Existe además
un corpus `model-evaluation` separado para medir el clasificador de complejidad con
el modelo local activo; queda excluido de la suite normal.

Limitación conocida: al volver a una rama existente se retoma su historial sin
incorporar lo conversado posteriormente en la otra rama. Un trabajo futuro podrá
añadir un handoff transitorio formado por el historial de la rama destino, el
último intercambio visible y la consulta actual, sin fusionar permanentemente
ambas ramas.

### 32.7. Cierre del PR #2 en `dev` — 11 de septiembre de 2026

El merge `4109320` incorporó en `dev` la línea completa de
`feature/current-research`, formada por:

```text
707b98b  Current Research con GPT y búsqueda web
22e3980  Current Research con Qwen local
c599c24  General con Qwen/GPT y cliente local compartido
eaff1fd  corpus de decisiones y retry del clasificador General
84c61d5  correcciones de overrides, negaciones y escalamiento
```

El estado resultante separa tres decisiones que no deben mezclarse:

```text
SemanticRouter
→ GENERAL o CURRENT_RESEARCH

GeneralBackendSelector
→ QWEN_LOCAL o GPT

ResearchBackendClassifier + ResearchDepthClassifier
→ QWEN_LOCAL o GPT_WEB
→ QUICK o DEEP
```

`CurrentResearchSkill` conserva ramas independientes para `QWEN_LOCAL` y
`GPT_WEB`. Una señal local explícita elige Qwen; una solicitud de Internet,
fuentes o información vigente elige GPT Web; un follow-up sin señal nueva hereda
el backend activo. Sin señal y sin historial, el backend predeterminado es Qwen.
La profundidad se clasifica aparte: una consulta puntual usa normalmente
`QUICK`, mientras una comparación, cronología o análisis de varias fuentes usa
`DEEP`.

`GeneralSkill` conserva ramas separadas para Qwen y GPT. El backend se actualiza
sólo después de una respuesta válida. Si Qwen fue seleccionado automáticamente y
no está disponible, se intenta GPT con origen `AUTOMATIC`; si Qwen fue pedido de
forma explícita, Ares informa la indisponibilidad con `PRESERVE` y no modifica el
snapshot. Errores locales de contrato o respuesta inválida no activan fallback.

El clasificador de complejidad General quedó detrás de
`GeneralBackendInference`. Acepta únicamente `local` o `gpt`, registra si la
clasificación se obtuvo en el primer o segundo intento y corrige una única vez
una salida inválida. Si el retry también falla, lanza
`InvalidGeneralBackendOutputException`; el selector contiene ese fallo y elige
Qwen como decisión automática conservadora.

Los overrides sólo se reconocen cuando una acción de cambio precede al backend,
por ejemplo «usa Qwen» o «respóndeme con GPT». Menciones temáticas y expresiones
negadas como «no quiero usar GPT» no cambian de rama. De forma análoga,
`GuardedSemanticRouter` consulta primero
`ResearchEscalationDetector.isExplicitlyNegated(...)`; «no lo busques en
Internet» permanece fuera de Research aunque contenga vocabulario de búsqueda.

### 32.8. Corpus de backend y profundidad

Los clasificadores de General y Research se evalúan de forma separada:

| Decisión | Development | Holdout | Etiquetas |
|---|---:|---:|---|
| Backend General | 30 | 20 por versión | `qwen_local`, `gpt` |
| Backend Research | 30 | 20 | `qwen_local`, `gpt_web` |
| Profundidad Research | 30 | 20 | `quick`, `deep` |

General conserva `holdout-v1` como regresión histórica y `holdout-v2` como
validación final congelada. Cada reporte incluye accuracy, macro-F1,
precision/recall/F1 por etiqueta, matriz de confusión, errores, latencias p50/p95
y métricas de retry. El gate exige cero errores finales y macro-F1 mínima de
`0.90`. El holdout v2 de General se ejecutó una vez el 11 de septiembre y obtuvo
macro-F1 `0.9000`, sin errores finales ni retries; desde entonces se considera
consumido y no debe usarse para reajustar el prompt.

Los comandos y reglas de evaluación se mantienen en
[`src/test/resources/evaluation/README.md`](../src/test/resources/evaluation/README.md).

---

## 33. OS Skills v2 — 11 al 13 de septiembre de 2026

Los commits `ce5f59a` y `7805b4d` reemplazaron la integración fija de Spotify
por un subsistema de aplicaciones de Windows. `Main` compone ahora:

```text
WindowsApplicationDiscovery
        +
ApplicationAliasConfigLoader(config/os-applications.json)
        ↓
ApplicationCatalog
        ↓
ApplicationRegistry ──────────────→ resolución de lenguaje
        ↓
ApplicationRuntimeResolver ───────→ identidad de procesos
        ↓
WindowsApplicationController ─────→ open / close / focus / status / list running
```

El catálogo se refresca al iniciar Ares. Si una resolución devuelve `UNKNOWN` o
`CATALOG_UNAVAILABLE`, se intenta un refresh y se resuelve una vez más. Si un
refresh falla después de haber cargado un catálogo válido, se conserva el último
estado disponible; si nunca hubo uno, el catálogo permanece degradado con el
motivo del error.

La resolución sigue esta precedencia:

```text
alias configurado
→ nombre/alias canónico normalizado
→ coincidencia natural determinista y única
→ AMBIGUOUS o UNKNOWN
```

La coincidencia natural sólo admite equivalencias de separación, como
`Prime Video`/`Primevideo`, y prefijos de tokens completos, como
`IntelliJ`/`IntelliJ IDEA`. No usa Levenshtein ni selecciona arbitrariamente
entre candidatos. Los aliases manuales tienen precedencia y `removeAliases`
puede retirar un alias automático sin eliminar la aplicación del catálogo.

### 33.1. Acciones y resultados

`OsAction` contiene:

```text
OPEN_APPLICATION
CLOSE_APPLICATION
FOCUS_APPLICATION
LIST_APPLICATIONS
LIST_RUNNING_APPLICATIONS
CHECK_APPLICATION_INSTALLED
GET_APPLICATION_STATUS
UNSUPPORTED
```

Resolver una aplicación produce `FOUND`, `UNKNOWN`, `AMBIGUOUS` o
`CATALOG_UNAVAILABLE`. Las acciones runtime devuelven `SUCCESS`, `NOT_RUNNING`,
`NO_VISIBLE_WINDOW`, `FOCUS_REJECTED`, `PROCESS_IDENTITY_UNAVAILABLE` o `FAILED`.
El estado observado distingue:

```text
NOT_RUNNING
RUNNING_BACKGROUND
RUNNING_WITH_WINDOW
```

Una aplicación puede abrirse y aparecer en el catálogo aunque su identidad
runtime sea insuficiente. En ese caso, Ares rechaza status, focus y close con
`PROCESS_IDENTITY_UNAVAILABLE` en lugar de inferir qué proceso controlar.

### 33.2. Identidad runtime y defensa contra TOCTOU

`ApplicationProcessIdentity` combina:

- rutas ejecutables;
- package roots;
- nombres de proceso candidatos;
- nombres confiables sólo cuando Windows no entrega una ruta;
- conjuntos de argumentos `CONTAINS_ALL`;
- vectores de argumentos `EXACT`;
- relación con una aplicación host;
- firmas de ventana por clase y/o título;
- habilitación explícita de asociación por ventana.

`Win32_Process` entrega PID, `ParentProcessId`, `CreationDate`, ruta, nombre y
command line dentro del mismo snapshot. `ApplicationRuntimeResolver` exige una
identidad fuerte y vuelve a comprobarla justo antes de cada efecto. La
combinación de PID y `CreationDate` evita actuar sobre un proceso distinto que
haya reutilizado el mismo PID.

Una query WMI exitosa sin el PID devuelve `NOT_FOUND`. Timeout, acceso denegado,
error COM, PID duplicado o datos inconsistentes devuelven
`OBSERVATION_FAILED`. Esos fallos nunca se convierten en `NOT_RUNNING`.

El cierre intenta primero `WM_CLOSE` sobre ventanas revalidadas. Si las ventanas
no desaparecen, sólo puede terminar procesos cuya identidad fue revalidada. Para
una aplicación hospedada en un proceso compartido, foco y cierre quedan
restringidos a HWND propios y el fallback de terminación se deshabilita.

La configuración exacta y sus invariantes se mantienen en
[`docs/OS_APPLICATIONS.md`](../docs/OS_APPLICATIONS.md). El esquema vigente es:

```json
{
  "aliases": {},
  "removeAliases": [],
  "processNames": {},
  "trustedProcessNamesWhenPathUnavailable": {},
  "commandLineArgumentSets": {},
  "exactCommandLineArgumentSets": {},
  "hostRelationships": {},
  "windowSignatures": {},
  "windowAssociationsEnabled": {}
}
```

### 33.3. Catálogo conversacional y salida visual

`AssistantResult` puede transportar un `AssistantPayload` además del texto y los
estados de General/Research. Para OS existen dos payloads:

```text
ApplicationCatalogPayload
→ sessionId, filtro, página, tamaño, total e items
→ KEEP_OPEN + OsConversationState

OpenApplicationsPayload
→ aplicaciones abiertas verificadas + contador no verificable
→ PRESERVE, sin crear una sesión OS
```

`CatalogSessionStore` mantiene una única sesión activa, ordena por display name y
divide los resultados en páginas de 20. Los follow-ups aceptan siguiente,
anterior, primera, última y filtros como «muestra las aplicaciones de Microsoft».
JavaFX observa la sesión y
actualiza buscador, lista y controles de página sin volver a consultar al modelo.

«¿Qué aplicaciones están abiertas?» toma un solo snapshot de procesos y enumera
una sola vez las ventanas visibles. Sólo publica aplicaciones
`RUNNING_WITH_WINDOW`; las no verificables se excluyen y se cuentan. Con hasta
cinco resultados, la voz enumera todos. Con más de cinco, menciona los primeros
cinco y la lista completa se fuerza a pantalla. Mute, volumen cero y volumen bajo
siguen respetando `OutputPresentationPolicy`.

### 33.4. Firefox y Prime Video

La configuración actual identifica Firefox normal mediante argumentos exactos
`[]` o `[-os-autostart]` y una firma opt-in de `MozillaWindowClass` cuyo título
termina en «— Mozilla Firefox». Prime Video declara su relación con Firefox como
host, pero todavía necesita capturas comparativas que prueben una señal HWND
positiva y estable. Mientras esa evidencia no exista, Prime Video debe permanecer
no verificable para status, focus y close cuando comparte por completo el host.

---

## 34. Estado vigente y validación — 13 de septiembre de 2026

Arquitectura de composición actual:

```text
Ares startup → LmStudioStartupCoordinator
    ├─ LMS daemon
    ├─ API server + health check HTTP
    ├─ phi-router (prioridad)
    └─ qwen-main (background)
    → ModelRuntimeSnapshot
        ├─ InfrastructureStatus → Console / JavaFX
        └─ daemon + API server + Phi READY → iniciar voz una sola vez

Mic → VAD → STT → control/activación → clasificación de utterance
    → GuardedSemanticRouter → SkillRouter
        ├─ SYSTEM_TIME
        ├─ AUDIO_CONTROL
        ├─ OS_COMMAND → catálogo + identidad Windows + payload visual/interactivo
        ├─ CURRENT_RESEARCH → Qwen local | GPT Web → QUICK | DEEP
        └─ GENERAL → Qwen local | GPT
    → AssistantTurn
        ├─ Completed / Async → AssistantResult + estado/payload + política efectiva
        └─ AwaitingInteraction → InteractionService → continuación del Skill
    → AssistantOutputCoordinator → TTS y/o JavaFX
```

Configuración de modelos:

| Propiedad | Uso | Predeterminado |
|---|---|---|
| `ares.local-model` | Phi para clasificadores estructurados | `phi-router` |
| `ares.local-qwen-base-url` | proveedor local de Qwen | `http://localhost:1234` |
| `ares.local-qwen-model` | Qwen compartido por General y Research | `qwen-main` |
| `ares.local-research-model` | alias compatible del modelo Qwen | sólo si falta la propiedad nueva |

La evidencia histórica queda fechada para evitar confundirla con una ejecución
actual:

```text
5 de septiembre   167 pruebas, 0 fallos, 0 errores
10 de septiembre  285 pruebas sin fallos
12 de septiembre  reportes Surefire locales: 403 pruebas, 0 fallos, 0 errores, 0 omitidas
13 de septiembre  suite Maven: 415 pruebas, 0 fallos, 0 errores, 0 omitidas
14 de septiembre  suite Maven: 437 pruebas, 0 fallos, 0 errores, 0 omitidas
```

La validación del 13 de septiembre produjo 60 reportes bajo
`target/surefire-reports`. Incluye las pruebas del supervisor de LM Studio y las
regresiones de disponibilidad local, Research y limpieza de recursos.

La validación del 14 de septiembre incorpora las regresiones de sesión
interactiva, routing de voz, reanudación en el executor de Ares y resolución
conservadora del monitor.

Pendientes vigentes:

- obtener evidencia estable para aplicaciones hospedadas como Prime Video;
- agregar smoke tests de composición de `Main` y pruebas E2E con el hardware objetivo;
- evaluar sistemáticamente `LocalAudioControlParser`;
- decidir un handoff transitorio al volver a una rama conversacional ya existente;
- medir coste y latencia reales por backend;
- reemplazar salidas directas a consola por logging estructurado;
- validar e inyectar de forma uniforme dependencias que aún se crean en `Main`.

---

## 35. Arranque supervisado de LM Studio — 13 de septiembre de 2026

`LmStudioStartupCoordinator` concentra la comprobación y recuperación inicial de
la infraestructura local. La aplicación ya no presupone que LM Studio, su API y
los modelos están disponibles cuando comienza `Main`: publica snapshots de su
estado, ejecuta sólo las operaciones necesarias y mantiene Ares y JavaFX activos
si una recuperación termina en fallo.

La dependencia de arranque es:

```text
LMS_DAEMON
    ├─ API_SERVER
    ├─ PHI_ROUTER   → habilita el runtime de voz junto con daemon + API
    └─ QWEN_MAIN    → continúa recuperándose en background
```

Los tres componentes dependientes esperan a que el daemon esté `READY`. Esa
espera no consume intentos. Al recuperarse el daemon se reanudan en orden API,
Phi y Qwen; Phi conserva así prioridad sobre el modelo generativo.

### 35.1. Estado por componente y estado global

El estado observable separa dos niveles:

```text
ComponentState = CHECKING | LOADING | READY | RETRY_WAIT | FAILED
RuntimeState   = STARTING | PARTIALLY_READY | READY | DEGRADED
```

`RuntimeState` siempre se deriva de los cuatro `ComponentSnapshot`:

- cualquier componente en `FAILED` produce `DEGRADED`;
- daemon, API y Phi en `READY`, con Qwen todavía pendiente, producen
  `PARTIALLY_READY`;
- los cuatro componentes en `READY` producen `READY`;
- cualquier otra combinación no terminal permanece en `STARTING`.

Cada snapshot conserva detalle diagnóstico, dependencia bloqueante, retries
consumidos, instante del próximo retry y generación de recuperación.
`isPhiUsable()` exige daemon, API y `PHI_ROUTER` en `READY`; `isQwenUsable()`
exige daemon, API y `QWEN_MAIN`. Ambos métodos son snapshots de disponibilidad:
los clientes siguen tratando los errores de transporte que pueden ocurrir entre
la consulta y una petición posterior.

### 35.2. Comprobaciones y operaciones mutantes

Las comprobaciones usan un timeout de 10 segundos:

```text
lms daemon status --json
lms server status --json
lms ps --json
GET http://127.0.0.1:1234/api/v1/models
```

El servidor sólo queda disponible cuando LMS informa que está iniciado y el
endpoint devuelve HTTP 2xx con JSON compatible que contiene `models` o `data`.
La combinación de ambas señales diferencia:

- `STOPPED`: LMS informa servidor detenido y nada escucha en el puerto; se
  permite ejecutar `server start`;
- `UNHEALTHY`: LMS informa servidor iniciado, pero el health check real falla;
  no se inicia una segunda instancia;
- `PORT_CONFLICT`: LMS informa servidor detenido, pero el puerto responde; no se
  atribuye automáticamente ese proceso a LM Studio ni se ejecuta `server start`.

Las operaciones permitidas son exactamente:

```text
lms daemon up --json
lms server start --port 1234 --bind 127.0.0.1
lms load phi-3.5-mini-instruct --gpu off --context-length 4096 --identifier phi-router --yes
lms load qwen/qwen3.5-9b --gpu max --context-length 32768 --identifier qwen-main --yes
```

Daemon y servidor disponen de 30 segundos para ejecutar su comando; una carga de
modelo dispone de 10 minutos. Justo antes de cada operación mutante se repite la
comprobación de postcondición bajo el lock global de comandos mutantes. Si el
componente se recuperó mientras esperaba, pasa a `READY` sin ejecutar el comando
ni consumir el retry. Un resultado indeterminado tampoco autoriza una mutación.

Si un proceso supera su timeout, `ProcessLmsCommandRunner` intenta terminarlo y,
tras dos segundos de gracia, fuerza su cierre si continúa vivo. El coordinador
vuelve a comprobar la postcondición antes de registrar el intento como fallido:
un daemon, servidor o modelo que haya quedado disponible se acepta como `READY`.
Para operaciones de infraestructura que terminan antes del timeout, la
postcondición puede observarse durante hasta 30 segundos.

Para los modelos se exige el alias exacto `phi-router` o `qwen-main`. Cuando
`lms ps --json` entrega `path`, `modelKey`, `model_key` o `key`, también se
comprueba que pertenezca a `phi-3.5-mini-instruct` o `qwen3.5-9b`. Una identidad
incompatible bloquea la carga para no sobrescribir el alias; si LMS no entrega
datos suficientes, el alias exacto se acepta con identidad no verificable. El
coordinador reutiliza el modelo correcto sin comparar context length, GPU,
cuantización ni otros parámetros de carga.

### 35.3. Reintentos, single-flight y generaciones

Cada componente posee su propia serie de recuperación:

```text
intento inicial
→ fallo → esperar 15 s → retry 1
→ fallo → esperar 45 s → retry 2
→ fallo → esperar 120 s → retry 3
→ fallo → FAILED
```

El backoff es relativo al fallo anterior. `operationLock` permite una sola
operación activa por componente y `mutatingCommandLock` serializa las mutaciones
de LMS entre componentes. `ProcessLmsCommandRunner` también rechaza un segundo
proceso activo para el mismo propietario.

Cada serie tiene una generación. Un retry manual cancela la tarea programada y
el proceso propios de la generación anterior, incrementa la generación, reinicia
el contador y comienza con un nuevo pre-check. Si el componente solicitado
depende de un daemon en `FAILED`, también inicia una serie nueva para el daemon.
Al alcanzar `READY` se cancelan los retries pendientes y se invalida la
generación. `CommandGeneration` coordina esa invalidación con la creación del
proceso hijo, de modo que una tarea obsoleta tampoco puede iniciar una operación
en la ventana entre la última comprobación y `ProcessBuilder.start()`.

### 35.4. Integración con Ares y JavaFX

`Main` registra el coordinador en `ResourceCleanup` y se suscribe a cada
`ModelRuntimeSnapshot`. La suscripción publica un `InfrastructureStatus` en la
salida visual y expone `retry(RuntimeComponent)` a la interfaz. Cuando
`isPhiUsable()` pasa a verdadero, un `AtomicBoolean` y un lock compartido con el
cierre inician STT, TTS y captura de voz una sola vez. Qwen puede continuar su
carga mientras la interacción por voz ya está disponible.

JavaFX muestra daemon, API, `phi-router` y `qwen-main`, con estado, diagnóstico y
próximo retry. Un componente en `FAILED` ofrece su propio botón **Reintentar**.
El panel permanece visible durante el arranque o la degradación; cuando todo está
listo muestra temporalmente «phi-router y qwen-main están activos». Una respuesta
del asistente conserva prioridad visual y el último estado de infraestructura se
vuelve a presentar después. `ConsoleVisualOutput` ofrece el mismo estado como
fallback textual.

`LocalQwenChatClient` consulta `isQwenUsable()` antes de llamar a la API y falla
rápido con un snapshot de indisponibilidad, sin eliminar el manejo normal de
errores HTTP. General conserva su fallback automático a GPT. Research contiene
la indisponibilidad local y responde explícitamente que el modelo no está
disponible en ese momento.

Al cerrar Ares, el coordinador invalida generaciones, cancela tareas y termina
únicamente los procesos hijos que todavía controla. No ejecuta `lms unload` ni
descarga modelos que ya estuvieran cargados en LM Studio.

### 35.5. Validación automatizada

La suite cubre las garantías centrales del supervisor:

- infraestructura ya disponible sin comandos mutantes;
- modelo que aparece entre el probe inicial y el pre-check;
- timeout cuyo efecto aparece antes del retry siguiente;
- retry manual exitoso que invalida un retry automático pendiente;
- intento inicial, tres retries y transición terminal;
- estados globales `PARTIALLY_READY` y `DEGRADED`;
- puerto ocupado, servidor iniciado pero no saludable y servidor detenido;
- alias asociado a otro modelo y alias con identidad no verificable;
- disponibilidad de Qwen, mensaje explícito de Research y orden de limpieza.

La suite Maven completa del 13 de septiembre finalizó con 415 pruebas, 0 fallos,
0 errores y 0 omitidas.

---

## 36. Superficie interactiva — 14 de septiembre de 2026

La superficie interactiva permite que un Skill suspenda una acción cuando
necesita input humano y la reanude con un resultado tipado. No convierte toda
operación asíncrona en interacción: `SkillExecution` y `AssistantTurn` distinguen
explícitamente tres estados:

```text
Completed
→ resultado disponible

Async
→ trabajo asíncrono ordinario

AwaitingInteraction<T>
→ InteractionRequest<T> + continuación tipada
```

El único consumidor productivo actual es `OsCommandSkill`. Cuando
`OPEN_APPLICATION` resuelve el target como `AMBIGUOUS`, construye un
`ChoiceRequest` con los candidatos y suspende la ejecución. Una selección válida
reanuda exactamente la intención capturada y abre la aplicación elegida; una
cancelación, expiración o indisponibilidad no ejecuta la acción.

```text
"Ares, abre Studio"
→ OS_COMMAND / OPEN_APPLICATION
→ ApplicationRegistry = AMBIGUOUS
→ AwaitingInteraction<String>
→ opciones en la superficie
→ selección por toque o voz
→ continuación de OsCommandSkill en el executor de Ares
→ abrir únicamente la aplicación seleccionada
```

Una coincidencia única se ejecuta directamente y un target desconocido responde
sin abrir la superficie. `CLOSE_APPLICATION`, `FOCUS_APPLICATION` y
`GET_APPLICATION_STATUS` todavía conservan su comportamiento anterior ante una
ambigüedad.

### 36.1. Identidad, exclusión y lifecycle

`InteractionService` crea el UUID interno de cada sesión. El caller no construye
ni conoce esa identidad; `requestId` queda separado como correlación externa
opcional y se propaga al `InteractionResult`.

Sólo puede existir una sesión activa. La instalación se hace mediante CAS; una
segunda solicitud devuelve `BUSY` inmediatamente y no crea sesión. El caller
recibe un `CompletionStage` mínimo y no el `CompletableFuture` mutable que posee
la sesión.

```text
PRESENTING
├─ visible() → VISIBLE
├─ cancel → CANCELLED
├─ presenter no disponible → UNAVAILABLE
└─ cierre de Ares → CLOSED

VISIBLE
├─ submit → SUBMITTED
├─ cancel → CANCELLED
├─ timeout → EXPIRED
├─ pérdida del monitor → UNAVAILABLE
└─ cierre de Ares → CLOSED
```

`visible(sessionId)` sólo admite una transición `PRESENTING → VISIBLE`. El timeout
predeterminado de 90 segundos se programa exclusivamente cuando JavaFX publica
`WINDOW_SHOWN`, no al ejecutar `Platform.runLater`. Toda transición terminal
cancela explícitamente la tarea de timeout. `dismiss(sessionId)` vuelve a comprobar
la identidad en el JavaFX Application Thread para que un dismiss tardío nunca
oculte una sesión posterior.

### 36.2. Voz y reanudación

Mientras haya una sesión activa, `InteractionVoiceRouter` consume cada
transcripción antes del control conversacional, activación y routing normales.
La orden universal de cancelación funciona incluso durante `PRESENTING` o cuando
la solicitud no habilita respuestas de voz.

Las opciones de voz se comparan mediante label normalizado, aliases y ordinales.
Si una transcripción coincide con más de un ID después de normalizar, se consume
como no resoluble y la sesión permanece activa. No se selecciona un candidato
arbitrariamente.

El thread que completa `InteractionService` sólo agenda la reanudación. La
continuación de dominio del Skill se ejecuta después en el executor lógico de
`SpeechProcessingService`; no puede ejecutarse en el thread de JavaFX, STT,
scheduler o en otro productor del resultado.

### 36.3. Tipos de interacción y foco

El presenter soporta tres solicitudes:

| Tipo | Resultado | Presentación |
|---|---|---|
| `ChoiceRequest` | ID de la opción | botones táctiles y resolución opcional por voz |
| `ConfirmationRequest` | `boolean` | confirmar/rechazar por toque o voz |
| `TextInputRequest` | texto validado | editor y teclado táctil QWERTY latino |

La superficie vive en un `Stage` separado y `alwaysOnTop`. Visibilidad y foco son
decisiones distintas: `PASSIVE` intenta aplicar `WS_EX_NOACTIVATE`, no llama a
`requestFocus()` y restaura el foreground previo de Windows como operación
best-effort. Esa restauración no forma parte del éxito de la interacción.
`TextInputRequest` (`FREE_TEXT`) solicita foco después de mostrarse para habilitar la entrada física,
además del teclado táctil.

La salida visual normal y la superficie comparten un único `JavaFxRuntime`.
`Platform.setImplicitExit(false)` mantiene vivo el toolkit cuando ambos stages
están ocultos; sólo el propietario registrado por `Main` llama a
`Platform.exit()` durante el cierre.

### 36.4. Resolución del monitor

`config/interaction-display.json` permite declarar una identidad persistente,
una resolución de fallback y si el monitor principal puede participar en la
selección normal. La precedencia vigente es:

```text
1. displayId configurado, único y permitido
2. coincidencia única con la resolución fallback
3. monitor secundario disponible
   → orden determinista por nombre nativo e ID persistente
4. monitor principal como último fallback si no existe un secundario
```

Si varias pantallas coinciden exactamente con la resolución fallback, el
resultado permanece `UNAVAILABLE`: una coincidencia ambigua no autoriza escoger
una pantalla arbitrariamente. Cuando no existe ninguna coincidencia de
resolución, sí se activa el fallback por disponibilidad. Con la configuración
actual y dos monitores 2560×1440, se selecciona automáticamente el secundario.
La identidad nativa se vuelve a mapear a las coordenadas actuales de JavaFX; si
el monitor desaparece durante una sesión, ésta termina como `UNAVAILABLE`.

### 36.5. Casos preparados pero aún no conectados

La infraestructura ya permite que otros Skills produzcan interacciones, pero los
siguientes casos no están implementados como flujos productivos:

- elegir entre aplicaciones ambiguas al cerrar, enfocar o consultar estado;
- elegir un dispositivo de audio, modelo o resultado;
- confirmar una acción destructiva, sensible o difícil de revertir;
- pedir mediante texto libre una ruta, URL, nombre, filtro o argumento ausente;
- corregir manualmente una transcripción que no pudo resolverse.

Estos casos deben implementarse devolviendo `SkillExecution.AwaitingInteraction`,
sin llamar directamente al presenter ni generar un ID de sesión desde el Skill.

### 36.6. Validación automatizada

La suite cubre creación interna de identidad, vista no mutable del resultado,
CAS y `BUSY`, transición de visibilidad, timeout, cancelación terminal,
cancelación universal por voz, aliases ambiguos, reanudación fuera del thread que
completa la interacción, distinción entre `Async` y `AwaitingInteraction`,
vertical slice de aplicación ambigua y selección del monitor.

La suite Maven completa del 14 de septiembre finalizó con 437 pruebas, 0 fallos,
0 errores y 0 omitidas.
