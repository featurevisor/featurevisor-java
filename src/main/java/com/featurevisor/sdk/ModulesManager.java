package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModulesManager {
    private final List<FeaturevisorModule> modules = new ArrayList<>();
    private final FeaturevisorDiagnosticReporter diagnosticReporter;
    private final FeaturevisorModuleApiFactory moduleApiFactory;
    private final java.util.function.Consumer<FeaturevisorModule> clearModuleDiagnosticSubscriptions;

    public static class ConfigureBucketKeyOptions {
        private String featureKey;
        private Map<String, Object> context;
        private Bucket bucketBy;
        private String bucketKey;

        public ConfigureBucketKeyOptions(String featureKey, Map<String, Object> context, Bucket bucketBy, String bucketKey) {
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

    public static class ConfigureBucketValueOptions {
        private String featureKey;
        private String bucketKey;
        private Map<String, Object> context;
        private int bucketValue;

        public ConfigureBucketValueOptions(String featureKey, String bucketKey, Map<String, Object> context, int bucketValue) {
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

    @FunctionalInterface
    public interface ConfigureBucketKey {
        String configure(ConfigureBucketKeyOptions options);
    }

    @FunctionalInterface
    public interface ConfigureBucketValue {
        int configure(ConfigureBucketValueOptions options);
    }

    public static class ModulesManagerOptions {
        private List<FeaturevisorModule> modules;
        private FeaturevisorDiagnosticReporter diagnosticReporter;
        private FeaturevisorModuleApiFactory moduleApiFactory;
        private java.util.function.Consumer<FeaturevisorModule> clearModuleDiagnosticSubscriptions;

        public ModulesManagerOptions modules(List<FeaturevisorModule> modules) {
            this.modules = modules;
            return this;
        }

        public ModulesManagerOptions diagnosticReporter(FeaturevisorDiagnosticReporter diagnosticReporter) {
            this.diagnosticReporter = diagnosticReporter;
            return this;
        }

        public ModulesManagerOptions moduleApiFactory(FeaturevisorModuleApiFactory moduleApiFactory) {
            this.moduleApiFactory = moduleApiFactory;
            return this;
        }

        public ModulesManagerOptions clearModuleDiagnosticSubscriptions(java.util.function.Consumer<FeaturevisorModule> clearModuleDiagnosticSubscriptions) {
            this.clearModuleDiagnosticSubscriptions = clearModuleDiagnosticSubscriptions;
            return this;
        }

        public List<FeaturevisorModule> getModules() { return modules; }
        public FeaturevisorDiagnosticReporter getDiagnosticReporter() { return diagnosticReporter; }
        public FeaturevisorModuleApiFactory getModuleApiFactory() { return moduleApiFactory; }
        public java.util.function.Consumer<FeaturevisorModule> getClearModuleDiagnosticSubscriptions() { return clearModuleDiagnosticSubscriptions; }
    }

    public ModulesManager(ModulesManagerOptions options) {
        this.diagnosticReporter = options.getDiagnosticReporter();
        this.moduleApiFactory = options.getModuleApiFactory();
        this.clearModuleDiagnosticSubscriptions = options.getClearModuleDiagnosticSubscriptions();

        if (options.getModules() != null) {
            for (FeaturevisorModule module : options.getModules()) {
                add(module);
            }
        }
    }

    public Runnable add(FeaturevisorModule module) {
        if (module == null) {
            return null;
        }

        String name = module.getName();
        if (name != null && !name.isBlank() && modules.stream().anyMatch(existingModule -> name.equals(existingModule.getName()))) {
            report(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.ERROR)
                .code("duplicate_module")
                .message("Duplicate module name")
                .moduleName(name), module);
            return null;
        }

        if (module.getSetup() != null && moduleApiFactory != null) {
            try {
                module.getSetup().accept(moduleApiFactory.create(module));
            } catch (Throwable error) {
                if (clearModuleDiagnosticSubscriptions != null) {
                    clearModuleDiagnosticSubscriptions.accept(module);
                }
                report(new FeaturevisorDiagnostic()
                    .level(FeaturevisorLogLevel.ERROR)
                    .code("module_setup_error")
                    .message("Module setup failed")
                    .moduleName(module.getName())
                    .originalError(String.valueOf(error)), null);
                closeModule(module);
                return null;
            }
        }

        modules.add(module);
        return () -> remove(module);
    }

    public void remove(String name) {
        if (name == null) {
            return;
        }
        List<FeaturevisorModule> toRemove = new ArrayList<>();
        for (FeaturevisorModule module : modules) {
            if (name.equals(module.getName())) {
                toRemove.add(module);
            }
        }
        for (FeaturevisorModule module : toRemove) {
            remove(module);
        }
    }

    public void remove(FeaturevisorModule module) {
        if (module == null) {
            return;
        }
        if (modules.remove(module)) {
            if (clearModuleDiagnosticSubscriptions != null) {
                clearModuleDiagnosticSubscriptions.accept(module);
            }
            closeModule(module);
        }
    }

    public List<FeaturevisorModule> getAll() {
        return new ArrayList<>(modules);
    }

    public EvaluateOptions executeBeforeModules(EvaluateOptions options) {
        EvaluateOptions currentOptions = options;
        for (FeaturevisorModule module : modules) {
            if (module.getBefore() != null) {
                currentOptions = module.getBefore().apply(currentOptions);
            }
        }
        return currentOptions;
    }

    public Evaluation executeAfterModules(Evaluation evaluation, EvaluateOptions options) {
        Evaluation currentEvaluation = evaluation;
        for (FeaturevisorModule module : modules) {
            if (module.getAfter() != null) {
                currentEvaluation = module.getAfter().apply(currentEvaluation, options);
            }
        }
        return currentEvaluation;
    }

    public String executeBucketKeyModules(ConfigureBucketKeyOptions options) {
        String currentBucketKey = options.getBucketKey();
        for (FeaturevisorModule module : modules) {
            if (module.getBucketKey() != null) {
                currentBucketKey = module.getBucketKey().configure(new ConfigureBucketKeyOptions(
                    options.getFeatureKey(),
                    options.getContext(),
                    options.getBucketBy(),
                    currentBucketKey
                ));
            }
        }
        return currentBucketKey;
    }

    public int executeBucketValueModules(ConfigureBucketValueOptions options) {
        int currentBucketValue = options.getBucketValue();
        for (FeaturevisorModule module : modules) {
            if (module.getBucketValue() != null) {
                currentBucketValue = module.getBucketValue().configure(new ConfigureBucketValueOptions(
                    options.getFeatureKey(),
                    options.getBucketKey(),
                    options.getContext(),
                    currentBucketValue
                ));
            }
        }
        return currentBucketValue;
    }

    public void closeAll() {
        for (FeaturevisorModule module : new ArrayList<>(modules)) {
            if (clearModuleDiagnosticSubscriptions != null) {
                clearModuleDiagnosticSubscriptions.accept(module);
            }
            closeModule(module);
        }
        modules.clear();
    }

    private void closeModule(FeaturevisorModule module) {
        if (module == null || module.getClose() == null) {
            return;
        }

        try {
            module.getClose().run();
        } catch (Throwable error) {
            report(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.ERROR)
                .code("module_close_error")
                .message("Module close failed")
                .moduleName(module.getName())
                .originalError(String.valueOf(error)), null);
        }
    }

    private void report(FeaturevisorDiagnostic diagnostic, FeaturevisorModule module) {
        if (diagnosticReporter != null) {
            diagnosticReporter.report(diagnostic, module);
        }
    }
}
