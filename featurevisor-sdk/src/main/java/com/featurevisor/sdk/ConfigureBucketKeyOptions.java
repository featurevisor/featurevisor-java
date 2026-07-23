package com.featurevisor.sdk;

import java.util.Map;

public final class ConfigureBucketKeyOptions {
    private String featureKey;
    private Map<String, Object> context;
    private Bucket bucketBy;
    private String bucketKey;

    public ConfigureBucketKeyOptions(
            String featureKey,
            Map<String, Object> context,
            Bucket bucketBy,
            String bucketKey) {
        this.featureKey = featureKey;
        this.context = context;
        this.bucketBy = bucketBy;
        this.bucketKey = bucketKey;
    }

    public String getFeatureKey() { return featureKey; }
    public Map<String, Object> getContext() { return context; }
    public Bucket getBucketBy() { return bucketBy; }
    public String getBucketKey() { return bucketKey; }

    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setBucketBy(Bucket bucketBy) { this.bucketBy = bucketBy; }
    public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
}
