package com.featurevisor.sdk;

@FunctionalInterface
interface FeaturevisorModuleApiFactory {
    FeaturevisorModuleApi create(FeaturevisorModule module);
}
