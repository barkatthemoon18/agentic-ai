package com.fuad.model.runtime;

import java.net.URI;
import java.time.Duration;
import java.util.List;

final class LmStudioRuntimeConstants {
    static final Duration INFRASTRUCTURE_TIMEOUT = Duration.ofSeconds(30);
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    static final Duration MODEL_LOAD_TIMEOUT = Duration.ofMinutes(10);
    static final Duration POST_CONDITION_POLL_INTERVAL = Duration.ofMillis(500);
    static final Duration DEPENDENT_START_DELAY = Duration.ofMillis(50);
    static final List<Duration> DEFAULT_BACKOFFS = List.of(
            Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(120));

    static final int SCHEDULER_THREAD_COUNT = 4;
    static final String SCHEDULER_THREAD_NAME = "lms-startup";

    static final String JSON_STATUS_FIELD = "status";
    static final String JSON_RUNNING_FIELD = "running";
    static final String MODEL_IDENTIFIER_FIELD = "identifier";
    static final String RUNNING_STATUS = "running";
    static final String STOPPED_STATUS = "stopped";
    static final String NOT_RUNNING_STATUS = "not-running";
    static final String PORT_CONFLICT_PREFIX = "PORT_CONFLICT";
    static final List<String> MODEL_COLLECTION_FIELDS = List.of("models", "data", "items");
    static final List<String> MODEL_IDENTITY_FIELDS = List.of("path", "modelKey", "model_key", "key");

    static final List<String> DAEMON_STATUS_COMMAND = List.of("lms", "daemon", "status", "--json");
    static final List<String> SERVER_STATUS_COMMAND = List.of("lms", "server", "status", "--json");
    static final List<String> MODEL_STATUS_COMMAND = List.of("lms", "ps", "--json");
    static final List<String> DAEMON_START_COMMAND = List.of("lms", "daemon", "up", "--json");

    private static final String SERVER_HOST = "127.0.0.1";
    private static final String SERVER_PORT = "1234";
    static final URI MODELS_HEALTH_URI = URI.create(
            "http://" + SERVER_HOST + ":" + SERVER_PORT + "/api/v1/models");
    static final List<String> SERVER_START_COMMAND = List.of(
            "lms", "server", "start", "--port", SERVER_PORT, "--bind", SERVER_HOST);

    private static final String PHI_ALIAS = "phi-router";
    private static final String PHI_MODEL_REFERENCE = "phi-3.5-mini-instruct";
    private static final String QWEN_ALIAS = "qwen-main";
    private static final String QWEN_MODEL_REFERENCE = "qwen/qwen3.5-9b";
    private static final String QWEN_MODEL_FAMILY = "qwen3.5-9b";

    static final ModelSpec PHI_MODEL = new ModelSpec(
            RuntimeComponent.PHI_ROUTER,
            PHI_ALIAS,
            PHI_MODEL_REFERENCE,
            List.of("lms", "load", PHI_MODEL_REFERENCE, "--gpu", "off",
                    "--context-length", "4096", "--identifier", PHI_ALIAS, "--yes"));
    static final ModelSpec QWEN_MODEL = new ModelSpec(
            RuntimeComponent.QWEN_MAIN,
            QWEN_ALIAS,
            QWEN_MODEL_FAMILY,
            List.of("lms", "load", QWEN_MODEL_REFERENCE, "--gpu", "max",
                    "--context-length", "32768", "--identifier", QWEN_ALIAS, "--yes"));

    static final List<RuntimeComponent> DEPENDENT_START_PRIORITY = List.of(
            RuntimeComponent.API_SERVER, RuntimeComponent.PHI_ROUTER, RuntimeComponent.QWEN_MAIN);

    private LmStudioRuntimeConstants() {
    }

    record ModelSpec(RuntimeComponent component, String alias, String family,
                     List<String> loadCommand) {
    }
}
