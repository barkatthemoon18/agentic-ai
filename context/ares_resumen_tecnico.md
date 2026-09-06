# Ares — Resumen técnico de arquitectura, lógica y decisiones

## 1. Objetivo de la iteración

Esta conversación consolidó varias piezas de Ares:

- routing semántico de capacidades;
- `OS_COMMAND` para abrir/cerrar aplicaciones;
- protección determinista antes de ejecutar comandos;
- conversación contextual, timeout y cancelación forzada;
- política por skill para mantener o preservar contexto;
- clasificador unificado `NEW_REQUEST` / `FOLLOW_UP` / `OTHER`;
- ownership del contexto por `Capability`;
- implementación inicial completa de `AUDIO_CONTROL`, limitada a la voz del asistente;
- idea futura de elegir entre modelo local y GPT según complejidad.

Arquitectura general:

```text
Mic
→ VAD
→ STT
→ ConversationControlDetector
→ Activation
→ Capability Router
→ Skill
→ AssistantResult
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

La arquitectura quedó:

```text
OsCommandSkill
    ↓
OsCommandSafetyGuard
    ↓
LocalOsCommandParser
    ↓
ApplicationRegistry
    ↓
ApplicationDefinition
    ↓
ApplicationController
```

Principio:

> El modelo no genera comandos shell arbitrarios. Produce una intención estructurada; Java autoriza el target y ejecuta un comando definido previamente.

### `ApplicationDefinition`

```java
public class ApplicationDefinition {
    private final String id;
    private final String displayName;
    private final List<String> openCommand;
    private final String processName;
}
```

Spotify:

```java
ApplicationDefinition spotify =
        new ApplicationDefinition(
                "spotify",
                "Spotify",
                List.of(
                        "cmd.exe",
                        "/c",
                        "start",
                        "",
                        "spotify:"
                ),
                "Spotify.exe"
        );
```

### Parser

Salida restringida:

```text
open_application|spotify
close_application|spotify
unsupported|unknown
```

### Apertura

```java
new ProcessBuilder(
        applicationDefinition.getOpenCommand()
).start();
```

### Cierre

Se decidió usar `ProcessHandle`, validando el ejecutable contra el `processName` autorizado.

```java
ProcessHandle.allProcesses()
        .filter(process -> process.info()
                .command()
                .map(command -> {
                    String filename =
                            Path.of(command)
                                    .getFileName()
                                    .toString();

                    return filename.equalsIgnoreCase(
                            applicationDefinition.getProcessName()
                    );
                })
                .orElse(false))
        .forEach(ProcessHandle::destroy);
```

Esto evita depender de comandos arbitrarios como `taskkill` producidos por un LLM.

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

## 5. Validación end-to-end de Spotify

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
→ probablemente KEEP_OPEN

OS_COMMAND
→ PRESERVE

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

## 12. Posible semántica híbrida — postergada

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

La implementación se **postergó** porque no bloquea el desarrollo actual y conviene acumular más casos reales antes de endurecer reglas.

---

## 13. Evaluación futura de otros modelos locales

El modelo local actual es Phi-3.5 Mini Instruct, servido con el identificador `phi-router`. Las clases usan el prefijo `Local` y el modelo se configura mediante `ares.local-model`; las evaluaciones permiten sobrescribirlo con `evaluation.model`.

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
→ introducir UtteranceShapeDetector híbrido
```

---

## 14. Futura separación local vs GPT

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
OS_COMMAND Spotify OPEN
OS_COMMAND Spotify CLOSE
OsCommandSafetyGuard
Application whitelist
ProcessHandle para cierre
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

3. Proteger onSpeechSegment ante RejectedExecutionException:
   si submit falla después de beginProcessing(), debe liberarse AudioPipeline.

4. Validar dependencias obligatorias en constructores.

5. Ejecutar los corpus contra Phi-3.5 Mini Instruct y ajustar el prompt con development;
   reservar holdout para validar exactitud, estabilidad y latencia finales.
```

## Funcional pero pendiente de evaluación

```text
LocalUtteranceClassifier
→ contrato unificado implementado
→ orquestación cubierta por tests
→ precisión real del modelo aún no medida sistemáticamente

LocalSemanticRouter
→ funcional tras reforzar OS_COMMAND vs AUDIO_CONTROL

LocalAudioControlParser
→ contrato cerrado y validación determinista implementados
→ interpretación lingüística real pendiente de evaluación sistemática
```

## Pendiente futuro

```text
CURRENT_RESEARCH
más aplicaciones OS
corpus específico para evaluar LocalAudioControlParser
persistencia del volumen entre ejecuciones
evaluar curva perceptual de ganancia frente a la curva lineal actual
posible soporte futuro para scopes SYSTEM y APPLICATION
comparar Phi-3.5 Mini Instruct / Gemma / Qwen
UtteranceShapeDetector híbrido
ModelRouter local vs GPT
métricas de coste/latencia por backend
```

---

# 27. Principios arquitectónicos consolidados

```text
1. Java decide lo determinista.
2. Phi-3.5 Mini Instruct interpreta lenguaje, no ejecuta comandos.
3. GPT se reserva para razonamiento/contenido donde aporta valor.
4. Los modelos nunca generan shell arbitrario.
5. Los targets OS deben estar whitelisteados.
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
ancho                         440 px
alto mínimo                   132 px
alto máximo                   360 px
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

`JavaFxVisualOutputDemo` es una prueba manual ejecutable desde IntelliJ. Muestra
secuencialmente un mensaje muted, uno de volumen bajo y otro extenso con scroll;
luego prueba el ocultamiento y el cierre del runtime JavaFX.

La suite automática no crea ventanas ni depende de una pantalla disponible. La
posición, animación, legibilidad, foco y comportamiento multimonitor se validan
manualmente mediante el demo.

## 28.3. Limitación conocida

JavaFX no garantiza por sí solo que un `Stage` transparente nunca tome foco en
Windows. Si el demo evidencia robo de foco, una iteración posterior deberá
aplicar la bandera nativa `WS_EX_NOACTIVATE`. Esto no modifica la política de
salida ni el coordinador.
