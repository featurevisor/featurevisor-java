package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a test feature
 */
public class TestFeature {
    @JsonProperty("key")
    private String key; // file path

    @JsonProperty("feature")
    private String feature;

    @JsonProperty("assertions")
    private List<FeatureAssertion> assertions;

    public TestFeature() {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public List<FeatureAssertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<FeatureAssertion> assertions) {
        this.assertions = assertions;
    }
}
