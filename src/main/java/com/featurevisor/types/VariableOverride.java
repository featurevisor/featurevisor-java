package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VariableOverride {
    @JsonProperty("value")
    private Object value;

    @JsonProperty("conditions")
    private Object conditions; // Can be Condition, List<Condition>

    @JsonProperty("segments")
    private Object segments; // Can be GroupSegment, List<GroupSegment>

    // Constructors
    public VariableOverride() {}

    public VariableOverride(Object value) {
        this.value = value;
    }

    // Getters and Setters
    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

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
}
