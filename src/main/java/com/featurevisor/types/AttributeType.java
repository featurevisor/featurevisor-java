package com.featurevisor.types;

public enum AttributeType {
    BOOLEAN("boolean"),
    STRING("string"),
    INTEGER("integer"),
    DOUBLE("double"),
    DATE("date"),
    SEMVER("semver"),
    OBJECT("object"),
    ARRAY("array");

    private final String value;

    AttributeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
