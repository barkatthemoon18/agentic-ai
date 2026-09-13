package com.fuad.assistant.skills.os;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.WbemcliUtil;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WmiWindowsProcessSnapshotSource implements WindowsProcessSnapshotSource {
    private static final int QUERY_TIMEOUT_MILLIS = 3_000;
    private static final Pattern CIM_DATETIME = Pattern.compile(
            "^(\\d{14})\\.(\\d{6})([+-])(\\d{3})$");
    private static final DateTimeFormatter CIM_LOCAL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final WmiProcessQuery query;

    WmiWindowsProcessSnapshotSource() {
        this(new JnaWmiProcessQuery());
    }

    WmiWindowsProcessSnapshotSource(WmiProcessQuery query) {
        this.query = query;
    }

    @Override
    public ProcessSnapshotBatch snapshotAll() {
        try {
            List<WmiProcessRow> rows = query.query(null);
            List<WindowsProcessSnapshot> snapshots = new ArrayList<>();
            Set<Long> observedPids = new HashSet<>();
            for (WmiProcessRow row : rows) {
                if (row.pid() == 0) continue; // System Idle Process is not an actionable application process.
                if (row.pid() < 0 || !observedPids.add(row.pid())) {
                    return ProcessSnapshotBatch.failed(ProcessObservationFailure.INVALID_RESPONSE,
                            "WMI returned an invalid or duplicate process id");
                }
                snapshots.add(toSnapshot(row));
            }
            return ProcessSnapshotBatch.complete(snapshots);
        }
        catch (Exception error) {
            return ProcessSnapshotBatch.failed(failure(error), safeMessage(error));
        }
    }

    @Override
    public ProcessSnapshotLookup snapshot(long pid) {
        if (pid <= 0) {
            return ProcessSnapshotLookup.failed(ProcessObservationFailure.INVALID_RESPONSE,
                    "pid must be positive");
        }
        try {
            List<WmiProcessRow> rows = query.query(pid);
            if (rows.isEmpty()) return ProcessSnapshotLookup.notFound();
            if (rows.size() != 1 || rows.getFirst().pid() != pid) {
                return ProcessSnapshotLookup.failed(ProcessObservationFailure.INVALID_RESPONSE,
                        "WMI returned an inconsistent process lookup");
            }
            return ProcessSnapshotLookup.found(toSnapshot(rows.getFirst()));
        }
        catch (Exception error) {
            return ProcessSnapshotLookup.failed(failure(error), safeMessage(error));
        }
    }

    static Optional<Instant> parseCreationTime(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = CIM_DATETIME.matcher(value.trim());
        if (!matcher.matches()) return Optional.empty();
        try {
            LocalDateTime local = LocalDateTime.parse(matcher.group(1), CIM_LOCAL_TIME)
                    .withNano(Integer.parseInt(matcher.group(2)) * 1_000);
            int offsetMinutes = Integer.parseInt(matcher.group(4));
            if (matcher.group(3).equals("-")) offsetMinutes = -offsetMinutes;
            return Optional.of(local.toInstant(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)));
        }
        catch (DateTimeException | NumberFormatException error) {
            return Optional.empty();
        }
    }

    private WindowsProcessSnapshot toSnapshot(WmiProcessRow row) {
        Optional<Path> executablePath;
        try {
            executablePath = row.executablePath() == null || row.executablePath().isBlank()
                    ? Optional.empty() : Optional.of(Path.of(row.executablePath().trim()));
        }
        catch (RuntimeException error) {
            executablePath = Optional.empty();
        }
        List<String> tokens = WindowsCommandLineTokenizer.tokenize(row.commandLine());
        boolean argumentsAvailable = !tokens.isEmpty()
                && executableTokenMatches(tokens.getFirst(), executablePath, row.name());
        List<String> arguments = argumentsAvailable
                ? List.copyOf(tokens.subList(1, tokens.size())) : List.of();
        return new WindowsProcessSnapshot(row.pid(), row.parentProcessId(), parseCreationTime(row.creationDate()), executablePath,
                row.name(), arguments, argumentsAvailable);
    }

    private boolean executableTokenMatches(String token, Optional<Path> executablePath, String executableName) {
        String normalizedToken = normalizePath(token);
        if (executablePath.isPresent()
                && normalizedToken.equals(normalizePath(executablePath.get().toString()))) return true;
        int separator = normalizedToken.lastIndexOf('\\');
        String tokenName = separator >= 0 ? normalizedToken.substring(separator + 1) : normalizedToken;
        return executableName != null && tokenName.equals(executableName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('/', '\\').toLowerCase(java.util.Locale.ROOT);
    }

    private ProcessObservationFailure failure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException) return ProcessObservationFailure.TIMEOUT;
            if (current instanceof COMException comError) {
                return comError.matchesErrorCode(WinError.E_ACCESSDENIED)
                        ? ProcessObservationFailure.ACCESS_DENIED : ProcessObservationFailure.COM_FAILURE;
            }
            current = current.getCause();
        }
        return error instanceof InvalidWmiResponseException
                ? ProcessObservationFailure.INVALID_RESPONSE : ProcessObservationFailure.UNKNOWN;
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    @FunctionalInterface
    interface WmiProcessQuery {
        List<WmiProcessRow> query(Long pid) throws Exception;
    }

    record WmiProcessRow(long pid, long parentProcessId, String creationDate, String executablePath, String name,
                         String commandLine) {
        WmiProcessRow(long pid, String creationDate, String executablePath, String name, String commandLine) {
            this(pid, 0, creationDate, executablePath, name, commandLine);
        }
    }

    private enum ProcessProperty { PROCESSID, PARENTPROCESSID, CREATIONDATE, EXECUTABLEPATH, NAME, COMMANDLINE }

    private static final class JnaWmiProcessQuery implements WmiProcessQuery {
        @Override
        public List<WmiProcessRow> query(Long pid) throws TimeoutException {
            HRESULT initialization = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
            boolean ownsInitialization = !COMUtils.FAILED(initialization);
            if (!ownsInitialization && initialization.intValue() != WinError.RPC_E_CHANGED_MODE) {
                throw new COMException("Unable to initialize COM for WMI", initialization);
            }
            try {
                String wmiClass = pid == null ? "Win32_Process"
                        : "Win32_Process WHERE ProcessId = " + pid;
                WbemcliUtil.WmiResult<ProcessProperty> result =
                        new WbemcliUtil.WmiQuery<>(wmiClass, ProcessProperty.class)
                                .execute(QUERY_TIMEOUT_MILLIS);
                List<WmiProcessRow> rows = new ArrayList<>(result.getResultCount());
                for (int index = 0; index < result.getResultCount(); index++) {
                    Object processId = result.getValue(ProcessProperty.PROCESSID, index);
                    if (!(processId instanceof Number number)) {
                        throw new InvalidWmiResponseException("WMI returned a non-numeric process id");
                    }
                    Object parentProcessId = result.getValue(ProcessProperty.PARENTPROCESSID, index);
                    rows.add(new WmiProcessRow(number.longValue(),
                            parentProcessId instanceof Number parent ? parent.longValue() : 0,
                            text(result.getValue(ProcessProperty.CREATIONDATE, index)),
                            text(result.getValue(ProcessProperty.EXECUTABLEPATH, index)),
                            text(result.getValue(ProcessProperty.NAME, index)),
                            text(result.getValue(ProcessProperty.COMMANDLINE, index))));
                }
                return List.copyOf(rows);
            }
            finally {
                if (ownsInitialization) Ole32.INSTANCE.CoUninitialize();
            }
        }

        private String text(Object value) {
            return value == null ? null : value.toString();
        }
    }

    private static final class InvalidWmiResponseException extends RuntimeException {
        private InvalidWmiResponseException(String message) {
            super(message);
        }
    }
}
