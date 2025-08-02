package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;

/**
 * Logger for Featurevisor SDK
 */
public class Logger {
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, FATAL
    }

    private static final LogLevel[] ALL_LEVELS = {
        LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL
    };

    private static final LogLevel DEFAULT_LEVEL = LogLevel.INFO;
    private static final String LOGGER_PREFIX = "[Featurevisor]";

    private LogLevel level;
    private LogHandler handler;

    public interface LogHandler {
        void handle(LogLevel level, String message, Map<String, Object> details);
    }

    public static class CreateLoggerOptions {
        private LogLevel level;
        private LogHandler handler;

        public CreateLoggerOptions level(LogLevel level) {
            this.level = level;
            return this;
        }

        public CreateLoggerOptions handler(LogHandler handler) {
            this.handler = handler;
            return this;
        }

        public LogLevel getLevel() {
            return level;
        }

        public LogHandler getHandler() {
            return handler;
        }
    }

    public Logger() {
        this.level = DEFAULT_LEVEL;
        this.handler = this::defaultLogHandler;
    }

    public Logger(LogLevel level) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = this::defaultLogHandler;
    }

    public Logger(LogHandler handler) {
        this.level = DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    public Logger(LogLevel level, LogHandler handler) {
        this.level = level != null ? level : DEFAULT_LEVEL;
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    public Logger(CreateLoggerOptions options) {
        this.level = options.getLevel() != null ? options.getLevel() : DEFAULT_LEVEL;
        this.handler = options.getHandler() != null ? options.getHandler() : this::defaultLogHandler;
    }

    public void debug(String message) {
        debug(message, null);
    }

    public void debug(String message, Map<String, Object> details) {
        log(LogLevel.DEBUG, message, details);
    }

    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Map<String, Object> details) {
        log(LogLevel.INFO, message, details);
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Map<String, Object> details) {
        log(LogLevel.WARN, message, details);
    }

    public void error(String message) {
        error(message, null);
    }

    public void error(String message, Map<String, Object> details) {
        log(LogLevel.ERROR, message, details);
    }

    public void fatal(String message) {
        fatal(message, null);
    }

    public void fatal(String message, Map<String, Object> details) {
        log(LogLevel.FATAL, message, details);
    }

    public void log(LogLevel logLevel, String message, Map<String, Object> details) {
        if (shouldLog(logLevel)) {
            handler.handle(logLevel, message, details);
        }
    }

    private boolean shouldLog(LogLevel logLevel) {
        int currentLevelIndex = getLevelIndex(this.level);
        int messageLevelIndex = getLevelIndex(logLevel);

        // Log if message level is >= current level (higher index = higher priority)
        return messageLevelIndex >= currentLevelIndex;
    }

    private int getLevelIndex(LogLevel level) {
        for (int i = 0; i < ALL_LEVELS.length; i++) {
            if (ALL_LEVELS[i] == level) {
                return i;
            }
        }
        return 0;
    }

    private void defaultLogHandler(LogLevel level, String message, Map<String, Object> details) {
        String levelStr = level.name().toLowerCase();
        String logMessage = String.format("%s %s: %s", LOGGER_PREFIX, levelStr, message);

        if (details != null && !details.isEmpty()) {
            logMessage += " " + details.toString();
        }

        System.out.println(logMessage);
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        this.level = level != null ? level : DEFAULT_LEVEL;
    }

    public LogHandler getHandler() {
        return handler;
    }

    public void setHandler(LogHandler handler) {
        this.handler = handler != null ? handler : this::defaultLogHandler;
    }

    public static Logger createLogger() {
        return createLogger(new CreateLoggerOptions());
    }

    public static Logger createLogger(CreateLoggerOptions options) {
        return new Logger(options);
    }
}
