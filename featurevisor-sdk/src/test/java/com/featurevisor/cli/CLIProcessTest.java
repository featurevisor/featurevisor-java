package com.featurevisor.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CLIProcessTest {
    @Test
    void delegatesToTheNearestLocalNodeCliEntry(@TempDir Path directory) throws Exception {
        Path entry = directory.resolve("node_modules/@featurevisor/cli/bin.js");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, "");
        Path project = Files.createDirectories(directory.resolve("packages/flags"));
        org.apache.commons.exec.CommandLine command =
            CLI.nodeCommandLine(project.toString(), "npx featurevisor list --targets --json");
        assertEquals("node", command.getExecutable());
        assertArrayEquals(new String[] {entry.toString(), "list", "--targets", "--json"}, command.getArguments());
    }

    @Test
    void fallsBackToNpxAndLeavesOtherCommandsUnchanged(@TempDir Path directory) {
        org.apache.commons.exec.CommandLine command =
            CLI.nodeCommandLine(directory.toString(), "npx featurevisor config --json");
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        assertEquals(windows ? "npx.cmd" : "npx", command.getExecutable());
        assertArrayEquals(new String[] {"featurevisor", "config", "--json"}, command.getArguments());
        assertEquals("sh", CLI.nodeCommandLine(directory.toString(), "sh script.sh").getExecutable());
    }

    @Test
    void rawArgumentsAndWorkingDirectoryAreNotShellSplit(@TempDir Path directory) throws Exception {
        Path project = Files.createDirectory(directory.resolve("project's spaced path"));
        Path script = project.resolve("echo arguments.sh");
        Files.writeString(script, "pwd\nprintf '%s\\n' \"$@\"\n");
        String context = "{\"name\":\"O'Reilly; $(echo unsafe) 東京\", \"active\":true}";
        String caseId = "[null,\"scenario\",\"a quoted spec\",0,null]";
        String output = new CLI().executeCommandInDirectory(project.toString(), "sh",
            List.of(script.toString(), context, "--case=" + caseId, "--set=web team's set"));
        assertEquals(project.toRealPath() + "\n" + context + "\n--case=" + caseId + "\n--set=web team's set\n", output);
    }
    @Test
    void warningOnSuccessDoesNotCorruptJson(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("warning.sh");
        Files.writeString(script, "printf 'mutation warning\\n' >&2\nprintf '{\"ok\":true}\\n'\n");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream original = System.err;
        try {
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8.name()));
            String output = new CLI().executeCommandInDirectory(directory.toString(), "sh", List.of(script.toString()));
            assertEquals("{\"ok\":true}\n", output);
        } finally {
            System.setErr(original);
        }
        assertEquals("mutation warning\n", stderr.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    void largeStderrOnFailureRetainsExitCodeAndDiagnostics(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("failure.sh");
        Files.writeString(script, "head -c 262144 /dev/zero | tr '\\000' W >&2\nprintf 'syntax error at build staging\\n' >&2\nexit 17\n");
        IOException failure = assertThrows(IOException.class,
            () -> new CLI().executeCommandInDirectory(directory.toString(), "sh", List.of(script.toString())));
        assertTrue(failure.getMessage().contains("Exit value: 17"), failure.getMessage());
        assertTrue(failure.getMessage().contains("syntax error at build staging"));
        assertTrue(failure.getMessage().length() > 262144);
    }

    @Test
    void utf8OutputAndWarningSurviveDefaultCharset(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("unicode.sh");
        Files.writeString(script, "printf '\\303\\251' >&2\nprintf '\\303\\251'");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream original = System.err;
        try {
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8.name()));
            assertEquals("é", new CLI().executeCommandInDirectory(directory.toString(), "sh", List.of(script.toString())));
            assertEquals("é", new CLI().executeCommand("sh " + script));
        } finally {
            System.setErr(original);
        }
        assertEquals("éé", stderr.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    void utf8FailureDiagnosticsSurviveDefaultCharset(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("unicode-failure.sh");
        Files.writeString(script, "printf '\\303\\251' >&2\nprintf '\\303\\251'\nexit 7\n");
        CLI cli = new CLI();
        IOException inDirectory = assertThrows(IOException.class,
            () -> cli.executeCommandInDirectory(directory.toString(), "sh", List.of(script.toString())));
        assertTrue(inDirectory.getMessage().contains("é\né"), inDirectory.getMessage());
        IOException direct = assertThrows(IOException.class, () -> cli.executeCommand("sh " + script));
        assertTrue(direct.getMessage().contains("é\né"), direct.getMessage());
    }
}
