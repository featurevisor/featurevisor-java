package com.featurevisor.sdk;

@FunctionalInterface
public interface FeaturevisorEventHandler {
    void handle(FeaturevisorEventDetails details);
}
