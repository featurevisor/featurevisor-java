package com.featurevisor.types;

import java.util.Map;

/**
 * EvaluatedFeatures type alias
 * Corresponds to: export interface EvaluatedFeatures { [key: FeatureKey]: EvaluatedFeature; }
 */
public class EvaluatedFeatures {
    private Map<String, EvaluatedFeature> value;

    public EvaluatedFeatures(Map<String, EvaluatedFeature> value) {
        this.value = value;
    }

    public Map<String, EvaluatedFeature> getValue() {
        return value;
    }

    public void setValue(Map<String, EvaluatedFeature> value) {
        this.value = value;
    }

    public static EvaluatedFeatures of(Map<String, EvaluatedFeature> value) {
        return new EvaluatedFeatures(value);
    }
}
