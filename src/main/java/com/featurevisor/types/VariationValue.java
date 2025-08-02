package com.featurevisor.types;

/**
 * VariationValue type alias
 * Corresponds to: export type VariationValue = string;
 */
public class VariationValue {
    private String value;

    public VariationValue(String value) {
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

    public static VariationValue of(String value) {
        return new VariationValue(value);
    }
}
