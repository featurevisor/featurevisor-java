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
                    if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
                        return (T) Integer.valueOf(((Number) value).intValue());
                    }
                    if (value instanceof Long) {
                        long longValue = ((Long) value).longValue();
                        return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE
                            ? (T) Integer.valueOf((int) longValue)
                            : null;
                    }
                    if (value instanceof Float || value instanceof Double) {
                        double doubleValue = ((Number) value).doubleValue();
                        return Double.isFinite(doubleValue) && doubleValue == Math.rint(doubleValue)
                            && doubleValue >= Integer.MIN_VALUE && doubleValue <= Integer.MAX_VALUE
                            ? (T) Integer.valueOf((int) doubleValue)
                            : null;
                    }
                    return null;
                case "double":
                    if (value instanceof Number) {
                        double doubleValue = ((Number) value).doubleValue();
                        return Double.isFinite(doubleValue) ? (T) Double.valueOf(doubleValue) : null;
                    }
                    return null;
                case "boolean":
                    return (T) (value instanceof Boolean ? value : null);
                case "array":
                    return (T) (value instanceof List ? value : null);
                case "object":
                    return (T) (value instanceof Map ? value : null);
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
