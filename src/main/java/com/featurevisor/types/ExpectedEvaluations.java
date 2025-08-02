package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents expected evaluations
 */
public class ExpectedEvaluations {
    @JsonProperty("flag")
    private Map<String, Object> flag;

    @JsonProperty("variation")
    private Map<String, Object> variation;

    @JsonProperty("variables")
    private Map<String, Map<String, Object>> variables;

    public ExpectedEvaluations() {}

    public Map<String, Object> getFlag() {
        return flag;
    }

    public void setFlag(Map<String, Object> flag) {
        this.flag = flag;
    }

    public Map<String, Object> getVariation() {
        return variation;
    }

    public void setVariation(Map<String, Object> variation) {
        this.variation = variation;
    }

    public Map<String, Map<String, Object>> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Map<String, Object>> variables) {
        this.variables = variables;
    }
}
