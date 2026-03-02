package com.featurevisor.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CLIOptionsTest {

    @Test
    public void testParseScopedAndTaggedOptions() {
        CLI cli = new CLI();
        CommandLine.ParseResult result = new CommandLine(cli).parseArgs(
            "test",
            "--with-scopes",
            "--with-tags",
            "--showDatafile",
            "--schemaVersion=2",
            "--inflate=3"
        );

        assertTrue(result.hasMatchedOption("--with-scopes"));
        assertTrue(result.hasMatchedOption("--with-tags"));
        assertTrue(result.hasMatchedOption("--showDatafile"));
        assertTrue(result.hasMatchedOption("--schemaVersion"));
        assertTrue(result.hasMatchedOption("--inflate"));
    }
}
