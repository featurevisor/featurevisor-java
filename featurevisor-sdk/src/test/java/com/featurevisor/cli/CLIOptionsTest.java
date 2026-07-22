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

        assertEquals("false-target-checkout", cli.targetDatafileCacheKey(null, "checkout"));
        assertEquals("production-target-checkout", cli.targetDatafileCacheKey("production", "checkout"));
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
    public void testTargetAssertionSelectsTargetDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("environment", "production");
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put("production", new DatafileContent("2", "base"));
        cache.put("production-target-checkout", new DatafileContent("2", "target"));

        assertEquals("production-target-checkout", cli.selectDatafileKeyForAssertion(assertion, cache));
    }

    @Test
    public void testTargetAssertionFallsBackToBaseDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("environment", "production");
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put("production", new DatafileContent("2", "base"));

        assertEquals("production", cli.selectDatafileKeyForAssertion(assertion, cache));
    }

    @Test
    public void testNoEnvironmentTargetAssertionSelectsTargetDatafile() {
        CLI cli = new CLI();
        Map<String, Object> assertion = new HashMap<>();
        assertion.put("target", "checkout");

        Map<String, DatafileContent> cache = new HashMap<>();
        cache.put("__no_environment__", new DatafileContent("2", "base"));
        cache.put("false-target-checkout", new DatafileContent("2", "target"));

        assertEquals("false-target-checkout", cli.selectDatafileKeyForAssertion(assertion, cache));
    }
}
