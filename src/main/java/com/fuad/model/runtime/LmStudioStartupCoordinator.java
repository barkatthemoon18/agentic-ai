package com.fuad.model.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.config.AppConfig;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class LmStudioStartupCoordinator implements AutoCloseable {
    private static final Duration INFRASTRUCTURE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MODEL_LOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final List<Duration> DEFAULT_BACKOFFS = List.of(
            Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(120));

    private final LmsCommandRunner commandRunner;
    private final ServerHealthProbe healthProbe;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final List<Duration> backoffs;
    private final Map<RuntimeComponent, Recovery> recoveries = new EnumMap<>(RuntimeComponent.class);
    private final List<Consumer<ModelRuntimeSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock mutatingCommandLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public LmStudioStartupCoordinator() {
        this(new ProcessLmsCommandRunner(),
                new HttpLmStudioHealthProbe(URI.create("http://127.0.0.1:1234/api/v1/models"),
                        AppConfig.LOCAL_AI_API_KEY),
                new ObjectMapper(),
                Executors.newScheduledThreadPool(4, runnable -> {
                    Thread thread = new Thread(runnable, "lms-startup");
                    thread.setDaemon(true);
                    return thread;
                }), DEFAULT_BACKOFFS);
    }

    LmStudioStartupCoordinator(LmsCommandRunner commandRunner,
                               ServerHealthProbe healthProbe,
                               ObjectMapper objectMapper,
                               ScheduledExecutorService scheduler,
                               List<Duration> backoffs) {
        this.commandRunner = Objects.requireNonNull(commandRunner);
        this.healthProbe = Objects.requireNonNull(healthProbe);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.backoffs = List.copyOf(backoffs);
        if (this.backoffs.size() != 3) {
            throw new IllegalArgumentException("Exactly three retry delays are required");
        }
        for (RuntimeComponent component : RuntimeComponent.values()) {
            recoveries.put(component, new Recovery(component));
        }
    }

    public void startAsync() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        for (RuntimeComponent component : RuntimeComponent.values()) {
            beginSeries(component, false);
        }
    }

    public void retry(RuntimeComponent component) {
        Objects.requireNonNull(component, "component cannot be null");
        if (closed.get()) {
            return;
        }
        if (component != RuntimeComponent.LMS_DAEMON
                && state(RuntimeComponent.LMS_DAEMON) == ComponentState.FAILED) {
            beginSeries(RuntimeComponent.LMS_DAEMON, true);
        }
        beginSeries(component, true);
    }

    public AutoCloseable subscribe(Consumer<ModelRuntimeSnapshot> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
        listener.accept(snapshot());
        return () -> listeners.remove(listener);
    }

    public ModelRuntimeSnapshot snapshot() {
        Map<RuntimeComponent, ComponentSnapshot> components = new EnumMap<>(RuntimeComponent.class);
        for (Map.Entry<RuntimeComponent, Recovery> entry : recoveries.entrySet()) {
            Recovery recovery = entry.getValue();
            synchronized (recovery) {
                components.put(entry.getKey(), recovery.snapshot());
            }
        }
        return new ModelRuntimeSnapshot(deriveState(components), components);
    }

    public boolean isPhiUsable() {
        return snapshot().isPhiUsable();
    }

    public boolean isQwenUsable() {
        return snapshot().isQwenUsable();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Recovery recovery : recoveries.values()) {
            synchronized (recovery) {
                recovery.generation++;
                cancelScheduled(recovery);
                recovery.commandGeneration.cancel();
            }
            commandRunner.cancel(recovery.component);
        }
        scheduler.shutdownNow();
        commandRunner.close();
        listeners.clear();
    }

    private void beginSeries(RuntimeComponent component, boolean cancelRunning) {
        Recovery recovery = recoveries.get(component);
        long generation;
        synchronized (recovery) {
            recovery.commandGeneration.cancel();
            recovery.generation++;
            generation = recovery.generation;
            cancelScheduled(recovery);
            recovery.retriesUsed = 0;
            recovery.state = ComponentState.CHECKING;
            recovery.detail = "Comprobando disponibilidad";
            recovery.blockedBy = null;
            recovery.nextRetryAt = null;
            recovery.commandGeneration = new CommandGeneration();
        }
        if (cancelRunning) {
            commandRunner.cancel(component);
        }
        publish();
        schedule(component, generation, Duration.ZERO);
    }

    private void schedule(RuntimeComponent component, long generation, Duration delay) {
        Recovery recovery = recoveries.get(component);
        synchronized (recovery) {
            if (!current(recovery, generation)) {
                return;
            }
            recovery.scheduled = scheduler.schedule(
                    () -> attempt(component, generation), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void attempt(RuntimeComponent component, long generation) {
        Recovery recovery = recoveries.get(component);
        if (!current(recovery, generation) || closed.get()) {
            return;
        }
        try {
            recovery.operationLock.lockInterruptibly();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (!current(recovery, generation) || closed.get()) {
                return;
            }
            RuntimeComponent dependency = missingDependency(component);
            if (dependency != null) {
                update(recovery, generation, ComponentState.CHECKING,
                        "Esperando a " + displayName(dependency), dependency, null);
                return;
            }
            update(recovery, generation, ComponentState.CHECKING,
                    "Comprobando disponibilidad", null, null);

            ProbeResult initial = probe(component);
            if (!current(recovery, generation)) {
                return;
            }
            if (initial.outcome == ProbeOutcome.SATISFIED) {
                markReady(recovery, generation, initial.detail);
                return;
            }
            if (initial.outcome == ProbeOutcome.INDETERMINATE || !initial.mutationAllowed) {
                failAttempt(recovery, generation, initial.detail);
                return;
            }

            mutatingCommandLock.lockInterruptibly();
            try {
                if (!current(recovery, generation) || closed.get()) {
                    return;
                }
                ProbeResult preCheck = probe(component);
                if (!current(recovery, generation)) {
                    return;
                }
                if (preCheck.outcome == ProbeOutcome.SATISFIED) {
                    markReady(recovery, generation, preCheck.detail);
                    return;
                }
                if (preCheck.outcome == ProbeOutcome.INDETERMINATE || !preCheck.mutationAllowed) {
                    failAttempt(recovery, generation, preCheck.detail);
                    return;
                }
                CommandGeneration commandGeneration;
                synchronized (recovery) {
                    if (!current(recovery, generation)) {
                        return;
                    }
                    commandGeneration = recovery.commandGeneration;
                }
                update(recovery, generation,
                        isModel(component) ? ComponentState.LOADING : ComponentState.CHECKING,
                        mutationDescription(component), null, null);
                if (!current(recovery, generation)) {
                    return;
                }
                CommandResult command = runMutation(component, commandGeneration);
                if (!current(recovery, generation)) {
                    return;
                }
                ProbeResult postCondition = awaitPostCondition(component, command.timedOut());
                if (!current(recovery, generation)) {
                    return;
                }
                if (postCondition.outcome == ProbeOutcome.SATISFIED) {
                    String detail = command.timedOut()
                            ? postCondition.detail + " (confirmado después del timeout)"
                            : postCondition.detail;
                    markReady(recovery, generation, detail);
                    return;
                }
                String commandDetail = command.timedOut()
                        ? "La operación excedió el tiempo límite"
                        : command.exitCode() == 0 ? "La postcondición no se cumplió"
                        : "El comando terminó con código " + command.exitCode();
                failAttempt(recovery, generation, commandDetail + ": " + postCondition.detail);
            }
            finally {
                mutatingCommandLock.unlock();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (RuntimeException e) {
            failAttempt(recovery, generation, "Error de recuperación: " + safeMessage(e));
        }
        finally {
            recovery.operationLock.unlock();
        }
    }

    private ProbeResult probe(RuntimeComponent component) {
        return switch (component) {
            case LMS_DAEMON -> probeDaemon();
            case API_SERVER -> probeServer();
            case PHI_ROUTER -> probeModel(new ModelSpec("phi-router", "phi-3.5-mini-instruct"));
            case QWEN_MAIN -> probeModel(new ModelSpec("qwen-main", "qwen3.5-9b"));
        };
    }

    private ProbeResult probeDaemon() {
        CommandResult result = runProbe(RuntimeComponent.LMS_DAEMON,
                List.of("lms", "daemon", "status", "--json"));
        if (result.timedOut() || result.exitCode() != 0) {
            return ProbeResult.indeterminate("No se pudo comprobar el daemon: " + result.output());
        }
        try {
            String status = objectMapper.readTree(result.output()).path("status").asText();
            return "running".equalsIgnoreCase(status)
                    ? ProbeResult.ready("Daemon de LM Studio activo")
                    : ProbeResult.missing("Daemon de LM Studio detenido", true);
        }
        catch (Exception e) {
            return ProbeResult.indeterminate("Estado inválido del daemon");
        }
    }

    private ProbeResult probeServer() {
        CommandResult result = runProbe(RuntimeComponent.API_SERVER,
                List.of("lms", "server", "status", "--json"));
        ServerHealthProbe.Result health = healthProbe.check();
        if (result.timedOut() || result.exitCode() != 0) {
            return ProbeResult.indeterminate("No se pudo consultar el estado LMS del servidor");
        }
        Boolean running = parseServerRunning(result.output());
        if (running == null) {
            return ProbeResult.indeterminate("Estado LMS del servidor inválido");
        }
        if (running && health.healthy()) {
            return ProbeResult.ready("Servidor HTTP de LM Studio saludable");
        }
        if (running) {
            return ProbeResult.missing("UNHEALTHY: " + health.detail(), false);
        }
        if (health.reachable()) {
            return ProbeResult.missing("PORT_CONFLICT: " + health.detail(), false);
        }
        return ProbeResult.missing("STOPPED: servidor HTTP detenido", true);
    }

    private ProbeResult probeModel(ModelSpec spec) {
        RuntimeComponent component = "phi-router".equals(spec.alias)
                ? RuntimeComponent.PHI_ROUTER : RuntimeComponent.QWEN_MAIN;
        CommandResult result = runProbe(component, List.of("lms", "ps", "--json"));
        if (result.timedOut() || result.exitCode() != 0) {
            return ProbeResult.indeterminate("No se pudieron consultar los modelos cargados");
        }
        try {
            JsonNode root = objectMapper.readTree(result.output());
            JsonNode models = root != null && root.isArray() ? root
                    : root == null ? null : firstArray(root, "models", "data", "items");
            if (models == null || !models.isArray()) {
                return ProbeResult.indeterminate("Respuesta inválida de lms ps --json");
            }
            for (JsonNode model : models) {
                if (!spec.alias.equals(model.path("identifier").asText())) {
                    continue;
                }
                ModelIdentity identity = identity(model, spec);
                if (identity == ModelIdentity.MISMATCH) {
                    return ProbeResult.missing("El alias " + spec.alias
                            + " pertenece a otro modelo", false);
                }
                return ProbeResult.ready(spec.alias + " activo"
                        + (identity == ModelIdentity.UNKNOWN ? " (identidad no verificable)" : ""));
            }
            return ProbeResult.missing(spec.alias + " no está cargado", true);
        }
        catch (Exception e) {
            return ProbeResult.indeterminate("Respuesta inválida de lms ps --json");
        }
    }

    private CommandResult runProbe(RuntimeComponent component, List<String> command) {
        try {
            return commandRunner.run(component, command, PROBE_TIMEOUT);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "Comprobación interrumpida", true);
        }
    }

    private CommandResult runMutation(RuntimeComponent component, CommandGeneration generation)
            throws InterruptedException {
        return switch (component) {
            case LMS_DAEMON -> commandRunner.run(component,
                    List.of("lms", "daemon", "up", "--json"), INFRASTRUCTURE_TIMEOUT, generation);
            case API_SERVER -> commandRunner.run(component,
                    List.of("lms", "server", "start", "--port", "1234", "--bind", "127.0.0.1"),
                    INFRASTRUCTURE_TIMEOUT, generation);
            case PHI_ROUTER -> commandRunner.run(component, List.of(
                    "lms", "load", "phi-3.5-mini-instruct", "--gpu", "off",
                    "--context-length", "4096", "--identifier", "phi-router", "--yes"),
                    MODEL_LOAD_TIMEOUT, generation);
            case QWEN_MAIN -> commandRunner.run(component, List.of(
                    "lms", "load", "qwen/qwen3.5-9b", "--gpu", "max",
                    "--context-length", "32768", "--identifier", "qwen-main", "--yes"),
                    MODEL_LOAD_TIMEOUT, generation);
        };
    }

    private ProbeResult awaitPostCondition(RuntimeComponent component, boolean commandTimedOut)
            throws InterruptedException {
        Duration wait = commandTimedOut || isModel(component)
                ? Duration.ZERO : INFRASTRUCTURE_TIMEOUT;
        Instant deadline = Instant.now().plus(wait);
        ProbeResult result;
        do {
            result = probe(component);
            if (result.outcome == ProbeOutcome.SATISFIED
                    || result.outcome == ProbeOutcome.INDETERMINATE
                    || (!result.mutationAllowed && component == RuntimeComponent.API_SERVER
                    && result.detail.startsWith("PORT_CONFLICT"))) {
                return result;
            }
            if (!Instant.now().isBefore(deadline)) {
                return result;
            }
            Thread.sleep(500);
        }
        while (true);
    }

    private void failAttempt(Recovery recovery, long generation, String detail) {
        Duration delay;
        boolean terminal = false;
        synchronized (recovery) {
            if (!current(recovery, generation)) {
                return;
            }
            if (recovery.retriesUsed >= backoffs.size()) {
                recovery.state = ComponentState.FAILED;
                recovery.detail = detail;
                recovery.nextRetryAt = null;
                recovery.scheduled = null;
                terminal = true;
                delay = Duration.ZERO;
            }
            else {
                delay = backoffs.get(recovery.retriesUsed);
                recovery.retriesUsed++;
                recovery.state = ComponentState.RETRY_WAIT;
                recovery.detail = detail;
                recovery.nextRetryAt = Instant.now().plus(delay);
                recovery.blockedBy = null;
            }
        }
        publish();
        if (!terminal) {
            schedule(recovery.component, generation, delay);
        }
    }

    private void markReady(Recovery recovery, long generation, String detail) {
        synchronized (recovery) {
            if (!current(recovery, generation)) {
                return;
            }
            cancelScheduled(recovery);
            recovery.commandGeneration.cancel();
            recovery.state = ComponentState.READY;
            recovery.detail = detail;
            recovery.blockedBy = null;
            recovery.nextRetryAt = null;
            recovery.generation++;
        }
        publish();
        if (recovery.component == RuntimeComponent.LMS_DAEMON) {
            resumeDaemonDependents();
        }
    }

    private void resumeDaemonDependents() {
        List<RuntimeComponent> priority = List.of(
                RuntimeComponent.API_SERVER, RuntimeComponent.PHI_ROUTER, RuntimeComponent.QWEN_MAIN);
        long delayMillis = 0;
        for (RuntimeComponent component : priority) {
            Recovery recovery = recoveries.get(component);
            long generation;
            synchronized (recovery) {
                if (recovery.state != ComponentState.CHECKING
                        || recovery.blockedBy != RuntimeComponent.LMS_DAEMON) {
                    continue;
                }
                recovery.blockedBy = null;
                generation = recovery.generation;
            }
            schedule(component, generation, Duration.ofMillis(delayMillis));
            delayMillis += 50;
        }
    }

    private RuntimeComponent missingDependency(RuntimeComponent component) {
        if (component == RuntimeComponent.LMS_DAEMON) {
            return null;
        }
        return state(RuntimeComponent.LMS_DAEMON) == ComponentState.READY
                ? null : RuntimeComponent.LMS_DAEMON;
    }

    private ComponentState state(RuntimeComponent component) {
        Recovery recovery = recoveries.get(component);
        synchronized (recovery) {
            return recovery.state;
        }
    }

    private void update(Recovery recovery, long generation, ComponentState state,
                        String detail, RuntimeComponent blockedBy, Instant nextRetryAt) {
        synchronized (recovery) {
            if (!current(recovery, generation)) {
                return;
            }
            recovery.state = state;
            recovery.detail = detail;
            recovery.blockedBy = blockedBy;
            recovery.nextRetryAt = nextRetryAt;
        }
        publish();
    }

    private boolean current(Recovery recovery, long generation) {
        synchronized (recovery) {
            return !closed.get() && recovery.generation == generation
                    && recovery.state != ComponentState.READY;
        }
    }

    private void publish() {
        ModelRuntimeSnapshot snapshot = snapshot();
        for (Consumer<ModelRuntimeSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            }
            catch (RuntimeException e) {
                System.err.println("Model runtime listener failed: " + safeMessage(e));
            }
        }
    }

    private static RuntimeState deriveState(Map<RuntimeComponent, ComponentSnapshot> components) {
        if (components.values().stream().anyMatch(value -> value.state() == ComponentState.FAILED)) {
            return RuntimeState.DEGRADED;
        }
        boolean daemon = ready(components, RuntimeComponent.LMS_DAEMON);
        boolean server = ready(components, RuntimeComponent.API_SERVER);
        boolean phi = ready(components, RuntimeComponent.PHI_ROUTER);
        boolean qwen = ready(components, RuntimeComponent.QWEN_MAIN);
        if (daemon && server && phi && qwen) {
            return RuntimeState.READY;
        }
        return daemon && server && phi ? RuntimeState.PARTIALLY_READY : RuntimeState.STARTING;
    }

    private static boolean ready(Map<RuntimeComponent, ComponentSnapshot> components,
                                 RuntimeComponent component) {
        ComponentSnapshot snapshot = components.get(component);
        return snapshot != null && snapshot.state() == ComponentState.READY;
    }

    private Boolean parseServerRunning(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("running").isBoolean()) {
                return root.path("running").asBoolean();
            }
            String status = root.path("status").asText("");
            if ("running".equalsIgnoreCase(status)) {
                return true;
            }
            if ("stopped".equalsIgnoreCase(status) || "not-running".equalsIgnoreCase(status)) {
                return false;
            }
        }
        catch (Exception ignored) {
            // The caller reports this as an indeterminate server state.
        }
        return null;
    }

    private static JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode candidate = root.path(name);
            if (candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private static ModelIdentity identity(JsonNode model, ModelSpec spec) {
        List<String> candidates = new ArrayList<>();
        for (String field : List.of("path", "modelKey", "model_key", "key")) {
            if (model.path(field).isTextual() && !model.path(field).asText().isBlank()) {
                candidates.add(normalize(model.path(field).asText()));
            }
        }
        if (candidates.isEmpty()) {
            return ModelIdentity.UNKNOWN;
        }
        String expected = normalize(spec.family);
        return candidates.stream().anyMatch(value -> value.contains(expected))
                ? ModelIdentity.MATCH : ModelIdentity.MISMATCH;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replace('\\', '/').replace('_', '-');
    }

    private static boolean isModel(RuntimeComponent component) {
        return component == RuntimeComponent.PHI_ROUTER || component == RuntimeComponent.QWEN_MAIN;
    }

    private static String mutationDescription(RuntimeComponent component) {
        return switch (component) {
            case LMS_DAEMON -> "Iniciando daemon de LM Studio";
            case API_SERVER -> "Iniciando servidor HTTP de LM Studio";
            case PHI_ROUTER -> "Cargando phi-router";
            case QWEN_MAIN -> "Cargando qwen-main";
        };
    }

    private static String displayName(RuntimeComponent component) {
        return switch (component) {
            case LMS_DAEMON -> "LM Studio daemon";
            case API_SERVER -> "API server";
            case PHI_ROUTER -> "phi-router";
            case QWEN_MAIN -> "qwen-main";
        };
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static void cancelScheduled(Recovery recovery) {
        if (recovery.scheduled != null) {
            recovery.scheduled.cancel(false);
            recovery.scheduled = null;
        }
    }

    private record ModelSpec(String alias, String family) {
    }

    private enum ProbeOutcome {
        SATISFIED,
        NOT_SATISFIED,
        INDETERMINATE
    }

    private record ProbeResult(ProbeOutcome outcome, String detail, boolean mutationAllowed) {
        static ProbeResult ready(String detail) {
            return new ProbeResult(ProbeOutcome.SATISFIED, detail, false);
        }

        static ProbeResult missing(String detail, boolean mutationAllowed) {
            return new ProbeResult(ProbeOutcome.NOT_SATISFIED, detail, mutationAllowed);
        }

        static ProbeResult indeterminate(String detail) {
            return new ProbeResult(ProbeOutcome.INDETERMINATE, detail, false);
        }
    }

    private static final class Recovery {
        private final RuntimeComponent component;
        private final ReentrantLock operationLock = new ReentrantLock();
        private ComponentState state = ComponentState.CHECKING;
        private String detail = "Pendiente";
        private RuntimeComponent blockedBy;
        private int retriesUsed;
        private Instant nextRetryAt;
        private long generation;
        private ScheduledFuture<?> scheduled;
        private CommandGeneration commandGeneration = new CommandGeneration();

        private Recovery(RuntimeComponent component) {
            this.component = component;
        }

        private ComponentSnapshot snapshot() {
            return new ComponentSnapshot(component, state, detail, blockedBy,
                    retriesUsed, nextRetryAt, generation);
        }
    }
}
