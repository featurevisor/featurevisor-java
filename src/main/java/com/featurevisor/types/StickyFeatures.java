package com.featurevisor.types;

import java.util.Map;

/**
 * StickyFeatures type alias
 * Corresponds to: export type StickyFeatures = EvaluatedFeatures;
 */
public class StickyFeatures {
    private Map<String, EvaluatedFeature> value;

    public StickyFeatures(Map<String, EvaluatedFeature> value) {
        this.value = value;
    }

    public Map<String, EvaluatedFeature> getValue() {
        return value;
    }

    public void setValue(Map<String, EvaluatedFeature> value) {
        this.value = value;
    }

    public static StickyFeatures of(Map<String, EvaluatedFeature> value) {
        return new StickyFeatures(value);
    }
}
