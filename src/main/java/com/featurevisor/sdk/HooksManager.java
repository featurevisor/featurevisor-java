package com.featurevisor.sdk;

import com.featurevisor.types.Bucket;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.BiFunction;

/**
 * Hooks utility for Featurevisor SDK
 * Provides hook management functionality for customizing evaluation behavior
 */
public class HooksManager {
    private List<Hook> hooks = new ArrayList<>();
    private Logger logger;

    /**
     * Options for configuring bucket key
     */
    public static class ConfigureBucketKeyOptions {
        private String featureKey;
        private Map<String, Object> context;
        private Bucket bucketBy;
        private String bucketKey; // the initial bucket key, which can be modified by hooks

        public ConfigureBucketKeyOptions(String featureKey, Map<String, Object> context, Bucket bucketBy, String bucketKey) {
            this.featureKey = featureKey;
            this.context = context;
            this.bucketBy = bucketBy;
            this.bucketKey = bucketKey;
        }

        // Getters
        public String getFeatureKey() { return featureKey; }
        public Map<String, Object> getContext() { return context; }
        public Bucket getBucketBy() { return bucketBy; }
        public String getBucketKey() { return bucketKey; }

        // Setters
        public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setBucketBy(Bucket bucketBy) { this.bucketBy = bucketBy; }
        public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
    }

    /**
     * Options for configuring bucket value
     */
    public static class ConfigureBucketValueOptions {
        private String featureKey;
        private String bucketKey;
        private Map<String, Object> context;
        private int bucketValue; // the initial bucket value, which can be modified by hooks

        public ConfigureBucketValueOptions(String featureKey, String bucketKey, Map<String, Object> context, int bucketValue) {
            this.featureKey = featureKey;
            this.bucketKey = bucketKey;
            this.context = context;
            this.bucketValue = bucketValue;
        }

        // Getters
        public String getFeatureKey() { return featureKey; }
        public String getBucketKey() { return bucketKey; }
        public Map<String, Object> getContext() { return context; }
        public int getBucketValue() { return bucketValue; }

        // Setters
        public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
        public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setBucketValue(int bucketValue) { this.bucketValue = bucketValue; }
    }

    /**
     * Functional interface for configuring bucket key
     */
    @FunctionalInterface
    public interface ConfigureBucketKey {
        String configure(ConfigureBucketKeyOptions options);
    }

    /**
     * Functional interface for configuring bucket value
     */
    @FunctionalInterface
    public interface ConfigureBucketValue {
        int configure(ConfigureBucketValueOptions options);
    }

    /**
     * Hook interface for customizing evaluation behavior
     */
    public static class Hook {
        private String name;
        private Function<EvaluateOptions, EvaluateOptions> before;
        private ConfigureBucketKey bucketKey;
        private ConfigureBucketValue bucketValue;
        private BiFunction<Evaluation, EvaluateOptions, Evaluation> after;

        public Hook(String name) {
            this.name = name;
        }

        // Getters
        public String getName() { return name; }
        public Function<EvaluateOptions, EvaluateOptions> getBefore() { return before; }
        public ConfigureBucketKey getBucketKey() { return bucketKey; }
        public ConfigureBucketValue getBucketValue() { return bucketValue; }
        public BiFunction<Evaluation, EvaluateOptions, Evaluation> getAfter() { return after; }

        // Setters
        public void setBefore(Function<EvaluateOptions, EvaluateOptions> before) { this.before = before; }
        public void setBucketKey(ConfigureBucketKey bucketKey) { this.bucketKey = bucketKey; }
        public void setBucketValue(ConfigureBucketValue bucketValue) { this.bucketValue = bucketValue; }
        public void setAfter(BiFunction<Evaluation, EvaluateOptions, Evaluation> after) { this.after = after; }

        // Builder pattern methods
        public Hook before(Function<EvaluateOptions, EvaluateOptions> before) {
            this.before = before;
            return this;
        }

        public Hook bucketKey(ConfigureBucketKey bucketKey) {
            this.bucketKey = bucketKey;
            return this;
        }

        public Hook bucketValue(ConfigureBucketValue bucketValue) {
            this.bucketValue = bucketValue;
            return this;
        }

        public Hook after(BiFunction<Evaluation, EvaluateOptions, Evaluation> after) {
            this.after = after;
            return this;
        }
    }

    /**
     * Options for HooksManager constructor
     */
    public static class HooksManagerOptions {
        private List<Hook> hooks;
        private Logger logger;

        public HooksManagerOptions(Logger logger) {
            this.logger = logger;
        }

        public HooksManagerOptions hooks(List<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        public HooksManagerOptions logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        // Getters
        public List<Hook> getHooks() { return hooks; }
        public Logger getLogger() { return logger; }
    }

    /**
     * Constructor
     */
    public HooksManager(HooksManagerOptions options) {
        this.logger = options.getLogger();

        if (options.getHooks() != null) {
            for (Hook hook : options.getHooks()) {
                add(hook);
            }
        }
    }

    /**
     * Add a hook
     * @param hook The hook to add
     * @return A function to remove the hook, or null if hook with same name already exists
     */
    public Runnable add(Hook hook) {
        if (hooks.stream().anyMatch(existingHook -> existingHook.getName().equals(hook.getName()))) {
            logger.error("Hook with name \"" + hook.getName() + "\" already exists.", Map.of(
                "name", hook.getName(),
                "hook", hook
            ));
            return null;
        }

        hooks.add(hook);

        return () -> remove(hook.getName());
    }

    /**
     * Remove a hook by name
     * @param name The name of the hook to remove
     */
    public void remove(String name) {
        hooks.removeIf(hook -> hook.getName().equals(name));
    }

    /**
     * Get all hooks
     * @return List of all hooks
     */
    public List<Hook> getAll() {
        return new ArrayList<>(hooks);
    }

    /**
     * Execute before hooks
     * @param options The evaluation options
     * @return Modified evaluation options
     */
    public EvaluateOptions executeBeforeHooks(EvaluateOptions options) {
        EvaluateOptions currentOptions = options;
        for (Hook hook : hooks) {
            if (hook.getBefore() != null) {
                currentOptions = hook.getBefore().apply(currentOptions);
            }
        }
        return currentOptions;
    }

    /**
     * Execute after hooks
     * @param evaluation The evaluation result
     * @param options The evaluation options
     * @return Modified evaluation result
     */
    public Evaluation executeAfterHooks(Evaluation evaluation, EvaluateOptions options) {
        Evaluation currentEvaluation = evaluation;
        for (Hook hook : hooks) {
            if (hook.getAfter() != null) {
                currentEvaluation = hook.getAfter().apply(currentEvaluation, options);
            }
        }
        return currentEvaluation;
    }

    /**
     * Execute bucket key hooks
     * @param options The bucket key options
     * @return Modified bucket key
     */
    public String executeBucketKeyHooks(ConfigureBucketKeyOptions options) {
        String currentBucketKey = options.getBucketKey();
        for (Hook hook : hooks) {
            if (hook.getBucketKey() != null) {
                ConfigureBucketKeyOptions hookOptions = new ConfigureBucketKeyOptions(
                    options.getFeatureKey(),
                    options.getContext(),
                    options.getBucketBy(),
                    currentBucketKey
                );
                currentBucketKey = hook.getBucketKey().configure(hookOptions);
            }
        }
        return currentBucketKey;
    }

    /**
     * Execute bucket value hooks
     * @param options The bucket value options
     * @return Modified bucket value
     */
    public int executeBucketValueHooks(ConfigureBucketValueOptions options) {
        int currentBucketValue = options.getBucketValue();
        for (Hook hook : hooks) {
            if (hook.getBucketValue() != null) {
                ConfigureBucketValueOptions hookOptions = new ConfigureBucketValueOptions(
                    options.getFeatureKey(),
                    options.getBucketKey(),
                    options.getContext(),
                    currentBucketValue
                );
                currentBucketValue = hook.getBucketValue().configure(hookOptions);
            }
        }
        return currentBucketValue;
    }
}
