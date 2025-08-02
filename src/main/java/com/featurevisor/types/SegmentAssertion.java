package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a segment assertion
 */
public class SegmentAssertion {
    @JsonProperty("matrix")
    private AssertionMatrix matrix;

    @JsonProperty("description")
    private String description;

    @JsonProperty("context")
    private Map<String, Object> context;

    @JsonProperty("expectedToMatch")
    private Boolean expectedToMatch;

    public SegmentAssertion() {}

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

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Boolean getExpectedToMatch() {
        return expectedToMatch;
    }

    public void setExpectedToMatch(Boolean expectedToMatch) {
        this.expectedToMatch = expectedToMatch;
    }
}
