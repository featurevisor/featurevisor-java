package com.featurevisor.sdk;

import java.util.List;
import java.util.Map;

/**
 * Helper utilities for Featurevisor SDK
 * Provides common utility functions used throughout the SDK
 */
public class Helpers {

    /**
     * Get value by type, converting the value to the specified type if possible
     * @param value The value to convert
     * @param fieldType The target type
     * @return The converted value, or null if conversion is not possible
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValueByType(Object value, String fieldType) {
        try {
            if (value == null) {
                return null;
            }

            switch (fieldType) {
                case "string":
                    return (T) (value instanceof String ? value : null);
                case "integer":
                    if (value instanceof Number) {
                        return (T) Integer.valueOf(((Number) value).intValue());
                    } else if (value instanceof String) {
                        return (T) Integer.valueOf((String) value);
                    }
                    return null;
                case "double":
                    if (value instanceof Number) {
                        return (T) Double.valueOf(((Number) value).doubleValue());
                    } else if (value instanceof String) {
                        return (T) Double.valueOf((String) value);
                    }
                    return null;
                case "boolean":
                    return (T) Boolean.valueOf(Boolean.TRUE.equals(value));
                case "array":
                    return (T) (value instanceof List ? value : null);
                case "object":
                    if (value instanceof Map) {
                        return (T) value;
                    }
                    if (value instanceof List) {
                        return (T) value;
                    }
                    return null;
                case "json":
                    // JSON type is handled specially in the calling code
                    return (T) value;
                default:
                    return (T) value;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
