package com.featurevisor.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
}
