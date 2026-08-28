package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.List;

final class ModulesManager {
    private final List<FeaturevisorModule> modules = new ArrayList<>();
    private final FeaturevisorDiagnosticReporter diagnosticReporter;
    private final FeaturevisorModuleApiFactory moduleApiFactory;
    private final java.util.function.Consumer<FeaturevisorModule> clearModuleDiagnosticSubscriptions;

    static class ModulesManagerOptions {
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
                .moduleName(name), null);
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
                    .originalError(error), null);
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
            if (!currentOptions.isGlobalVariable() && module.getBefore() != null) {
                currentOptions = module.getBefore().apply(currentOptions);
            }
        }
        for (FeaturevisorModule module : modules) {
            if (module.getBeforeEvaluation() != null) {
                currentOptions = module.getBeforeEvaluation().apply(currentOptions);
            }
        }
        return currentOptions;
    }

    public Evaluation executeAfterModules(Evaluation evaluation, EvaluateOptions options) {
        Evaluation currentEvaluation = evaluation;
        for (FeaturevisorModule module : modules) {
            if (module.getAfterEvaluation() != null) {
                currentEvaluation = module.getAfterEvaluation().apply(currentEvaluation, options);
            }
        }
        for (FeaturevisorModule module : modules) {
            if (!options.isGlobalVariable() && module.getAfter() != null) {
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
                .originalError(error), null);
        }
    }

    private void report(FeaturevisorDiagnostic diagnostic, FeaturevisorModule module) {
        if (diagnosticReporter != null) {
            diagnosticReporter.report(diagnostic, module);
        }
    }
}
