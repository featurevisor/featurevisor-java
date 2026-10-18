package com.featurevisor.sdk;

import java.util.Objects;

/** The immediate evaluation requesting a prerequisite getter. */
public final class DependencyOf {
    private final String type;
    private final String featureKey;
    private final String variableKey;

    public DependencyOf(String type, String featureKey, String variableKey) {
        this.type = type;
        this.featureKey = featureKey;
        this.variableKey = variableKey;
    }

    static DependencyOf from(EvaluateOptions options) {
        return new DependencyOf(options.getType(), options.isGlobalVariable() ? null : options.getFeatureKey(), options.getVariableKey());
    }

    public String getType() { return type; }
    public String getFeatureKey() { return featureKey; }
    public String getVariableKey() { return variableKey; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof DependencyOf)) return false;
        DependencyOf value = (DependencyOf) other;
        return Objects.equals(type, value.type)
            && Objects.equals(featureKey, value.featureKey)
            && Objects.equals(variableKey, value.variableKey);
    }

    @Override public int hashCode() { return Objects.hash(type, featureKey, variableKey); }
}
