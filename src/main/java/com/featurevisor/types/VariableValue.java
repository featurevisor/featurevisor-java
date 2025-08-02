package com.featurevisor.types;

/**
 * VariableValue type alias
 * Corresponds to: export type VariableValue = string | number | boolean | object | array | null;
 */
public class VariableValue {
    private Object value;

    public VariableValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : null;
    }

    public static VariableValue of(Object value) {
        return new VariableValue(value);
    }
}
