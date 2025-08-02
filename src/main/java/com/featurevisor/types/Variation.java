package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class Variation {
    @JsonProperty("description")
    private String description;

    @JsonProperty("value")
    private String value;

    @JsonProperty("weight")
    private Integer weight;

    @JsonProperty("variables")
    private Map<String, Object> variables;

    @JsonProperty("variableOverrides")
    private Map<String, List<VariableOverride>> variableOverrides;

    // Constructors
    public Variation() {}

    public Variation(String value) {
        this.value = value;
    }

    // Getters and Setters
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Map<String, List<VariableOverride>> getVariableOverrides() {
        return variableOverrides;
    }

    public void setVariableOverrides(Map<String, List<VariableOverride>> variableOverrides) {
        this.variableOverrides = variableOverrides;
    }
}
