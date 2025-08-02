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

public class LoggerTest {

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
    public void testCreateLoggerWithDefaultOptions() {
        Logger logger = Logger.createLogger();
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    public void testCreateLoggerWithCustomLevel() {
        Logger logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    public void testCreateLoggerWithCustomHandler() {
        final boolean[] handlerCalled = {false};
        final String[] capturedLevel = {null};
        final String[] capturedMessage = {null};
        final Object[] capturedDetails = {null};

                Logger.LogHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        Logger logger = Logger.createLogger(new Logger.CreateLoggerOptions().handler(customHandler));
        logger.info("test message");

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertNull(capturedDetails[0]);
    }

    @Test
    public void testLoggerConstructorWithDefaultLevel() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions());
        logger.debug("debug message");

        // Debug should not be logged with default level (info)
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));
    }

    @Test
    public void testLoggerConstructorWithProvidedLevel() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));
        logger.debug("debug message");

        // Debug should be logged with debug level
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("debug message"));
    }

    @Test
    public void testLoggerConstructorWithDefaultHandler() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions());
        logger.info("test message");

        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("test message"));
    }

    @Test
    public void testLoggerConstructorWithProvidedHandler() {
        final boolean[] handlerCalled = {false};
        final String[] capturedLevel = {null};
        final String[] capturedMessage = {null};
        final Object[] capturedDetails = {null};

        Logger.LogHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        Logger logger = new Logger(new Logger.CreateLoggerOptions().handler(customHandler));
        logger.info("test message");

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertNull(capturedDetails[0]);
    }

    @Test
    public void testSetLevel() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.INFO));

        // Debug should not be logged initially
        logger.debug("debug message");
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));

        // Clear output
        outputStream.reset();

        // Set to debug level
        logger.setLevel(Logger.LogLevel.DEBUG);
        logger.debug("debug message");
        output = outputStream.toString();
        assertTrue(output.contains("debug message"));
    }

    @Test
    public void testLogLevelFilteringErrorMessages() {
        Logger.LogLevel[] levels = {Logger.LogLevel.DEBUG, Logger.LogLevel.INFO, Logger.LogLevel.WARN, Logger.LogLevel.ERROR};

        for (Logger.LogLevel level : levels) {
            Logger logger = new Logger(new Logger.CreateLoggerOptions().level(level));
            logger.error("error message");

            String output = outputStream.toString();
            assertTrue(output.contains("error message"));

            // Clear output for next iteration
            outputStream.reset();
        }
    }

    @Test
    public void testLogLevelFilteringWarnMessages() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));

        logger.warn("warn message");
        String output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        logger.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testLogLevelFilteringInfoMessages() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));

        logger.info("info message");
        String output = outputStream.toString();
        assertFalse(output.contains("info message"));
    }

    @Test
    public void testLogLevelFilteringDebugMessages() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.INFO));

        logger.debug("debug message");
        String output = outputStream.toString();
        assertFalse(output.contains("debug message"));
    }

    @Test
    public void testLogLevelFilteringDebugLevel() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        logger.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        logger.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("info message"));

        outputStream.reset();

        logger.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        logger.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testConvenienceMethods() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        // Test debug method
        logger.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        // Test info method
        logger.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("info message"));

        outputStream.reset();

        // Test warn method
        logger.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        // Test error method
        logger.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testHandleDetailsParameter() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.INFO));

        Map<String, Object> details = new HashMap<>();
        details.put("key", "value");
        details.put("number", 42);

        logger.info("message with details", details);
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

        Logger.LogHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
            capturedLevel[0] = level.name().toLowerCase();
            capturedMessage[0] = message;
            capturedDetails[0] = details;
        };

        Logger logger = new Logger(new Logger.CreateLoggerOptions()
            .handler(customHandler)
            .level(Logger.LogLevel.DEBUG));

        Map<String, Object> details = new HashMap<>();
        details.put("test", true);

        logger.log(Logger.LogLevel.INFO, "test message", details);

        assertTrue(handlerCalled[0]);
        assertEquals("info", capturedLevel[0]);
        assertEquals("test message", capturedMessage[0]);
        assertEquals(details, capturedDetails[0]);
    }

    @Test
    public void testLogMethodWithLevelFiltering() {
        final boolean[] handlerCalled = {false};

        Logger.LogHandler customHandler = (level, message, details) -> {
            handlerCalled[0] = true;
        };

        Logger logger = new Logger(new Logger.CreateLoggerOptions()
            .handler(customHandler)
            .level(Logger.LogLevel.WARN));

        logger.log(Logger.LogLevel.DEBUG, "debug message", null);

        assertFalse(handlerCalled[0]);
    }

        @Test
    public void testDefaultLogHandler() {
        // Test that default handler works through the logger
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        // Test debug level
        logger.debug("debug message");
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("debug message"));

        outputStream.reset();

        // Test info level
        logger.info("info message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("info message"));

        outputStream.reset();

        // Test warn level
        logger.warn("warn message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("warn message"));

        outputStream.reset();

        // Test error level
        logger.error("error message");
        output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("error message"));
    }

    @Test
    public void testDefaultLogHandlerWithUndefinedDetails() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions());
        logger.info("message without details");
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("message without details"));
    }

    @Test
    public void testDefaultLogHandlerWithProvidedDetails() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions());
        Map<String, Object> details = new HashMap<>();
        details.put("key", "value");

        logger.info("message with details", details);
        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
        assertTrue(output.contains("message with details"));
        // Note: The exact format of details in output may vary based on implementation
    }

        @Test
    public void testLogLevelEnumValues() {
        assertEquals("DEBUG", Logger.LogLevel.DEBUG.name());
        assertEquals("INFO", Logger.LogLevel.INFO.name());
        assertEquals("WARN", Logger.LogLevel.WARN.name());
        assertEquals("ERROR", Logger.LogLevel.ERROR.name());
        assertEquals("FATAL", Logger.LogLevel.FATAL.name());
    }

    @Test
    public void testCreateLoggerOptionsBuilder() {
        Logger.LogHandler handler = (level, message, details) -> {};
        Logger.CreateLoggerOptions options = new Logger.CreateLoggerOptions()
            .level(Logger.LogLevel.DEBUG)
            .handler(handler);

        // Test that the builder pattern works correctly
        Logger logger = new Logger(options);
        assertNotNull(logger);
    }

    @Test
    public void testLogLevelEnumOrdinal() {
        // Test that log levels are in the correct order
        assertTrue(Logger.LogLevel.DEBUG.ordinal() < Logger.LogLevel.INFO.ordinal());
        assertTrue(Logger.LogLevel.INFO.ordinal() < Logger.LogLevel.WARN.ordinal());
        assertTrue(Logger.LogLevel.WARN.ordinal() < Logger.LogLevel.ERROR.ordinal());
        assertTrue(Logger.LogLevel.ERROR.ordinal() < Logger.LogLevel.FATAL.ordinal());
    }

    @Test
    public void testLoggerWithNullMessage() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        // Should not throw exception with null message
        assertDoesNotThrow(() -> {
            logger.info(null);
        });
    }

    @Test
    public void testLoggerWithEmptyMessage() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        // Should handle empty message
        assertDoesNotThrow(() -> {
            logger.info("");
        });

        String output = outputStream.toString();
        assertTrue(output.contains("[Featurevisor]"));
    }

    @Test
    public void testLoggerWithNullDetails() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

        // Should handle null details
        assertDoesNotThrow(() -> {
            logger.info("test message", null);
        });

        String output = outputStream.toString();
        assertTrue(output.contains("test message"));
    }

    @Test
    public void testLoggerWithComplexDetails() {
        Logger logger = new Logger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.DEBUG));

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
            logger.info("complex message", complexDetails);
        });

        String output = outputStream.toString();
        assertTrue(output.contains("complex message"));
    }
}
