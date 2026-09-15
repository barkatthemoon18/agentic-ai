package com.fuad.assistant.skills.os;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class ApplicationCatalogIdentity {
    private static final Comparator<String> NULLABLE_TEXT = Comparator.nullsLast(Comparator.naturalOrder());
    static final Comparator<ApplicationDefinition> STABLE_ORDER = Comparator
            .comparing((ApplicationDefinition app) -> normalizedOrNull(app.getDisplayName()), NULLABLE_TEXT)
            .thenComparing(ApplicationDefinition::getDisplayName, NULLABLE_TEXT)
            .thenComparing(ApplicationCatalogIdentity::stableKey);

    private ApplicationCatalogIdentity() { }

    static List<ApplicationDefinition> canonicalize(Collection<ApplicationDefinition> definitions) {
        Map<String, List<ApplicationDefinition>> groups = new LinkedHashMap<>();
        for (ApplicationDefinition definition : definitions) {
            if (definition == null || definition.getDisplayName() == null
                    || definition.getDisplayName().isBlank()) continue;
            groups.computeIfAbsent(stableKey(definition), ignored -> new ArrayList<>()).add(definition);
        }
        List<ApplicationDefinition> result = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> merge(entry.getKey(), entry.getValue()))
                .sorted(STABLE_ORDER)
                .toList();
        return List.copyOf(result);
    }

    static String stableKey(ApplicationDefinition definition) {
        String id = canonicalId(definition.getId());
        return id == null ? "fingerprint:" + definitionFingerprint(definition) : "appid:" + id;
    }

    static String definitionFingerprint(ApplicationDefinition definition) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.string(1, ApplicationNames.normalize(definition.getDisplayName()));
        writer.unorderedStrings(2, normalizedSet(definition.getAliases()));
        writer.object(3, runtimeIdentityBytes(definition));
        return writer.digest();
    }

    static String runtimeIdentityFingerprint(ApplicationDefinition definition) {
        return sha256(runtimeIdentityBytes(definition));
    }

    private static ApplicationDefinition merge(String stableKey, List<ApplicationDefinition> group) {
        if (group.size() == 1) return group.getFirst();
        List<ApplicationDefinition> ordered = group.stream().sorted(STABLE_ORDER
                .thenComparing(ApplicationCatalogIdentity::definitionFingerprint)).toList();
        ApplicationDefinition base = ordered.getFirst();
        if (stableKey.startsWith("appid:")) {
            String expected = runtimeIdentityFingerprint(base);
            boolean conflict = ordered.stream()
                    .anyMatch(definition -> !runtimeIdentityFingerprint(definition).equals(expected));
            if (conflict) {
                throw new IllegalArgumentException(
                        "Conflicting runtime identity for application " + stableKey.substring("appid:".length()));
            }
        }
        String displayName = ordered.stream().map(ApplicationDefinition::getDisplayName)
                .min(Comparator.comparing(ApplicationNames::normalize)
                        .thenComparing(Comparator.naturalOrder())).orElseThrow();
        Set<String> aliases = new TreeSet<>(Comparator.comparing(ApplicationNames::normalize)
                .thenComparing(Comparator.naturalOrder()));
        ordered.forEach(definition -> {
            aliases.addAll(definition.getAliases());
            if (!definition.getDisplayName().equals(displayName)) aliases.add(definition.getDisplayName());
        });
        String id = ordered.stream().map(ApplicationDefinition::getId).filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty())
                .min(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .orElse(null);
        return new ApplicationDefinition(id, displayName, aliases,
                base.getOpenCommand(), base.getProcessIdentity());
    }

    private static byte[] runtimeIdentityBytes(ApplicationDefinition definition) {
        CanonicalWriter writer = new CanonicalWriter();
        List<String> command = new ArrayList<>(definition.getOpenCommand());
        if (!command.isEmpty()) {
            command.set(0, ApplicationRuntimeResolver.normalizePath(command.getFirst()));
        }
        writer.orderedStrings(1, command);
        ApplicationProcessIdentity identity = definition.getProcessIdentity();
        writer.unorderedStrings(2, identity.executablePaths().stream()
                .map(ApplicationRuntimeResolver::normalizePath).toList());
        writer.unorderedStrings(3, identity.packageRoots().stream()
                .map(ApplicationRuntimeResolver::normalizeRoot).toList());
        writer.unorderedStrings(4, identity.processNames().stream()
                .map(ApplicationRuntimeResolver::normalizeName).toList());
        writer.unorderedStrings(5, identity.trustedProcessNamesWhenPathUnavailable().stream()
                .map(ApplicationRuntimeResolver::normalizeName).toList());
        writer.unorderedGroups(6, identity.commandLineArgumentSets().stream()
                .map(List::copyOf).toList(), false);
        writer.unorderedGroups(7, identity.exactCommandLineArgumentSets(), true);
        writer.string(8, canonicalId(identity.hostApplicationId()));
        writer.unorderedObjects(9, identity.windowSignatures().stream()
                .map(ApplicationCatalogIdentity::windowSignatureBytes).toList());
        writer.bool(10, identity.windowAssociationEnabled());
        return writer.bytes();
    }

    private static byte[] windowSignatureBytes(ApplicationWindowSignature signature) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.string(1, ApplicationRuntimeResolver.normalizeName(signature.className()));
        writer.string(2, signature.titlePattern());
        return writer.bytes();
    }

    private static List<String> normalizedSet(Collection<String> values) {
        return values.stream().map(ApplicationNames::normalize).toList();
    }

    private static String normalizedOrNull(String value) {
        return value == null ? null : ApplicationNames.normalize(value);
    }

    static String canonicalId(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static final class CanonicalWriter {
        private static final byte STRING = 1;
        private static final byte BOOLEAN = 2;
        private static final byte ORDERED_STRINGS = 3;
        private static final byte UNORDERED_STRINGS = 4;
        private static final byte OBJECT = 5;
        private static final byte UNORDERED_OBJECTS = 6;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream output = new DataOutputStream(bytes);

        void string(int tag, String value) {
            try {
                field(tag, STRING);
                writeString(value);
            }
            catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        void bool(int tag, boolean value) {
            try {
                field(tag, BOOLEAN);
                output.writeBoolean(value);
            }
            catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        void orderedStrings(int tag, Collection<String> values) {
            writeStrings(tag, ORDERED_STRINGS, List.copyOf(values));
        }

        void unorderedStrings(int tag, Collection<String> values) {
            writeStrings(tag, UNORDERED_STRINGS, values.stream().distinct().sorted().toList());
        }

        void unorderedGroups(int tag, Collection<? extends Collection<String>> groups,
                             boolean preserveInnerOrder) {
            List<byte[]> encoded = groups.stream().map(group -> {
                List<String> ordered = preserveInnerOrder
                        ? List.copyOf(group)
                        : group.stream().distinct().sorted().toList();
                CanonicalWriter nested = new CanonicalWriter();
                nested.orderedStrings(1, ordered);
                return nested.bytes();
            }).toList();
            unorderedObjects(tag, encoded);
        }

        void object(int tag, byte[] value) {
            try {
                field(tag, OBJECT);
                writeBytes(value);
            }
            catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        void unorderedObjects(int tag, Collection<byte[]> values) {
            Map<String, byte[]> ordered = new java.util.TreeMap<>();
            values.forEach(value -> ordered.put(HexFormat.of().formatHex(value), value));
            try {
                field(tag, UNORDERED_OBJECTS);
                output.writeInt(ordered.size());
                for (byte[] value : ordered.values()) writeBytes(value);
            }
            catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        String digest() {
            return sha256(bytes());
        }

        byte[] bytes() {
            return bytes.toByteArray();
        }

        private void writeStrings(int tag, byte type, List<String> values) {
            try {
                field(tag, type);
                output.writeInt(values.size());
                for (String value : values) writeString(value);
            }
            catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        private void field(int tag, byte type) throws IOException {
            output.writeInt(tag);
            output.writeByte(type);
        }

        private void writeString(String value) throws IOException {
            output.writeBoolean(value != null);
            if (value == null) return;
            writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }

        private void writeBytes(byte[] value) throws IOException {
            output.writeInt(value.length);
            output.write(value);
        }
    }
}
