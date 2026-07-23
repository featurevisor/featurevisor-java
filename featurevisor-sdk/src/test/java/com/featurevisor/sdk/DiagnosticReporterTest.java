package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class DiagnosticReporterTest {

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(outputStream));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testCreateDiagnosticReporterWithDefaultOptions() {
        DiagnosticReporter diagnostics = DiagnosticReporter.createDiagnosticReporter();
        assertNotNull(diagnostics);
        assertTrue(diagnostics instanceof DiagnosticReporter);
    }

    @Test
    public void testCreateDiagnosticReporterWithCustomLevel() {
        DiagnosticReporter diagnostics = DiagnosticReporter.createDiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));
        assertNotNull(diagnostics);
        assertTrue(diagnostics instanceof DiagnosticReporter);
    }

    @Test
    public void testCreateDiagnosticReporterWithCustomHandler() {
        final boolean[] handlerCalled = {false};
        final String[] capturedLevel = {null};
        final String[] capturedMessage = {null};
        final Object[] capturedDetails = {null};

                DiagnosticReporter.DiagnosticOutputHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        DiagnosticReporter diagnostics = DiagnosticReporter.createDiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().handler(customHandler));
        diagnostics.info("test message");

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertNull(capturedDetails[0]);
    }

    @Test
    public void testDiagnosticReporterConstructorWithDefaultLevel() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions());
        diagnostics.debug("debug message");

        // Debug should not be logged with default level (info)
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));
    }

    @Test
    public void testDiagnosticReporterConstructorWithProvidedLevel() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));
        diagnostics.debug("debug message");

        // Debug should be logged with debug level
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("debug message"));
    }

    @Test
    public void testDiagnosticReporterConstructorWithDefaultHandler() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions());
        diagnostics.info("test message");

        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("test message"));
    }

    @Test
    public void testDiagnosticReporterConstructorWithProvidedHandler() {
        final boolean[] handlerCalled = {false};
        final String[] capturedLevel = {null};
        final String[] capturedMessage = {null};
        final Object[] capturedDetails = {null};

        DiagnosticReporter.DiagnosticOutputHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().handler(customHandler));
        diagnostics.info("test message");

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertNull(capturedDetails[0]);
    }

    @Test
    public void testSetLevel() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.INFO));

        // Debug should not be logged initially
        diagnostics.debug("debug message");
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));

        // Clear output
        outputStream.reset();

        // Set to debug level
        diagnostics.setLevel(FeaturevisorLogLevel.DEBUG);
        diagnostics.debug("debug message");
        output = outputStream.toString();
        assertTrue(output.contains("debug message"));
    }

    @Test
    public void testLogLevelFilteringErrorMessages() {
        FeaturevisorLogLevel[] levels = {FeaturevisorLogLevel.DEBUG, FeaturevisorLogLevel.INFO, FeaturevisorLogLevel.WARN, FeaturevisorLogLevel.ERROR};

        for (FeaturevisorLogLevel level : levels) {
            DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(level));
            diagnostics.error("error message");

            String output = outputStream.toString();
            assertTrue(output.contains("error message"));

            // Clear output for next iteration
            outputStream.reset();
        }
    }

    @Test
    public void testLogLevelFilteringWarnMessages() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.WARN));

        diagnostics.warn("warn message");
        String output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        diagnostics.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testLogLevelFilteringInfoMessages() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.WARN));

        diagnostics.info("info message");
        String output = outputStream.toString();
        assertFalse(output.contains("info message"));
    }

    @Test
    public void testLogLevelFilteringDebugMessages() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.INFO));

        diagnostics.debug("debug message");
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));
    }

    @Test
    public void testLogLevelFilteringDebugLevel() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        diagnostics.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        diagnostics.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("info message"));

        outputStream.reset();

        diagnostics.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        diagnostics.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testConvenienceMethods() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        // Test debug method
        diagnostics.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        // Test info method
        diagnostics.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("info message"));

        outputStream.reset();

        // Test warn method
        diagnostics.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        // Test error method
        diagnostics.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testHandleDetailsParameter() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.INFO));

        Map<String, Object> details = new HashMap<>();
        details.put("key", "value");
        details.put("number", 42);

        diagnostics.info("message with details", details);
        String output = outputStream.toString();
        assertTrue(output.contains("message with details"));
        // Note: The exact format of details in output may vary based on implementation
    }

    @Test
    public void testLogMethodWithCustomHandler() {
        final boolean[] handlerCalled = {false};
        final String[] capturedLevel = {null};
        final String[] capturedMessage = {null};
        final Object[] capturedDetails = {null};

        DiagnosticReporter.DiagnosticOutputHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions()
            .handler(customHandler)
            .level(FeaturevisorLogLevel.DEBUG));

        Map<String, Object> details = new HashMap<>();
        details.put("test", true);

        diagnostics.log(FeaturevisorLogLevel.INFO, "test message", details);

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertEquals(details, capturedDetails[0]);
    }

    @Test
    public void testCustomEvaluatorSinkReceivesAllLevels() {
        final boolean[] handlerCalled = {false};

        DiagnosticReporter.DiagnosticOutputHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
        };

        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions()
            .handler(customHandler)
            .level(FeaturevisorLogLevel.WARN));

        diagnostics.log(FeaturevisorLogLevel.DEBUG, "debug message", null);

        assertTrue(handlerCalled[0]);
    }

        @Test
    public void testDefaultDiagnosticOutputHandler() {
        // Test that default handler works through the diagnostics
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        // Test debug level
        diagnostics.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        // Test info level
        diagnostics.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("info message"));

        outputStream.reset();

        // Test warn level
        diagnostics.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        // Test error level
        diagnostics.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testDefaultDiagnosticOutputHandlerWithUndefinedDetails() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions());
        diagnostics.info("message without details");
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("message without details"));
    }

    @Test
    public void testDefaultDiagnosticOutputHandlerWithProvidedDetails() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions());
        Map<String, Object> details = new HashMap<>();
        details.put("key", "value");

        diagnostics.info("message with details", details);
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("message with details"));
        // Note: The exact format of details in output may vary based on implementation
    }

        @Test
    public void testLogLevelEnumValues() {
        assertEquals("DEBUG", FeaturevisorLogLevel.DEBUG.name());
        assertEquals("INFO", FeaturevisorLogLevel.INFO.name());
        assertEquals("WARN", FeaturevisorLogLevel.WARN.name());
        assertEquals("ERROR", FeaturevisorLogLevel.ERROR.name());
        assertEquals("FATAL", FeaturevisorLogLevel.FATAL.name());
    }

    @Test
    public void testDiagnosticReporterOptionsBuilder() {
        DiagnosticReporter.DiagnosticOutputHandler handler = (level, message, details) -> {};
        DiagnosticReporter.DiagnosticReporterOptions options = new DiagnosticReporter.DiagnosticReporterOptions()
            .level(FeaturevisorLogLevel.DEBUG)
            .handler(handler);

        // Test that the builder pattern works correctly
        DiagnosticReporter diagnostics = new DiagnosticReporter(options);
        assertNotNull(diagnostics);
    }

    @Test
    public void testLogLevelEnumOrdinal() {
        // Test that log levels are in the correct order
        assertTrue(FeaturevisorLogLevel.DEBUG.ordinal() < FeaturevisorLogLevel.INFO.ordinal());
        assertTrue(FeaturevisorLogLevel.INFO.ordinal() < FeaturevisorLogLevel.WARN.ordinal());
        assertTrue(FeaturevisorLogLevel.WARN.ordinal() < FeaturevisorLogLevel.ERROR.ordinal());
        assertTrue(FeaturevisorLogLevel.ERROR.ordinal() < FeaturevisorLogLevel.FATAL.ordinal());
    }

    @Test
    public void testDiagnosticReporterWithNullMessage() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        // Should not throw exception with null message
        assertDoesNotThrow(() -> {
            diagnostics.info(null);
        });
    }

    @Test
    public void testDiagnosticReporterWithEmptyMessage() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        // Should handle empty message
        assertDoesNotThrow(() -> {
            diagnostics.info("");
        });

        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
    }

    @Test
    public void testDiagnosticReporterWithNullDetails() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        // Should handle null details
        assertDoesNotThrow(() -> {
            diagnostics.info("test message", null);
        });

        String output = outputStream.toString();
        assertTrue(output.contains("test message"));
    }

    @Test
    public void testDiagnosticReporterWithComplexDetails() {
        DiagnosticReporter diagnostics = new DiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.DEBUG));

        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("string", "value");
        complexDetails.put("number", 42);
        complexDetails.put("boolean", true);
        complexDetails.put("null", null);

        List<String> list = new ArrayList<>();
        list.add("item1");
        list.add("item2");
        complexDetails.put("list", list);

        Map<String, Object> nested = new HashMap<>();
        nested.put("nestedKey", "nestedValue");
        complexDetails.put("nested", nested);

        assertDoesNotThrow(() -> {
            diagnostics.info("complex message", complexDetails);
        });

        String output = outputStream.toString();
        assertTrue(output.contains("complex message"));
    }
}
