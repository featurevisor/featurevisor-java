package com.featurevisor.sdk;

public enum FeaturevisorEventName {
    DATAFILE_SET("datafile_set"),
    CONTEXT_SET("context_set"),
    STICKY_FEATURES_SET("sticky_features_set"),
    STICKY_VARIABLES_SET("sticky_variables_set"),
    ERROR("error");

    private final String value;

    FeaturevisorEventName(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FeaturevisorEventName fromString(String value) {
        for (FeaturevisorEventName eventName : values()) {
            if (eventName.value.equals(value)) {
                return eventName;
            }
        }
        throw new IllegalArgumentException("Unknown event name: " + value);
    }
}
