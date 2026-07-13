package com.featurevisor.sdk;

public interface FeaturevisorModuleApi {
    String getRevision();

    Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler);

    Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler, FeaturevisorModuleDiagnosticOptions options);

    void reportDiagnostic(FeaturevisorDiagnostic diagnostic);
}
