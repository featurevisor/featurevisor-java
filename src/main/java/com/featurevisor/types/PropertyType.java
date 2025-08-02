package com.featurevisor.types;

public enum PropertyType {
    BOOLEAN("boolean"),
    STRING("string"),
    INTEGER("integer"),
    DOUBLE("double"),
    DATE("date"),
    SEMVER("semver"),
    ARRAY("array");

    private final String value;

    PropertyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
