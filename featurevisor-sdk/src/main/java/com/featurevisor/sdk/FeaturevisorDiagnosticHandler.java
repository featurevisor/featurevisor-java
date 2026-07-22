package com.featurevisor.sdk;

@FunctionalInterface
public interface FeaturevisorDiagnosticHandler {
    void handle(FeaturevisorDiagnostic diagnostic);
}
