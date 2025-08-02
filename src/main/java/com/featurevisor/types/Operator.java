package com.featurevisor.types;

public enum Operator {
    EQUALS("equals"),
    NOT_EQUALS("notEquals"),
    EXISTS("exists"),
    NOT_EXISTS("notExists"),

    // numeric
    GREATER_THAN("greaterThan"),
    GREATER_THAN_OR_EQUALS("greaterThanOrEquals"),
    LESS_THAN("lessThan"),
    LESS_THAN_OR_EQUALS("lessThanOrEquals"),

    // string
    CONTAINS("contains"),
    NOT_CONTAINS("notContains"),
    STARTS_WITH("startsWith"),
    ENDS_WITH("endsWith"),

    // semver (string)
    SEMVER_EQUALS("semverEquals"),
    SEMVER_NOT_EQUALS("semverNotEquals"),
    SEMVER_GREATER_THAN("semverGreaterThan"),
    SEMVER_GREATER_THAN_OR_EQUALS("semverGreaterThanOrEquals"),
    SEMVER_LESS_THAN("semverLessThan"),
    SEMVER_LESS_THAN_OR_EQUALS("semverLessThanOrEquals"),

    // date comparisons
    BEFORE("before"),
    AFTER("after"),

    // array of strings
    INCLUDES("includes"),
    NOT_INCLUDES("notIncludes"),

    // regex
    MATCHES("matches"),
    NOT_MATCHES("notMatches"),

    // array of strings
    IN("in"),
    NOT_IN("notIn");

    private final String value;

    Operator(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
