package com.fuad.assistant.skills.os;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class WindowsApplicationDiscovery implements ApplicationDiscovery {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DISCOVERY_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $shell = New-Object -ComObject WScript.Shell
            $shortcutTargets = @{}
            $roots = @([Environment]::GetFolderPath('StartMenu'), [Environment]::GetFolderPath('CommonStartMenu'))
            foreach ($root in $roots) {
              if (-not [string]::IsNullOrWhiteSpace($root)) {
                Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
                  $shortcut = $shell.CreateShortcut($_.FullName)
                  if (-not [string]::IsNullOrWhiteSpace($shortcut.TargetPath)) {
                    $key = $_.BaseName.ToLowerInvariant()
                    if (-not $shortcutTargets.ContainsKey($key)) {
                      $shortcutTargets[$key] = [pscustomobject]@{
                        path = $shortcut.TargetPath
                        arguments = $shortcut.Arguments
                      }
                    }
                  }
                }
              }
            }
            $packageRoots = @{}
            Get-AppxPackage -ErrorAction SilentlyContinue | ForEach-Object {
              if (-not [string]::IsNullOrWhiteSpace($_.PackageFamilyName) -and
                  -not [string]::IsNullOrWhiteSpace($_.InstallLocation)) {
                $packageRoots[$_.PackageFamilyName] = $_.InstallLocation
              }
            }
            $rows = @(Get-StartApps | ForEach-Object {
              $shortcut = $shortcutTargets[$_.Name.ToLowerInvariant()]
              $root = $null
              if ($_.AppID -like '*!*') {
                $family = $_.AppID.Split('!')[0]
                $root = $packageRoots[$family]
              }
              [pscustomobject]@{
                id = $_.AppID
                name = $_.Name
                executablePath = $shortcut.path
                arguments = $shortcut.arguments
                packageRoot = $root
              }
            })
            $rows | ConvertTo-Json -Compress -Depth 3
            """;

    private final CommandRunner commandRunner;
    private final ObjectMapper objectMapper;

    public WindowsApplicationDiscovery() {
        this(new ProcessCommandRunner(), new ObjectMapper());
    }

    WindowsApplicationDiscovery(CommandRunner commandRunner, ObjectMapper objectMapper) {
        this.commandRunner = commandRunner;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ApplicationDefinition> discover() throws IOException {
        CommandResult result = commandRunner.run(List.of(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", DISCOVERY_SCRIPT), TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("Windows application discovery failed: " + result.stderr().trim());
        }
        JsonNode root = objectMapper.readTree(result.stdout());
        if (root == null || root.isNull()) return List.of();
        List<JsonNode> rows = new ArrayList<>();
        if (root.isArray()) root.forEach(rows::add); else rows.add(root);
        List<ApplicationDefinition> applications = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = text(row, "id");
            String name = text(row, "name");
            if (id.isBlank() || name.isBlank()) continue;
            Set<String> paths = valueSet(text(row, "executablePath"));
            Set<String> packageRoots = valueSet(text(row, "packageRoot"));
            List<String> arguments = WindowsCommandLineTokenizer.tokenize(text(row, "arguments"));
            List<Set<String>> argumentSets = arguments.isEmpty() ? List.of() : List.of(Set.copyOf(arguments));
            applications.add(new ApplicationDefinition(id, name, Set.of(),
                    List.of("explorer.exe", "shell:AppsFolder\\" + id),
                    new ApplicationProcessIdentity(paths, packageRoots, Set.of(), Set.of(), argumentSets)));
        }
        return List.copyOf(applications);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static Set<String> valueSet(String value) {
        return value.isBlank() ? Set.of() : Set.of(value);
    }

    interface CommandRunner {
        CommandResult run(List<String> command, Duration timeout) throws IOException;
    }

    record CommandResult(int exitCode, String stdout, String stderr) { }

    static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command, Duration timeout) throws IOException {
            Process process = new ProcessBuilder(command).start();
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroy();
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                    throw new IOException("Windows application discovery timed out after " + timeout.toSeconds() + "s");
                }
                return new CommandResult(process.exitValue(), stdout.join(), stderr.join());
            }
            catch (InterruptedException e) {
                process.destroy();
                Thread.currentThread().interrupt();
                throw new IOException("Windows application discovery was interrupted", e);
            }
            catch (CompletionException e) {
                throw new IOException("Unable to read Windows application discovery output", e.getCause());
            }
        }

        private CompletableFuture<String> readAsync(java.io.InputStream stream) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
        }
    }
}
