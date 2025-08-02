package com.featurevisor.sdk;

import java.util.Map;

/**
 * Utility class for working with context objects
 */
public class ContextUtils {

    /**
     * Get a value from a context object using a dot-separated path
     * @param context The context object (Map<String, Object>)
     * @param path The dot-separated path to the value
     * @return The value at the path, or null if not found
     */
    public static Object getValueFromContext(Map<String, Object> context, String path) {
        if (path.indexOf(".") == -1) {
            return context.get(path);
        }

        String[] parts = path.split("\\.");
        Object current = context;

        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null;
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
