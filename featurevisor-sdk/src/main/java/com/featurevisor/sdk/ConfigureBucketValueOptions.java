package com.featurevisor.sdk;

import java.util.Map;

public final class ConfigureBucketValueOptions {
    private String featureKey;
    private String bucketKey;
    private Map<String, Object> context;
    private int bucketValue;

    public ConfigureBucketValueOptions(
            String featureKey,
            String bucketKey,
            Map<String, Object> context,
            int bucketValue) {
        this.featureKey = featureKey;
        this.bucketKey = bucketKey;
        this.context = context;
        this.bucketValue = bucketValue;
    }

    public String getFeatureKey() { return featureKey; }
    public String getBucketKey() { return bucketKey; }
    public Map<String, Object> getContext() { return context; }
    public int getBucketValue() { return bucketValue; }

    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setBucketValue(int bucketValue) { this.bucketValue = bucketValue; }
}
