package com.featurevisor.sdk;

import com.featurevisor.sdk.Bucket;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;

/**
 * Bucketer for Featurevisor SDK
 * Handles bucketing logic for feature flags
 */
final class Bucketer {

    /**
     * Bucket key type
     */
    public static final String BUCKET_KEY = String.class.getName();

    /**
     * Bucket value type (0 to 100,000)
     */
    public static final int BUCKET_VALUE = 0; // Placeholder, actual value is int

    /**
     * Generic hashing constants
     */
    private static final int HASH_SEED = 1;
    private static final long MAX_HASH_VALUE = 4294967296L; // 2^32

    /**
     * Maximum bucketed number (100% * 1000 to include three decimal places)
     */
    public static final int MAX_BUCKETED_NUMBER = 100000;

    /**
     * Default bucket key separator
     */
    private static final String DEFAULT_BUCKET_KEY_SEPARATOR = ".";

    /**
     * Get a bucketed number from a bucket key
     * @param bucketKey The bucket key to hash
     * @return A number between 0 and 100000
     */
    public static int getBucketedNumber(String bucketKey) {
        int hashValue = MurmurHash.murmurHashV3(bucketKey, HASH_SEED);
        // Convert to unsigned 32-bit integer for calculation
        long unsignedHash = hashValue & 0xffffffffL;
        double ratio = (double) unsignedHash / MAX_HASH_VALUE;

        return (int) Math.floor(ratio * MAX_BUCKETED_NUMBER);
    }

    /**
     * Options for getting a bucket key
     */
    public static class GetBucketKeyOptions {
        private String featureKey;
        private Bucket bucketBy;
        private Map<String, Object> context;
        private DiagnosticReporter diagnostics;

        public GetBucketKeyOptions() {}

        public GetBucketKeyOptions featureKey(String featureKey) {
            this.featureKey = featureKey;
            return this;
        }

        public GetBucketKeyOptions bucketBy(Bucket bucketBy) {
            this.bucketBy = bucketBy;
            return this;
        }

        public GetBucketKeyOptions context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public GetBucketKeyOptions diagnostics(DiagnosticReporter diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        // Getters
        public String getFeatureKey() { return featureKey; }
        public Bucket getBucketBy() { return bucketBy; }
        public Map<String, Object> getContext() { return context; }
        public DiagnosticReporter getDiagnostics() { return diagnostics; }
    }

    /**
     * Get a bucket key from the given options
     * @param options The options containing feature key, bucketBy, context, and diagnostics
     * @return The bucket key string
     */
    public static String getBucketKey(GetBucketKeyOptions options) {
        String featureKey = options.getFeatureKey();
        Bucket bucketBy = options.getBucketBy();
        Map<String, Object> context = options.getContext();
        DiagnosticReporter diagnostics = options.getDiagnostics();

        String type;
        List<String> attributeKeys;

        if (bucketBy.isPlainBucketBy()) {
            type = "plain";
            attributeKeys = new ArrayList<>();
            attributeKeys.add(bucketBy.getPlainBucketBy());
        } else if (bucketBy.isAndBucketBy()) {
            type = "and";
            attributeKeys = bucketBy.getAndBucketBy();
        } else if (bucketBy.isOrBucketBy()) {
            type = "or";
            attributeKeys = bucketBy.getOr();
        } else {
            Map<String, Object> details = new java.util.HashMap<>();
            details.put("featureKey", featureKey);
            details.put("bucketBy", bucketBy);
            diagnostics.error("invalid bucketBy", details);

            throw new RuntimeException("invalid bucketBy");
        }

        List<Object> bucketKey = new ArrayList<>();

        for (String attributeKey : attributeKeys) {
            Object attributeValue = ContextUtils.getValueFromContext(context, attributeKey);

            if (attributeValue == null && !pathExists(context, attributeKey)) {
                continue;
            }

            if ("plain".equals(type) || "and".equals(type)) {
                bucketKey.add(attributeValue);
            } else {
                // or
                if (bucketKey.isEmpty()) {
                    bucketKey.add(attributeValue);
                }
            }
        }

        bucketKey.add(featureKey);

        return String.join(DEFAULT_BUCKET_KEY_SEPARATOR,
            bucketKey.stream().map(Bucketer::javascriptString).toArray(String[]::new));
    }

    @SuppressWarnings("unchecked")
    private static boolean pathExists(Map<String, Object> context, String path) {
        Object current = context;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map) || !((Map<String, Object>) current).containsKey(key)) {
                return false;
            }
            current = ((Map<String, Object>) current).get(key);
        }
        return true;
    }

    private static String javascriptString(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean) return (Boolean) value ? "true" : "false";
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number)) return "NaN";
            if (number == Double.POSITIVE_INFINITY) return "Infinity";
            if (number == Double.NEGATIVE_INFINITY) return "-Infinity";
            if (number == 0) return "0";
            BigDecimal decimal = value instanceof Float
                ? new BigDecimal(Float.toString((Float) value))
                : BigDecimal.valueOf(number);
            decimal = decimal.stripTrailingZeros();
            double absolute = Math.abs(number);
            return absolute >= 1e21 || absolute < 1e-6
                ? decimal.toString().replace("E", "e")
                : decimal.toPlainString();
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) value;
            return String.join(",", values.stream().map(Bucketer::javascriptString).toArray(String[]::new));
        }
        if (value instanceof Map) return "[object Object]";
        return value.toString();
    }
}
