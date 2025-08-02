package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a feature child assertion
 */
public class FeatureChildAssertion {
    @JsonProperty("sticky")
    private Map<String, Object> sticky;

    @JsonProperty("context")
    private Map<String, Object> context;

    @JsonProperty("defaultVariationValue")
    private Object defaultVariationValue;

    @JsonProperty("defaultVariableValues")
    private Map<String, Object> defaultVariableValues;

    @JsonProperty("expectedToBeEnabled")
    private Boolean expectedToBeEnabled;

    @JsonProperty("expectedVariation")
    private Object expectedVariation;

    @JsonProperty("expectedVariables")
    private Map<String, Object> expectedVariables;

    @JsonProperty("expectedEvaluations")
    private ExpectedEvaluations expectedEvaluations;

    public FeatureChildAssertion() {}

    public Map<String, Object> getSticky() {
        return sticky;
    }

    public void setSticky(Map<String, Object> sticky) {
        this.sticky = sticky;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Object getDefaultVariationValue() {
        return defaultVariationValue;
    }

    public void setDefaultVariationValue(Object defaultVariationValue) {
        this.defaultVariationValue = defaultVariationValue;
    }

    public Map<String, Object> getDefaultVariableValues() {
        return defaultVariableValues;
    }

    public void setDefaultVariableValues(Map<String, Object> defaultVariableValues) {
        this.defaultVariableValues = defaultVariableValues;
    }

    public Boolean getExpectedToBeEnabled() {
        return expectedToBeEnabled;
    }

    public void setExpectedToBeEnabled(Boolean expectedToBeEnabled) {
        this.expectedToBeEnabled = expectedToBeEnabled;
    }

    public Object getExpectedVariation() {
        return expectedVariation;
    }

    public void setExpectedVariation(Object expectedVariation) {
        this.expectedVariation = expectedVariation;
    }

    public Map<String, Object> getExpectedVariables() {
        return expectedVariables;
    }

    public void setExpectedVariables(Map<String, Object> expectedVariables) {
        this.expectedVariables = expectedVariables;
    }

    public ExpectedEvaluations getExpectedEvaluations() {
        return expectedEvaluations;
    }

    public void setExpectedEvaluations(ExpectedEvaluations expectedEvaluations) {
        this.expectedEvaluations = expectedEvaluations;
    }
}
