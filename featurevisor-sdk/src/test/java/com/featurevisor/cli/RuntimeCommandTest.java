package com.featurevisor.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCommandTest {
    private static final String DATAFILE = "{\"schemaVersion\":\"2\",\"revision\":\"1\",\"segments\":{},"
        + "\"features\":{\"checkout\":{\"bucketBy\":\"userId\",\"traffic\":[{\"key\":\"all\",\"segments\":\"*\",\"percentage\":100000}]}},"
        + "\"variables\":{\"colour\":{\"type\":\"string\",\"defaultValue\":\"blue\"}}}";

    @Test
    void inflateValidationAndForwarding() {
        CLI.NodeCommand unexpected = (directory, arguments) -> { fail("Invalid inflate must fail before delegation"); return DATAFILE; };
        for (String command : List.of("benchmark", "assess-distribution", "test")) {
            for (String value : List.of("0", "-1")) {
                assertEquals(1, new CommandLine(new CLI(unexpected)).execute(command, "--feature=checkout", "--n=1", "--inflate=" + value));
            }
            for (String value : List.of("0.5", "1.5", "invalid", "", "999999999999999999999999999999")) {
                assertEquals(2, new CommandLine(new CLI(unexpected)).execute(command, "--feature=checkout", "--n=1", "--inflate=" + value));
            }
            assertEquals(2, new CommandLine(new CLI(unexpected)).execute(command, "--inflate"));
            for (boolean supplied : List.of(false, true)) {
                List<String> builds = new ArrayList<>();
                CLI.NodeCommand node = (directory, arguments) -> {
                    if (arguments.contains("config --json")) return "{}";
                    if (arguments.contains("list --tests")) return "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":[]}";
                    if (arguments.contains("list --segments") || arguments.contains("list --targets")) return "[]";
                    assertTrue(arguments.contains("build --json"), arguments);
                    builds.add(arguments);
                    return DATAFILE;
                };
                List<String> args = new ArrayList<>(List.of(command, "--feature=checkout", "--n=1"));
                if (supplied) args.add("--inflate=1");
                assertEquals(0, new CommandLine(new CLI(node)).execute(args.toArray(new String[0])));
                assertEquals(List.of(supplied ? "npx featurevisor build --json --inflate=1" : "npx featurevisor build --json"), builds);
            }
        }
    }

    @Test
    void delegatesFlatEnvironmentSetAndDistinctTargets() {
        for (String command : List.of("benchmark", "assess-distribution")) {
            List<String> calls = new ArrayList<>();
            CLI.NodeCommand node = (directory, arguments) -> {
                if (arguments.contains("config --json")) return "{}";
                calls.add(arguments); return DATAFILE;
            };
            assertEquals(0, new CommandLine(new CLI(node)).execute(command, "--feature=checkout", "--n=1"));
            assertEquals(List.of("npx featurevisor build --json"), calls);
            calls.clear();
            assertEquals(0, new CommandLine(new CLI(node)).execute(command, "--feature=checkout", "--n=1",
                "--environment=production", "--set=storefront", "--target=web", "--target=mobile", "--target=web"));
            assertEquals(List.of(
                "npx featurevisor build --json --environment=production --target=web --set=storefront",
                "npx featurevisor build --json --environment=production --target=mobile --set=storefront"), calls);
        }
        assertEquals(0, new CommandLine(new CLI((directory, arguments) -> arguments.contains("config --json") ? "{}" : DATAFILE))
            .execute("benchmark", "--variable=colour", "--n=1"));
    }

    @Test
    void delegatedFailureAndMalformedDatafileReturnFailure() {
        for (String command : List.of("benchmark", "assess-distribution")) {
            for (String selection : List.of("--environment=unknown", "--set=unknown", "--target=unknown")) {
                assertEquals(1, new CommandLine(new CLI((directory, arguments) -> {
                    if (arguments.contains("config --json")) return "{}";
                    assertTrue(arguments.contains(selection));
                    throw new IOException("Invalid project selection");
                })).execute(command, "--feature=checkout", "--n=1", selection));
            }
            assertEquals(1, new CommandLine(new CLI((directory, arguments) -> arguments.contains("config --json") ? "{}" : "not JSON"))
                .execute(command, "--feature=checkout", "--n=1"));
            assertEquals(1, new CommandLine(new CLI((directory, arguments) -> {
                assertEquals("npx featurevisor config --json", arguments);
                return "not JSON";
            })).execute(command, "--feature=checkout", "--n=1"));
            List<String> calls = new ArrayList<>();
            assertEquals(1, new CommandLine(new CLI((directory, arguments) -> {
                if (arguments.contains("config --json")) return "{}";
                calls.add(arguments);
                if (arguments.contains("--target=unknown")) throw new IOException("Unknown target");
                return DATAFILE;
            })).execute(command, "--feature=checkout", "--n=1", "--target=web", "--target=unknown", "--target=unreached"));
            assertEquals(2, calls.size());
            assertFalse(calls.toString().contains("unreached"));
        }
    }

    @Test
    void invalidOptionsFailBeforeDelegation() {
        CLI.NodeCommand unexpected = (directory, arguments) -> { fail("Unexpected delegation"); return DATAFILE; };
        for (String command : List.of("benchmark", "assess-distribution")) {
            assertEquals(1, new CommandLine(new CLI(unexpected)).execute(command));
            for (String n : List.of("0", "-1")) {
                assertEquals(1, new CommandLine(new CLI(unexpected)).execute(command, "--feature=checkout", "--n=" + n));
            }
            for (String n : List.of("invalid", "1.5")) {
                assertEquals(2, new CommandLine(new CLI(unexpected)).execute(command, "--feature=checkout", "--n=" + n));
            }
        }
        assertEquals(1, new CommandLine(new CLI(unexpected)).execute("benchmark", "--variable=colour", "--variation"));
        assertEquals(1, new CommandLine(new CLI(unexpected)).execute("benchmark", "--feature=checkout", "--variable=colour", "--variation"));
    }

    @Test
    void allSetsAndExplicitSetsWithNestedTargets(@TempDir Path project) throws Exception {
        Path sets = project.resolve("lanes with spaces");
        Files.createDirectories(sets.resolve("store front"));
        Files.createDirectories(sets.resolve("admin"));
        Files.writeString(sets.resolve("ignored.txt"), "not a set");
        List<String> calls = new ArrayList<>();
        CLI.NodeCommand node = (directory, arguments) -> {
            calls.add(arguments);
            if (arguments.contains("config --json")) return "{\"sets\":true,\"setsDirectoryPath\":\"lanes with spaces\"}";
            if (arguments.contains("--set=unknown") || arguments.contains("--target=unknown")) throw new IOException("Invalid project selection");
            return DATAFILE;
        };
        for (String command : List.of("benchmark", "assess-distribution")) {
            List<String> base = List.of(command, "--projectDirectoryPath=" + project, "--feature=checkout", "--n=1");
            calls.clear();
            assertEquals(0, new CommandLine(new CLI(node)).execute(base.toArray(new String[0])));
            assertEquals(List.of("npx featurevisor config --json", "npx featurevisor build --json --set=admin",
                "npx featurevisor build --json --set=store front"), calls);

            List<String> targets = new ArrayList<>(base);
            targets.addAll(List.of("--environment=production", "--target=nested.web", "--target=mobile", "--target=nested.web"));
            calls.clear();
            CLI cli = new CLI(node);
            assertEquals(0, new CommandLine(cli).execute(targets.toArray(new String[0])));
            List<String> expected = new ArrayList<>(List.of("npx featurevisor config --json"));
            for (String set : List.of("admin", "store front")) {
                for (String target : List.of("nested.web", "mobile")) {
                    expected.add("npx featurevisor build --json --environment=production --target=" + target + " --set=" + set);
                }
            }
            assertEquals(expected, calls);
            calls.clear();
            assertEquals(0, cli.call());
            assertEquals(expected, calls, "Selections must be restored after execution");

            targets.add("--set=store front");
            calls.clear();
            assertEquals(0, new CommandLine(new CLI(node)).execute(targets.toArray(new String[0])));
            List<String> explicitExpected = new ArrayList<>(List.of("npx featurevisor config --json"));
            explicitExpected.addAll(expected.subList(3, 5));
            assertEquals(explicitExpected, calls);
            List<String> explicit = new ArrayList<>(base);
            explicit.add("--set=store front");
            calls.clear();
            assertEquals(0, new CommandLine(new CLI(node)).execute(explicit.toArray(new String[0])));
            assertEquals(List.of("npx featurevisor config --json", "npx featurevisor build --json --set=store front"), calls);
            explicit.set(explicit.size() - 1, "--set=unknown");
            calls.clear();
            assertEquals(1, new CommandLine(new CLI(node)).execute(explicit.toArray(new String[0])));
            assertEquals(List.of("npx featurevisor config --json", "npx featurevisor build --json --set=unknown"), calls);

            List<String> invalidTargets = new ArrayList<>(base);
            invalidTargets.addAll(List.of("--target=unknown", "--target=unreached"));
            calls.clear();
            assertEquals(1, new CommandLine(new CLI(node)).execute(invalidTargets.toArray(new String[0])));
            assertEquals(List.of("npx featurevisor config --json", "npx featurevisor build --json --target=unknown --set=admin"), calls);
        }
        Files.delete(sets.resolve("store front"));
        Files.delete(sets.resolve("admin"));
        for (String command : List.of("benchmark", "assess-distribution")) {
            calls.clear();
            assertEquals(1, new CommandLine(new CLI(node)).execute(command, "--projectDirectoryPath=" + project, "--feature=checkout"));
            assertEquals(List.of("npx featurevisor config --json"), calls);
        }
        Files.delete(sets.resolve("ignored.txt"));
        Files.delete(sets);
        for (String command : List.of("benchmark", "assess-distribution")) {
            calls.clear();
            assertEquals(1, new CommandLine(new CLI(node)).execute(command, "--projectDirectoryPath=" + project, "--feature=checkout"));
            assertEquals(List.of("npx featurevisor config --json"), calls);
        }
    }
}
