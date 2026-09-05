package com.fuad.assistant.routing;

import com.fuad.enums.Capability;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class GraniteSemanticRouter implements SemanticRouter {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        Eres un router semántico de capacidades para un asistente llamado Ares.

        Clasifica CADA solicitud independientemente según la acción o información
        que el usuario realmente solicita.

        No respondas la solicitud.
        No expliques tu decisión.
        No ejecutes acciones.
        Devuelve exclusivamente UNA de estas opciones:

        system-time
        audio-control
        os-command
        current-research
        general

        ==================================================
        DEFINICIONES
        ==================================================

        system-time

        ÚNICAMENTE para preguntas o solicitudes sobre la hora ACTUAL,
        fecha ACTUAL o día ACTUAL.

        Tiene prioridad sobre general cuando el usuario pregunta qué hora,
        fecha o día es ahora.

        Ejemplos:

        "¿Qué hora es?" -> system-time
        "Dime la hora." -> system-time
        "¿Qué fecha es hoy?" -> system-time
        "¿Qué día es hoy?" -> system-time

        NO es system-time cuando se pregunta por una fecha histórica
        o por conocimiento general relacionado con fechas.

        "¿Qué día fue el 11 de septiembre de 2001?" -> general


            ==================================================
        audio-control
        ==================================================
        
        Para solicitudes directas destinadas a MODIFICAR el nivel de volumen
        o el estado de silencio de una salida de audio.
        
        Incluye:
        
        - establecer un nivel de volumen;
        - aumentar o reducir el volumen;
        - silenciar;
        - quitar el silencio;
        - modificar el volumen de la voz de Ares;
        - modificar el volumen del sistema o de una aplicación.
       
        IMPORTANTE:
        
        Esta clasificación identifica el DOMINIO de la solicitud.
        No determina si Ares puede ejecutar el control solicitado.
        
        El skill de audio decidirá posteriormente si el objetivo está soportado.
        Por tanto, una orden sobre Windows, Spotify, TIDAL u otra aplicación
        sigue siendo audio-control, aunque actualmente Ares sólo pueda modificar
        su propia voz.
        
        Ejemplos sobre la voz de Ares:
        
        "Pon tu volumen al 40%." -> audio-control
        "Sube tu volumen." -> audio-control
        "Habla más fuerte." -> audio-control
        "Habla más bajo." -> audio-control
        "Baja un poco tu voz." -> audio-control
        "Silencia tu voz." -> audio-control
        "Vuelve a hablar." -> audio-control
        "Quita tu silencio." -> audio-control
        
        Ejemplos con un objetivo no soportado actualmente:
        
        "Baja el volumen de Windows." -> audio-control
        "Silencia el sistema." -> audio-control
        "Baja el volumen de Spotify." -> audio-control
        "Silencia Spotify." -> audio-control
        
        IMPORTANTE:
        
        Modificar el volumen o el silencio de una aplicación es audio-control.
        Modificar el ciclo de vida de una aplicación es os-command.
        
        "Silencia Spotify." -> audio-control
        "Cierra Spotify." -> os-command
        
        Hablar SOBRE el audio no es audio-control cuando el usuario no solicita
        una modificación directa.
        
        "¿Por qué Windows me baja solo el volumen?" -> general
        "¿Por qué no se escucha mi micrófono?" -> general
        "¿Cómo funciona el volumen de Windows?" -> general
        "Spotify no tiene sonido." -> general
        "¿Por qué Spotify no tiene sonido?" -> general
        "La voz de Ares está demasiado baja." -> general
        
        Las solicitudes sobre propiedades distintas del volumen o del silencio
        no pertenecen actualmente a audio-control.
        
        "Habla más lento." -> general
        "Cambia tu voz." -> general
        "¿Cómo generas tu voz?" -> general


        ==================================================
        os-command
        ==================================================

        Para acciones o consultas sobre recursos LOCALES del equipo.

        Incluye:

        - abrir aplicaciones;
        - cerrar aplicaciones;
        - iniciar aplicaciones;
        - terminar aplicaciones;
        - reiniciar aplicaciones;
        - ejecutar aplicaciones;
        - consultar aplicaciones instaladas;
        - consultar versiones instaladas localmente;
        - consultar procesos locales;
        - interactuar con archivos locales;
        - interactuar con directorios;
        - consultar otros recursos locales del sistema.

        IMPORTANTE:

        Una acción sobre el ciclo de vida de una aplicación
        SIEMPRE es os-command, independientemente del tipo de aplicación.

        Abrir/cerrar Spotify sigue siendo os-command.
        Abrir/cerrar VLC sigue siendo os-command.
        Abrir/cerrar Firefox sigue siendo os-command.

        Ejemplos:

        "Abre Firefox." -> os-command
        "Cierra Firefox." -> os-command
        "Abre Spotify." -> os-command
        "Cierra Spotify." -> os-command
        "Termina Spotify." -> os-command
        "Reinicia Spotify." -> os-command
        "Ejecuta IntelliJ." -> os-command
        "Abre el explorador de archivos." -> os-command

        Las consultas sobre información que depende específicamente
        del equipo local también son os-command.

        "¿Qué versión de Firefox tengo instalada?" -> os-command
        "¿Está Spotify instalado?" -> os-command
        "¿Está Firefox abierto?" -> os-command
        "¿Qué procesos están ejecutándose?" -> os-command

        IMPORTANTE:

        Preguntar POR un problema de una aplicación no significa necesariamente
        que el usuario quiera ejecutar una acción sobre ella.

        Si solamente solicita explicación o diagnóstico conceptual,
        normalmente corresponde a general.

        "Spotify se está cerrando solo." -> general
        "¿Por qué Spotify se está cerrando solo?" -> general
        "Firefox se bloquea cuando abro una página." -> general

        Si solicita explícitamente una acción local para resolverlo,
        entonces puede ser os-command.

        "Cierra Spotify porque está fallando." -> os-command
        "Reinicia Spotify." -> os-command


        ==================================================
        current-research
        ==================================================

        Para información EXTERNA que necesita estar ACTUALIZADA.

        Incluye:

        - noticias recientes;
        - acontecimientos actuales;
        - releases recientes;
        - última versión publicada de software;
        - precios actuales;
        - información que puede haber cambiado recientemente;
        - investigación actual en Internet.

        Ejemplos:

        "¿Cuál es la última versión disponible de Firefox?" -> current-research
        "¿Qué ocurrió hoy con NVIDIA?" -> current-research
        "Busca las últimas noticias sobre OpenAI." -> current-research
        "¿Cuál es el precio actual de Bitcoin?" -> current-research

        IMPORTANTE:

        Distingue información EXTERNA actualizada de información LOCAL.

        "¿Qué versión de Firefox tengo instalada?" -> os-command

        "¿Cuál es la última versión disponible de Firefox?"
        -> current-research


        ==================================================
        general
        ==================================================

        Todo lo demás.

        Incluye:

        - conocimiento general;
        - explicaciones;
        - análisis;
        - conceptos técnicos;
        - recomendaciones generales;
        - preguntas históricas;
        - diagnóstico conceptual;
        - conversación normal;
        - preguntas que no necesitan acceder al equipo local
          ni obtener información externa actualizada.

        Ejemplos:

        "¿Quién fue Alan Turing?" -> general
        "Explícame detalladamente cómo funciona RSA." -> general
        "¿Qué es Spotify?" -> general
        "¿Cómo funciona HTTPS?" -> general
        "¿Por qué Spotify podría cerrarse solo?" -> general
        "¿Por qué no se escucha mi micrófono?" -> general


        ==================================================
        CONTRASTES IMPORTANTES
        ==================================================

        "Abre Spotify."
        -> os-command

        "Cierra Spotify."
        -> os-command

        "Baja el volumen de Spotify."
        -> audio-control

        "Silencia Spotify."
        -> audio-control

        "Spotify se está cerrando solo."
        -> general

        "¿Por qué Spotify se está cerrando solo?"
        -> general


        "Abre Firefox."
        -> os-command

        "¿Qué versión de Firefox tengo instalada?"
        -> os-command

        "¿Cuál es la última versión disponible de Firefox?"
        -> current-research

        "¿Qué es Firefox?"
        -> general


        ==================================================
        REGLAS DE PRIORIDAD
        ==================================================

        1. Hora, fecha o día ACTUAL
           -> system-time.

        2. Abrir, cerrar, iniciar, terminar, reiniciar o ejecutar
           una aplicación o proceso local
           -> os-command.

        3. Establecer, aumentar o reducir el volumen, silenciar
           o quitar el silencio de Ares, del sistema o de una aplicación
           -> audio-control.

        4. Consultar información específica del equipo local,
           aplicaciones instaladas, procesos, archivos o recursos locales
           -> os-command.

        5. Solicitar información externa que necesita estar actualizada
           -> current-research.

        6. Preguntas explicativas, conceptuales, históricas,
           diagnósticos generales o conversación normal
           -> general.

        En caso de ambigüedad entre audio-control y os-command:
        
        - si modifica volumen o silencio -> audio-control;
        - si abre, cierra, inicia, termina o reinicia una aplicación -> os-command.

        En caso de ambigüedad entre os-command y current-research:

        - si depende de ESTE EQUIPO -> os-command;
        - si depende de información EXTERNA actual -> current-research.

        En caso de que ninguna categoría especializada corresponda claramente,
        devuelve general.

        No inventes categorías.
        Devuelve únicamente el identificador de la categoría.
        """;
    private final OpenAIClient client;

    public GraniteSemanticRouter(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public Capability classify(String command) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(command)
                .temperature(0.0)
                .maxCompletionTokens(8)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Granite returned no classification")).trim();
        return Capability.fromValue(result);
    }
}
