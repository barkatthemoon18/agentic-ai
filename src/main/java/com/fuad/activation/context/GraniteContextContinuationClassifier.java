package com.fuad.activation.context;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteContextContinuationClassifier implements ContextContinuationClassifier {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        Eres un clasificador de activación para un asistente de voz llamado Ares.

        Determina si el texto representa razonablemente una solicitud,
        pregunta o instrucción que el usuario espera que un asistente atienda.

        Responde exclusivamente:

        activate
        none

        ACTIVATE si el texto:
        - da una orden directa;
        - formula una pregunta directa;
        - solicita información;
        - pide una recomendación o sugerencia;
        - pide ayuda, explicación o búsqueda;
        - formula una petición cortés mediante expresiones como:
          "puedes", "podrías", "me puedes", "me podrías",
          "me ayudas", "me recomendarías", "me sugieres";
        - pregunta por capacidad como forma de realizar una solicitud.

        IMPORTANTE:
        Una pregunta formulada como "¿puedes...?" o "¿podrías...?"
        normalmente es una PETICIÓN, no una pregunta literal sobre
        las capacidades del asistente.

        Ejemplos ACTIVATE:

        "Abre Spotify" -> activate
        "Dime qué hora es" -> activate
        "Qué hora es" -> activate
        "Explícame cómo funciona RSA" -> activate
        "Averigua qué pasó con NVIDIA" -> activate
        "Busca la última versión de Firefox" -> activate

        "¿Me puedes sugerir alguna canción?" -> activate
        "¿Puedes recomendarme una película?" -> activate
        "¿Podrías buscar información sobre NVIDIA?" -> activate
        "¿Me ayudas con este problema?" -> activate
        "¿Me puedes explicar RSA?" -> activate
        "¿Qué canción me recomiendas?" -> activate
        "¿Sabes qué hora es?" -> activate

        NONE si el texto:
        - es una afirmación sin petición;
        - describe algo que ocurre;
        - pertenece a conversación ambiental;
        - indica una acción que el hablante hará por sí mismo;
        - está dirigido claramente a otra persona;
        - es un fragmento sin una solicitud clara.

        Ejemplos NONE:

        "Spotify se está cerrando solo" -> none
        "Ayer fui a Spotify" -> none
        "Creo que Firefox está actualizado" -> none
        "Mañana voy a abrir Spotify" -> none
        "Le dije a Juan que abriera Spotify" -> none
        "Eres bastante rápido" -> none
        "Las áreas están delimitadas" -> none

        No respondas la solicitud.
        No expliques.
        Devuelve exclusivamente activate o none.
        """;
    private final OpenAIClient client;

    public  GraniteContextContinuationClassifier(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public boolean shouldContinue(String text) {
        ChatCompletionCreateParams params =
                ChatCompletionCreateParams.builder()
                        .model(MODEL)
                        .addSystemMessage(SYSTEM_PROMPT)
                        .addUserMessage(text)
                        .temperature(0.0)
                        .maxCompletionTokens(4)
                        .build();
        ChatCompletion completion =
                client.chat()
                        .completions()
                        .create(params);
        String result = completion
                .choices()
                .getFirst()
                .message()
                .content()
                .orElseThrow(() -> new IllegalStateException("Granite returned no context continuation classification"))
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (result) {
            case "continue" -> true;
            case "none" -> false;
            default -> throw new IllegalStateException(
                    "Unknown contextual classification: " + result
            );
        };
    }
}
