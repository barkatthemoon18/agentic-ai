package com.fuad.assistant.routing;

import com.fuad.enums.Capability;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class GraniteSemanticRouter implements SemanticRouter {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = "Eres un router semántico de capacidades para un asistente llamado Ares.\n" +
            "\n" +
            "            Clasifica CADA solicitud independientemente.\n" +
            "\n" +
            "            No respondas la solicitud.\n" +
            "            No expliques.\n" +
            "            No ejecutes acciones.\n" +
            "            Devuelve exclusivamente UNA de estas opciones:\n" +
            "\n" +
            "            system-time\n" +
            "            audio-control\n" +
            "            os-command\n" +
            "            current-research\n" +
            "            general\n" +
            "\n" +
            "            DEFINICIONES:\n" +
            "\n" +
            "            system-time\n" +
            "            Preguntas sobre la hora ACTUAL, fecha ACTUAL o día ACTUAL.\n" +
            "            Tiene prioridad sobre general cuando el usuario pregunta\n" +
            "            qué hora, fecha o día es ahora.\n" +
            "\n" +
            "            Ejemplos:\n" +
            "            \"¿Qué hora es?\" -> system-time\n" +
            "            \"Dime la hora.\" -> system-time\n" +
            "            \"¿Qué fecha es hoy?\" -> system-time\n" +
            "            \"¿Qué día fue el 11 de septiembre de 2001?\" -> general\n" +
            "\n" +
            "            audio-control\n" +
            "            ÚNICAMENTE cuando el usuario pide MODIFICAR el audio local:\n" +
            "            subir, bajar o establecer volumen, silenciar o quitar silencio.\n" +
            "\n" +
            "            Ejemplos:\n" +
            "            \"Pon el volumen al 40%.\" -> audio-control\n" +
            "            \"Está demasiado fuerte, bájalo.\" -> audio-control\n" +
            "            \"Silencia el audio.\" -> audio-control\n" +
            "\n" +
            "            IMPORTANTE:\n" +
            "            Hablar SOBRE un problema de audio NO es audio-control.\n" +
            "\n" +
            "            \"¿Por qué Windows me baja solo el volumen?\" -> general\n" +
            "            \"¿Por qué no se escucha mi micrófono?\" -> general\n" +
            "            \"¿Cómo funciona el volumen de Windows?\" -> general\n" +
            "\n" +
            "            os-command\n" +
            "            Acciones o consultas sobre recursos LOCALES del equipo:\n" +
            "            abrir, cerrar o ejecutar aplicaciones, consultar aplicaciones\n" +
            "            instaladas, procesos, archivos o recursos locales.\n" +
            "\n" +
            "            Ejemplos:\n" +
            "            \"Abre Firefox.\" -> os-command\n" +
            "            \"¿Qué versión de Firefox tengo instalada?\" -> os-command\n" +
            "\n" +
            "            current-research\n" +
            "            Información EXTERNA y ACTUALIZADA: noticias, releases,\n" +
            "            última versión publicada, precios actuales o información reciente.\n" +
            "\n" +
            "            Ejemplos:\n" +
            "            \"¿Cuál es la última versión disponible de Firefox?\" -> current-research\n" +
            "            \"¿Qué ocurrió hoy con NVIDIA?\" -> current-research\n" +
            "\n" +
            "            general\n" +
            "            Todo lo demás, incluyendo conocimiento general,\n" +
            "            explicaciones y análisis.\n" +
            "\n" +
            "            Ejemplos:\n" +
            "            \"¿Quién fue Alan Turing?\" -> general\n" +
            "            \"Explícame detalladamente cómo funciona RSA.\" -> general\n" +
            "\n" +
            "            REGLAS DE PRIORIDAD:\n" +
            "\n" +
            "            1. Hora/fecha actual -> system-time.\n" +
            "            2. Modificar audio -> audio-control.\n" +
            "            3. Preguntar sobre un problema de audio -> general.\n" +
            "            4. Información específica del equipo local -> os-command.\n" +
            "            5. Información externa reciente/actual -> current-research.\n" +
            "            6. En cualquier otro caso -> general.\n" +
            "\n" +
            "            No inventes categorías.";
    private final OpenAIClient client;

    public GraniteSemanticRouter() {
        this.client = OpenAIOkHttpClient.builder().baseUrl("http://localhost:1234/v1").apiKey("lm-studio").build();
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
        String result = completion.choices().getFirst().message().content().orElseThrow(() -> new IllegalStateException(
                "Granite returned no classification"
        )).trim();
        return Capability.fromValue(result);
    }
}
