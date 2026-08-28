package com.featurevisor.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VariableOverride {
    @JsonProperty("key") private String key;
    @JsonProperty("keyPath") private java.util.List<String> keyPath;
    @JsonProperty("value")
    private Object value;

    @JsonProperty("conditions")
    private Object conditions; // Can be Condition, List<Condition>

    @JsonProperty("segments")
    private Object segments; // Can be GroupSegment, List<GroupSegment>
    @JsonProperty("requiredFeatures") private java.util.List<Object> requiredFeatures;

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
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public java.util.List<String> getKeyPath() { return keyPath; }
    public void setKeyPath(java.util.List<String> keyPath) { this.keyPath = keyPath; }
    public java.util.List<Object> getRequiredFeatures() { return requiredFeatures; }
    public void setRequiredFeatures(java.util.List<Object> value) { this.requiredFeatures = value; }
}
