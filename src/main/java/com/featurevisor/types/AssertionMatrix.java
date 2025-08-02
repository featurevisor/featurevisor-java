package com.featurevisor.types;

import java.util.List;
import java.util.Map;

/**
 * Represents an assertion matrix
 */
public class AssertionMatrix {
    private Map<String, List<Object>> matrix;

    public AssertionMatrix() {}

    public AssertionMatrix(Map<String, List<Object>> matrix) {
        this.matrix = matrix;
    }

    public Map<String, List<Object>> getMatrix() {
        return matrix;
    }

    public void setMatrix(Map<String, List<Object>> matrix) {
        this.matrix = matrix;
    }
}
