package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;

/**
 * Logger for Featurevisor SDK
 */
final class Logger {
    private static final FeaturevisorLogLevel[] ALL_LEVELS = {
        FeaturevisorLogLevel.DEBUG, FeaturevisorLogLevel.INFO, FeaturevisorLogLevel.WARN, FeaturevisorLogLevel.ERROR, FeaturevisorLogLevel.FATAL
    };

    private static final FeaturevisorLogLevel DEFAULT_LEVEL = FeaturevisorLogLevel.INFO;
    private static final String LOGGER_PREFIX = "[Featurevisor]";

    private FeaturevisorLogLevel level;
    private LogHandler handler;

    interface LogHandler {
        void handle(FeaturevisorLogLevel level, String message, Map<String, Object> details);
    }

    static class CreateLoggerOptions {
        private FeaturevisorLogLevel level;
        private LogHandler handler;

        public CreateLoggerOptions level(FeaturevisorLogLevel level) {
            this.level = level;
            return this;
        }

        public CreateLoggerOptions handler(LogHandler handler) {
            this.handler = handler;
            return this;
        }

        public FeaturevisorLogLevel getLevel() {
            return level;
        }

        public LogHandler getHandler() {
            return handler;
        }
    }

    Logger() {
        this.level = DEFAULT_LEVEL;
        this.handler = this::defaultLogHandler;
    }

    Logger(FeaturevisorLogLevel level) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = this::defaultLogHandler;
    }

    Logger(LogHandler handler) {
        this.level = DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    Logger(FeaturevisorLogLevel level, LogHandler handler) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    Logger(CreateLoggerOptions options) {
        this.level = options.getLevel() != null ? options.getLevel() : DEFAULT_LEVEL;
        this.handler = options.getHandler() != null ? options.getHandler() : this::defaultLogHandler;
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
        if (shouldLog(logLevel)) {
            handler.handle(logLevel, message, details);
        }
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

    private void defaultLogHandler(FeaturevisorLogLevel level, String message, Map<String, Object> details) {
        writeToConsole(level, message, details);
    }

    static void writeToConsole(FeaturevisorLogLevel level, String message, Map<String, Object> details) {
        String levelStr = level.name().toLowerCase();
        String logMessage = String.format("%s %s: %s", LOGGER_PREFIX, levelStr, message);

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

    LogHandler getHandler() {
        return handler;
    }

    void setHandler(LogHandler handler) {
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    static Logger createLogger() {
        return createLogger(new CreateLoggerOptions());
    }

    static Logger createLogger(CreateLoggerOptions options) {
        return new Logger(options);
    }
}
