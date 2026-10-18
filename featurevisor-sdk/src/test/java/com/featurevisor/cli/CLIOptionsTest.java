package com.featurevisor.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import com.featurevisor.sdk.DatafileContent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CLIOptionsTest {
    @Test
    void aliasesAndValueFormsAreEquivalent() {
        String[][] aliases = {
            {"keyPattern", "key-pattern", "flag with spaces"},
            {"assertionPattern", "assertion-pattern", "country: nl"},
            {"entityType", "entity-type", "scenario"},
            {"projectDirectoryPath", "project-directory-path", "a project's directory"},
            {"projectDirectoryPath", "root-directory-path", "a project's directory"},
            {"projectDirectoryPath", "rootDirectoryPath", "a project's directory"}
        };
        for (String[] alias : aliases) {
            for (String spelling : new String[]{alias[0], alias[1]}) {
                for (boolean equals : new boolean[]{false, true}) {
                    String[] args = equals ? new String[]{"test", "--" + spelling + "=" + alias[2]}
                        : new String[]{"test", "--" + spelling, alias[2]};
                    var result = new CommandLine(new CLI()).parseArgs(args);
                    assertEquals(alias[2], result.matchedOption("--" + alias[0]).getValue());
                }
            }
        }
        for (String[] aliasesPair : new String[][]{
            {"onlyFailures", "only-failures"}, {"showDatafile", "show-datafile"}}) {
            for (String spelling : aliasesPair) {
                var result = new CommandLine(new CLI()).parseArgs("test", "--" + spelling);
                assertEquals(Boolean.TRUE, result.matchedOption("--" + aliasesPair[0]).getValue());
            }
        }
        for (String option : new String[]{"--n", "-n"}) {
            for (String[] args : new String[][]{{"benchmark", option, "2"}, {"benchmark", option + "=2"}}) {
                assertEquals(Integer.valueOf(2), new CommandLine(new CLI()).parseArgs(args).matchedOption("--n").getValue());
            }
        }
    }

    @Test
    void repeatedSelectorsPreserveOrderAndConsumeExactlyOneValue() {
        var result = new CommandLine(new CLI()).parseArgs("assess-distribution",
            "--target=web one", "--target", "mobile", "--target=web one",
            "--populate-uuid=userId", "--populateUuid", "device id");
        assertEquals(java.util.List.of("web one", "mobile", "web one"), result.matchedOption("--target").getValue());
        assertEquals(java.util.List.of("userId", "device id"), result.matchedOption("--populateUuid").getValue());
        for (String[] args : new String[][]{
            {"test", "--target=web", "extra"}, {"test", "--populate-uuid=userId", "extra"},
            {"test", "--target"}, {"test", "--populate-uuid"}, {"test", "--unknown"},
            {"test", "--only-failures=invalid"}, {"test", "--n=1.5"},
            {"test", "--key-pattern=a", "--keyPattern=b"}}) {
            org.junit.jupiter.api.Assertions.assertThrows(CommandLine.ParameterException.class,
                () -> new CommandLine(new CLI()).parseArgs(args), java.util.Arrays.toString(args));
        }
    }

    @Test
    public void testParseLegacyIgnoredOptions() {
        CLI cli = new CLI();
        CommandLine.ParseResult result = new CommandLine(cli).parseArgs(
            "test",
            "--with-scopes",
            "--with-tags",
            "--showDatafile",
            "--schema-version=2",
            "--inflate=3"
        );

        assertTrue(result.hasMatchedOption("--with-scopes"));
        assertTrue(result.hasMatchedOption("--with-tags"));
        assertTrue(result.hasMatchedOption("--showDatafile"));
        assertTrue(result.hasMatchedOption("--schema-version"));
        assertTrue(result.hasMatchedOption("--inflate"));
    }

    @Test
    public void testTargetDatafileCacheKey() {
        CLI cli = new CLI();

        assertEquals("[null,\"checkout\"]", cli.targetDatafileCacheKey(null, "checkout"));
        assertEquals("[\"production\",\"checkout\"]", cli.targetDatafileCacheKey("production", "checkout"));
    }

    @Test
    public void testRepeatedTargets() {
        CLI cli = new CLI();
        CommandLine.ParseResult result = new CommandLine(cli).parseArgs(
            "benchmark", "--target=web", "--target=mobile"
        );
        assertEquals(java.util.Arrays.asList("web", "mobile"), result.matchedOption("--target").getValue());
    }

    @Test
    public void testReportingOptions() {
        CLI cli = new CLI();
        CommandLine.ParseResult result = new CommandLine(cli).parseArgs(
            "test",
            "--case=[null,\"variable\",\"settings.spec\",0,null]",
            "--json",
            "--pretty",
            "--coverage",
            "--uncovered"
        );

        assertTrue(result.hasMatchedOption("--case"));
        assertTrue(result.hasMatchedOption("--json"));
        assertTrue(result.hasMatchedOption("--pretty"));
        assertTrue(result.hasMatchedOption("--coverage"));
        assertTrue(result.hasMatchedOption("--uncovered"));
    }

    @Test
    public void testTargetAssertionSelectsTargetDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("environment", "production");
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put(cli.getEnvironmentKey("production"), new DatafileContent("2", "base"));
        cache.put(cli.targetDatafileCacheKey("production", "checkout"), new DatafileContent("2", "target"));

        assertEquals("[\"production\",\"checkout\"]", cli.selectDatafileKeyForAssertion(assertion, cache));
    }

    @Test
    public void testTargetAssertionDoesNotFallBackToBaseDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("environment", "production");
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put(cli.getEnvironmentKey("production"), new DatafileContent("2", "base"));

        assertEquals("[\"production\",\"checkout\"]", cli.selectDatafileKeyForAssertion(assertion, cache));
    }

    @Test
    public void testNoEnvironmentTargetAssertionSelectsTargetDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put(cli.getEnvironmentKey(null), new DatafileContent("2", "base"));
        cache.put(cli.targetDatafileCacheKey(null, "checkout"), new DatafileContent("2", "target"));

        assertEquals("[null,\"checkout\"]", cli.selectDatafileKeyForAssertion(assertion, cache));
    }

    @Test
    public void testCacheKeysPreserveEverySelectionAndRoundTripJson() throws Exception {
        CLI cli = new CLI();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String[] names = {null, "", "false", "__no_environment__", "prod", "prod-target-web",
            "web", "web-target-mobile", "mobile", "Café 東京 🧪\"\\", "[null,\"web\"]"};
        Map<String, DatafileContent> cache = new HashMap<>();
        for (String environment : names) {
            for (String target : names) {
                String key = target == null ? cli.getEnvironmentKey(environment)
                    : cli.targetDatafileCacheKey(environment, target);
                org.junit.jupiter.api.Assertions.assertNull(cache.put(key, new DatafileContent("2", key)));
                assertEquals(json.valueToTree(java.util.Arrays.asList(environment, target)), json.readTree(key));
            }
        }
        for (String environment : names) {
            for (String target : names) {
                Map<String, Object> assertion = new HashMap<>();
                assertion.put("environment", environment);
                assertion.put("target", target);
                String key = cli.selectDatafileKeyForAssertion(assertion, cache);
                assertEquals(json.valueToTree(java.util.Arrays.asList(environment, target)), json.readTree(key));
                assertEquals(key, cache.get(key).getRevision());
            }
        }
        assertEquals(names.length * names.length, cache.size());
    }
}
