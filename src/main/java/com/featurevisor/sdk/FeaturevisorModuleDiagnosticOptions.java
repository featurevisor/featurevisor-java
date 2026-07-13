package com.featurevisor.sdk;

public class FeaturevisorModuleDiagnosticOptions {
    private FeaturevisorLogLevel logLevel = FeaturevisorLogLevel.INFO;

    public FeaturevisorModuleDiagnosticOptions() {}

    public FeaturevisorModuleDiagnosticOptions(FeaturevisorLogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public FeaturevisorLogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(FeaturevisorLogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public FeaturevisorModuleDiagnosticOptions logLevel(FeaturevisorLogLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }
}
