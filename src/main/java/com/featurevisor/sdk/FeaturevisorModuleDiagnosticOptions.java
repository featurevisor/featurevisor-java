package com.featurevisor.sdk;

public class FeaturevisorModuleDiagnosticOptions {
    private Logger.LogLevel logLevel = Logger.LogLevel.INFO;

    public FeaturevisorModuleDiagnosticOptions() {}

    public FeaturevisorModuleDiagnosticOptions(Logger.LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public Logger.LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(Logger.LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public FeaturevisorModuleDiagnosticOptions logLevel(Logger.LogLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }
}
