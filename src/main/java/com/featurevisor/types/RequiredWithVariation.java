package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RequiredWithVariation {
    @JsonProperty("key")
    private String key;

    @JsonProperty("variation")
    private String variation;

    // Constructors
    public RequiredWithVariation() {}

    public RequiredWithVariation(String key, String variation) {
        this.key = key;
        this.variation = variation;
    }

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getVariation() {
        return variation;
    }

    public void setVariation(String variation) {
        this.variation = variation;
    }
}
