package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Force {
    @JsonProperty("conditions")
    private Object conditions; // Can be Condition, List<Condition>

    @JsonProperty("segments")
    private Object segments; // Can be GroupSegment, List<GroupSegment>

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("variation")
    private String variation;

    @JsonProperty("variables")
    private Map<String, Object> variables;

    // Constructors
    public Force() {}

    // Getters and Setters
    public Object getConditions() {
        return conditions;
    }

    public void setConditions(Object conditions) {
        this.conditions = conditions;
    }

    public Object getSegments() {
        return segments;
    }

    public void setSegments(Object segments) {
        this.segments = segments;
    }

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
