package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a feature assertion
 */
public class FeatureAssertion {
    @JsonProperty("matrix")
    private AssertionMatrix matrix;

    @JsonProperty("description")
    private String description;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("at")
    private Integer at; // bucket weight: 0 to 100

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

    @JsonProperty("children")
    private List<FeatureChildAssertion> children;

    public FeatureAssertion() {}

    public AssertionMatrix getMatrix() {
        return matrix;
    }

    public void setMatrix(AssertionMatrix matrix) {
        this.matrix = matrix;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Integer getAt() {
        return at;
    }

    public void setAt(Integer at) {
        this.at = at;
    }

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

    public List<FeatureChildAssertion> getChildren() {
        return children;
    }

    public void setChildren(List<FeatureChildAssertion> children) {
        this.children = children;
    }
}
