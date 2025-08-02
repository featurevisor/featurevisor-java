package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class EvaluatedFeature {
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("variation")
    private String variation;

    @JsonProperty("variables")
    private Map<String, Object> variables;

    // Constructors
    public EvaluatedFeature() {}

    public EvaluatedFeature(Boolean enabled) {
        this.enabled = enabled;
    }

    // Getters and Setters
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getVariation() {
        return variation;
    }

    public void setVariation(String variation) {
        this.variation = variation;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
