package com.fuad.evaluation.stability;

import com.openai.client.OpenAIClient;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Child-first for application classes; SDK and evaluation infrastructure remain shared. */
final class ProductionVariant implements AutoCloseable {
    private static final String RESEARCH = "com.fuad.assistant.skills.research.";
    private final URLClassLoader loader;
    private final OpenAIClient client;
    private final String model;
    private final Object general;
    private final Object semantic;
    private final Object guarded;
    private final Object plan;
    private final Object legacyBackend;
    private final Object legacyDepth;

    ProductionVariant(Path classes, OpenAIClient client, String model) throws Exception {
        this.client = client;
        this.model = model;
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null && name.startsWith("com.fuad.")
                            && !name.startsWith("com.fuad.evaluation.")) {
                        // Never silently substitute current application classes for historical ones.
                        loaded = findClass(name);
                    }
                    if (loaded == null) loaded = super.loadClass(name, false);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        general = classifier("com.fuad.assistant.skills.general.LocalGeneralComplexityClassifier");
        semantic = classifier("com.fuad.assistant.routing.LocalSemanticRouter");
        guarded = type("com.fuad.assistant.routing.GuardedSemanticRouter")
                .getConstructor(type("com.fuad.assistant.routing.SemanticRouter")).newInstance(semantic);
        Object currentPlan = null;
        try { currentPlan = classifier(RESEARCH + "LocalResearchPlanClassifier"); }
        catch (ClassNotFoundException absentInHistoricalVersion) { /* Original legacy contract. */ }
        plan = currentPlan;
        legacyBackend = plan == null ? type(RESEARCH + "DefaultResearchBackendClassifier")
                .getConstructor().newInstance() : null;
        legacyDepth = plan == null ? classifier(RESEARCH + "LocalResearchDepthClassifier") : null;
    }

    String general(String query) {
        return enumName(call(general, "classify", new Class<?>[]{String.class}, query))
                .equals("QWEN_LOCAL") ? "qwen_local" : "gpt";
    }

    String routing(String query, boolean protect) {
        return (String) call(call(protect ? guarded : semantic, "classify",
                new Class<?>[]{String.class}, query), "value", new Class<?>[]{});
    }

    Map<String, String> research(String query, String inheritedBackend, String dimension) {
        Map<String, String> result = new LinkedHashMap<>();
        if (plan != null) {
            Object actual = call(plan, "classify", new Class<?>[]{String.class}, query);
            result.put("access", enumName(call(actual, "access", new Class<?>[]{}))
                    .equals("MODEL_KNOWLEDGE") ? "knowledge" : "web");
            result.put("depth", enumName(call(actual, "depth", new Class<?>[]{})).toLowerCase());
        } else {
            if (!dimension.equals("depth")) {
                Class<?> backend = uncheckedType(RESEARCH + "ResearchBackend");
                Object inherited = inheritedBackend == null ? null
                        : enumConstant(backend, inheritedBackend.equals("qwen_local") ? "QWEN_LOCAL" : "GPT_WEB");
                result.put("access", enumName(call(legacyBackend, "classify",
                        new Class<?>[]{String.class, backend}, query, inherited))
                        .equals("QWEN_LOCAL") ? "knowledge" : "web");
            }
            if (!dimension.equals("backend")) result.put("depth", enumName(call(legacyDepth,
                    "classify", new Class<?>[]{String.class}, query)).toLowerCase());
        }
        return result;
    }

    String followUp(String query, String owner) throws Exception {
        Class<?> capability = type("com.fuad.enums.Capability");
        Class<?> skill = type("com.fuad.assistant.skills.Skill");
        Map<Object, Object> skills = new LinkedHashMap<>();
        Object unusedSkill = Proxy.newProxyInstance(loader, new Class<?>[]{skill}, (proxy, method, args) -> null);
        for (Object constant : capability.getEnumConstants()) skills.put(constant, unusedSkill);
        Class<?> registryType = type("com.fuad.assistant.skills.SkillRegistry");
        Object registry = registryType.getConstructor(Map.class).newInstance(skills);
        Object router = type("com.fuad.assistant.routing.AiSkillRouter")
                .getConstructor(type("com.fuad.assistant.routing.SemanticRouter"), registryType)
                .newInstance(semantic, registry);
        Class<?> snapshotType = type("com.fuad.assistant.session.ConversationSnapshot");
        Object snapshot = snapshotType.getConstructor(capability, String.class, String.class, String.class)
                .newInstance(enumConstant(capability, owner), "Explícame AES-GCM",
                        "Es un esquema de cifrado autenticado.", null);
        Object route = call(router, "routeFollowUp", new Class<?>[]{String.class, snapshotType}, query, snapshot);
        return (String) call(call(route, "capability", new Class<?>[]{}), "value", new Class<?>[]{});
    }

    Map<String, String> prompts() throws Exception {
        Map<String, String> prompts = new LinkedHashMap<>();
        for (Object target : new Object[]{general, semantic, plan == null ? legacyDepth : plan}) {
            var field = target.getClass().getDeclaredField("SYSTEM_PROMPT");
            field.setAccessible(true);
            prompts.put(target.getClass().getSimpleName(), (String) field.get(null));
            try {
                var reminder = target.getClass().getDeclaredField("CLASSIFICATION_REMINDER");
                reminder.setAccessible(true);
                prompts.put(target.getClass().getSimpleName() + "-user-reminder", (String) reminder.get(null));
            } catch (NoSuchFieldException absentInBaseline) { /* Original user contract. */ }
            try {
                var retry = target.getClass().getDeclaredField("RETRY_INSTRUCTION");
                retry.setAccessible(true);
                prompts.put(target.getClass().getSimpleName() + "-retry", (String) retry.get(null));
            } catch (NoSuchFieldException noRetryField) { /* General retry lives in its inference adapter. */ }
        }
        var generalRetry = type("com.fuad.assistant.skills.general.OpenAiGeneralBackendInference")
                .getDeclaredField("RETRY_INSTRUCTION");
        generalRetry.setAccessible(true);
        prompts.put("General-retry", (String) generalRetry.get(null));
        try {
            var reminder = type("com.fuad.assistant.skills.general.OpenAiGeneralBackendInference")
                    .getDeclaredField("CLASSIFICATION_REMINDER");
            reminder.setAccessible(true);
            prompts.put("General-user-reminder", (String) reminder.get(null));
        } catch (NoSuchFieldException absentInBaseline) { /* Original user contract. */ }
        return prompts;
    }

    boolean usesPlan() { return plan != null; }
    private Object classifier(String name) throws Exception {
        return type(name).getConstructor(OpenAIClient.class, String.class).newInstance(client, model);
    }
    private Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass(name); }
    private Class<?> uncheckedType(String name) {
        try { return type(name); } catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
    private static String enumName(Object value) { return ((Enum<?>) value).name(); }
    private static Object call(Object target, String method, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(method, types).invoke(target, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException(error.getCause());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @Override public void close() throws IOException { loader.close(); }
}
