package com.featurevisor.types;

/**
 * VariableKey type alias
 * Corresponds to: export type VariableKey = string;
 */
public class VariableKey {
    private String value;

    public VariableKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static VariableKey of(String value) {
        return new VariableKey(value);
    }
}
