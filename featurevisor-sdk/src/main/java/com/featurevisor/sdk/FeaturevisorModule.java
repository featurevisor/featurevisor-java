package com.featurevisor.sdk;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class FeaturevisorModule {
    private final String id = UUID.randomUUID().toString();
    private String name;
    private Consumer<FeaturevisorModuleApi> setup;
    private Function<EvaluateOptions, EvaluateOptions> before;
    private ConfigureBucketKey bucketKey;
    private ConfigureBucketValue bucketValue;
    private BiFunction<Evaluation, EvaluateOptions, Evaluation> after;
    private Runnable close;

    public FeaturevisorModule(String name) {
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Consumer<FeaturevisorModuleApi> getSetup() { return setup; }
    public Function<EvaluateOptions, EvaluateOptions> getBefore() { return before; }
    public ConfigureBucketKey getBucketKey() { return bucketKey; }
    public ConfigureBucketValue getBucketValue() { return bucketValue; }
    public BiFunction<Evaluation, EvaluateOptions, Evaluation> getAfter() { return after; }
    public Runnable getClose() { return close; }

    public void setName(String name) { this.name = name; }
    public void setSetup(Consumer<FeaturevisorModuleApi> setup) { this.setup = setup; }
    public void setBefore(Function<EvaluateOptions, EvaluateOptions> before) { this.before = before; }
    public void setBucketKey(ConfigureBucketKey bucketKey) { this.bucketKey = bucketKey; }
    public void setBucketValue(ConfigureBucketValue bucketValue) { this.bucketValue = bucketValue; }
    public void setAfter(BiFunction<Evaluation, EvaluateOptions, Evaluation> after) { this.after = after; }
    public void setClose(Runnable close) { this.close = close; }

    public FeaturevisorModule setup(Consumer<FeaturevisorModuleApi> setup) {
        this.setup = setup;
        return this;
    }

    public FeaturevisorModule before(Function<EvaluateOptions, EvaluateOptions> before) {
        this.before = before;
        return this;
    }

    public FeaturevisorModule bucketKey(ConfigureBucketKey bucketKey) {
        this.bucketKey = bucketKey;
        return this;
    }

    public FeaturevisorModule bucketValue(ConfigureBucketValue bucketValue) {
        this.bucketValue = bucketValue;
        return this;
    }

    public FeaturevisorModule after(BiFunction<Evaluation, EvaluateOptions, Evaluation> after) {
        this.after = after;
        return this;
    }

    public FeaturevisorModule close(Runnable close) {
        this.close = close;
        return this;
    }
}
