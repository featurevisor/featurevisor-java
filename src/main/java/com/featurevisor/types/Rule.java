package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Rule {
    @JsonProperty("key")
    private String key;

    @JsonProperty("description")
    private String description;

    @JsonProperty("segments")
    private Object segments; // Can be GroupSegment, List<GroupSegment>, or "*"

    @JsonProperty("percentage")
    private Integer percentage;

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("variation")
    private String variation;

    @JsonProperty("variables")
    private Map<String, Object> variables;

    @JsonProperty("variationWeights")
    private Map<String, Integer> variationWeights;

    // Constructors
    public Rule() {}

    public Rule(String key) {
        this.key = key;
    }

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getSegments() {
        return segments;
    }

    public void setSegments(Object segments) {
        this.segments = segments;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
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

    public Map<String, Integer> getVariationWeights() {
        return variationWeights;
    }

    public void setVariationWeights(Map<String, Integer> variationWeights) {
        this.variationWeights = variationWeights;
    }
}
