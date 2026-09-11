package com.fuad.assistant.skills.os;

import com.fuad.config.AppConfig;
import com.fuad.enums.OsAction;
import com.fuad.model.LocalModelOutput;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalOsCommandParser implements OsCommandParser {
    private static final Pattern OUTPUT = Pattern.compile(
            "^(open_application|close_application|focus_application|list_applications"
                    + "|check_application_installed|get_application_status|unsupported)\\|(.+)$");
    private static final String SYSTEM_PROMPT = """
            You are a restricted application command parser. The user speaks Spanish.

            Return exactly one line using one of these forms:
            open_application|<application name>
            close_application|<application name>
            focus_application|<application name>
            list_applications|<optional filter or none>
            check_application_installed|<application name>
            get_application_status|<application name>
            unsupported|unknown

            Open, close and focus are valid only for a direct request to perform the action now.
            Descriptions, past events, future plans, predictions and reported speech are unsupported.
            Preserve the application name from the request. Never invent an executable, path,
            AppID or shell command.

            "Abre Firefox" -> open_application|Firefox
            "Cierra Spotify" -> close_application|Spotify
            "Enfoca Visual Studio Code" -> focus_application|Visual Studio Code
            "Trae IntelliJ al frente" -> focus_application|IntelliJ
            "Cambia a Firefox" -> focus_application|Firefox
            "Qué aplicaciones tengo" -> list_applications|none
            "Muéstrame las aplicaciones de Adobe" -> list_applications|Adobe
            "Está Firefox instalado" -> check_application_installed|Firefox
            "Está Spotify abierto" -> get_application_status|Spotify
            "Mañana voy a cerrar Spotify" -> unsupported|unknown
            "Spotify se cerró solo" -> unsupported|unknown

            Do not explain. Return only the structured line.
            """;

    private final OpenAIClient client;
    private final String model;

    public LocalOsCommandParser(OpenAIClient client) {
        this(client, AppConfig.LOCAL_MODEL_ID);
    }

    public LocalOsCommandParser(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.model = LocalModelOutput.requireModelId(model);
    }

    @Override
    public OsCommandIntent parse(String command) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(command)
                .temperature(0.0)
                .maxCompletionTokens(40)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String output = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Local model returned no OS command classification"));
        return parseOutput(output);
    }

    static OsCommandIntent parseOutput(String output) {
        String result = Objects.requireNonNull(output, "output cannot be null").trim();
        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("OS command classification must contain exactly one line");
        }
        Matcher matcher = OUTPUT.matcher(result.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalStateException("Unknown OS command classification: " + result);
        }
        String target = result.substring(result.indexOf('|') + 1).trim();
        return switch (matcher.group(1)) {
            case "open_application" -> new OsCommandIntent(OsAction.OPEN_APPLICATION, target);
            case "close_application" -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, target);
            case "focus_application" -> new OsCommandIntent(OsAction.FOCUS_APPLICATION, target);
            case "list_applications" -> new OsCommandIntent(OsAction.LIST_APPLICATIONS,
                    target.equalsIgnoreCase("none") ? "" : target);
            case "check_application_installed" -> new OsCommandIntent(OsAction.CHECK_APPLICATION_INSTALLED, target);
            case "get_application_status" -> new OsCommandIntent(OsAction.GET_APPLICATION_STATUS, target);
            case "unsupported" -> OsCommandIntent.unsupported();
            default -> throw new IllegalStateException("Unknown OS command classification: " + result);
        };
    }
}
