package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;

/**
 * DiagnosticReporter for Featurevisor SDK
 */
final class DiagnosticReporter {
    private static final FeaturevisorLogLevel[] ALL_LEVELS = {
        FeaturevisorLogLevel.DEBUG, FeaturevisorLogLevel.INFO, FeaturevisorLogLevel.WARN, FeaturevisorLogLevel.ERROR, FeaturevisorLogLevel.FATAL
    };

    private static final FeaturevisorLogLevel DEFAULT_LEVEL = FeaturevisorLogLevel.INFO;
    private static final String DIAGNOSTIC_PREFIX = "[Featurevisor]";

    private FeaturevisorLogLevel level;
    private DiagnosticOutputHandler handler;
    private boolean filter;

    interface DiagnosticOutputHandler {
        void handle(FeaturevisorLogLevel level, String message, Map<String, Object> details);
    }

    static class DiagnosticReporterOptions {
        private FeaturevisorLogLevel level;
        private DiagnosticOutputHandler handler;

        public DiagnosticReporterOptions level(FeaturevisorLogLevel level) {
            this.level = level;
            return this;
        }

        public DiagnosticReporterOptions handler(DiagnosticOutputHandler handler) {
            this.handler = handler;
            return this;
        }

        public FeaturevisorLogLevel getLevel() {
            return level;
        }

        public DiagnosticOutputHandler getHandler() {
            return handler;
        }
    }

    DiagnosticReporter() {
        this.level = DEFAULT_LEVEL;
        this.handler = this::defaultDiagnosticOutputHandler;
        this.filter = true;
    }

    DiagnosticReporter(FeaturevisorLogLevel level) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = this::defaultDiagnosticOutputHandler;
        this.filter = true;
    }

    DiagnosticReporter(DiagnosticOutputHandler handler) {
        this.level = DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultDiagnosticOutputHandler;
        this.filter = handler == null;
    }

    DiagnosticReporter(FeaturevisorLogLevel level, DiagnosticOutputHandler handler) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultDiagnosticOutputHandler;
        this.filter = handler == null;
    }

    DiagnosticReporter(DiagnosticReporterOptions options) {
        this.level = options.getLevel() != null ? options.getLevel() : DEFAULT_LEVEL;
        this.handler = options.getHandler() != null ? options.getHandler() : this::defaultDiagnosticOutputHandler;
        this.filter = options.getHandler() == null;
    }

    public void debug(String message) {
        debug(message, null);
    }

    public void debug(String message, Map<String, Object> details) {
        log(FeaturevisorLogLevel.DEBUG, message, details);
    }

    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Map<String, Object> details) {
        log(FeaturevisorLogLevel.INFO, message, details);
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Map<String, Object> details) {
        log(FeaturevisorLogLevel.WARN, message, details);
    }

    public void error(String message) {
        error(message, null);
    }

    public void error(String message, Map<String, Object> details) {
        log(FeaturevisorLogLevel.ERROR, message, details);
    }

    public void fatal(String message) {
        fatal(message, null);
    }

    public void fatal(String message, Map<String, Object> details) {
        log(FeaturevisorLogLevel.FATAL, message, details);
    }

    public void log(FeaturevisorLogLevel logLevel, String message, Map<String, Object> details) {
        if (filter && !shouldLog(logLevel)) {
            return;
        }

        // Custom handlers are evaluator sinks and receive every diagnostic.
        // Featurevisor applies filters independently for module subscribers,
        // the main diagnostic handler, console output, and error events.
        handler.handle(logLevel, message, details);
    }

    private boolean shouldLog(FeaturevisorLogLevel logLevel) {
        int currentLevelIndex = getLevelIndex(this.level);
        int messageLevelIndex = getLevelIndex(logLevel);

        // Log if message level is >= current level (higher index = higher priority)
        return messageLevelIndex >= currentLevelIndex;
    }

    private int getLevelIndex(FeaturevisorLogLevel level) {
        for (int i = 0; i < ALL_LEVELS.length; i++) {
            if (ALL_LEVELS[i] == level) {
                return i;
            }
        }
        return 0;
    }

    private void defaultDiagnosticOutputHandler(FeaturevisorLogLevel level, String message, Map<String, Object> details) {
        writeToConsole(level, message, details);
    }

    static void writeToConsole(FeaturevisorLogLevel level, String message, Map<String, Object> details) {
        String levelStr = level.name().toLowerCase();
        String logMessage = String.format("%s %s: %s", DIAGNOSTIC_PREFIX, levelStr, message);

        if (details != null && !details.isEmpty()) {
            logMessage += " " + details.toString();
        }

        System.out.println(logMessage);
    }

    public FeaturevisorLogLevel getLevel() {
        return level;
    }

    public void setLevel(FeaturevisorLogLevel level) {
        this.level = level != null ? level : DEFAULT_LEVEL;
    }

    DiagnosticOutputHandler getHandler() {
        return handler;
    }

    void setHandler(DiagnosticOutputHandler handler) {
        this.handler = handler != null ? handler : this::defaultDiagnosticOutputHandler;
        this.filter = handler == null;
    }

    static DiagnosticReporter createDiagnosticReporter() {
        return createDiagnosticReporter(new DiagnosticReporterOptions());
    }

    static DiagnosticReporter createDiagnosticReporter(DiagnosticReporterOptions options) {
        return new DiagnosticReporter(options);
    }
}
