package com.featurevisor.sdk;

@FunctionalInterface
interface FeaturevisorDiagnosticReporter {
    void report(FeaturevisorDiagnostic diagnostic, FeaturevisorModule sourceModule);
}
