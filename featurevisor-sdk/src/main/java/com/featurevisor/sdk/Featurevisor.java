package com.featurevisor.sdk;

import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.EvaluatedFeature;
import com.featurevisor.sdk.EvaluatedFeatures;
import com.featurevisor.sdk.VariableType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Main Featurevisor SDK class
 * Provides the primary interface for feature flag evaluation and factory methods
 */
public class Featurevisor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // from options
    private Map<String, Object> context = new HashMap<>();
    private DiagnosticReporter diagnostics;
    private Map<String, Object> stickyFeatures;
    private Map<String, Object> stickyVariables;
    private FeaturevisorDiagnosticHandler onDiagnostic;
    private boolean closed = false;

    // internally created
    private InstanceEvaluationDataProvider evaluationData;
    private ModulesManager modulesManager;
    private Emitter emitter;
    private final List<ModuleDiagnosticSubscription> moduleDiagnosticSubscriptions = new ArrayList<>();

    private static final DatafileContent emptyDatafile;

    static {
        emptyDatafile = new DatafileContent();
        emptyDatafile.setSchemaVersion("2");
        emptyDatafile.setRevision("unknown");
        emptyDatafile.setSegments(new HashMap<>());
        emptyDatafile.setFeatures(new HashMap<>());
    }

    /**
     * Factory methods
     */
    public static Featurevisor createFeaturevisor(FeaturevisorOptions options) {
        if (options == null) {
            options = new FeaturevisorOptions();
        }
        return new Featurevisor(options);
    }

    public static Featurevisor createFeaturevisor() {
        return createFeaturevisor(new FeaturevisorOptions());
    }

    /**
     * Options for creating an instance
     */
    public static class FeaturevisorOptions {
        private DatafileContent datafile;
        private String datafileString;
        private Map<String, Object> context;
        private FeaturevisorLogLevel logLevel;
        private Map<String, Object> stickyFeatures;
        private Map<String, Object> stickyVariables;
        private List<FeaturevisorModule> modules;
        private FeaturevisorDiagnosticHandler onDiagnostic;

        public FeaturevisorOptions() {}

        // Getters
        public DatafileContent getDatafile() { return datafile; }
        public String getDatafileString() { return datafileString; }
        public Map<String, Object> getContext() { return context; }
        public FeaturevisorLogLevel getLogLevel() { return logLevel; }
        public Map<String, Object> getStickyFeatures() { return stickyFeatures; }
        public Map<String, Object> getStickyVariables() { return stickyVariables; }
        @Deprecated public Map<String, Object> getSticky() { return stickyFeatures; }
        public List<FeaturevisorModule> getModules() { return modules; }
        public FeaturevisorDiagnosticHandler getOnDiagnostic() { return onDiagnostic; }

        // Setters
        public void setDatafile(DatafileContent datafile) { this.datafile = datafile; }
        public void setDatafileString(String datafileString) { this.datafileString = datafileString; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setLogLevel(FeaturevisorLogLevel logLevel) { this.logLevel = logLevel; }
        public void setStickyFeatures(Map<String, Object> value) { this.stickyFeatures = value; }
        public void setStickyVariables(Map<String, Object> value) { this.stickyVariables = value; }
        @Deprecated public void setSticky(Map<String, Object> value) { this.stickyFeatures = value; }
        public void setModules(List<FeaturevisorModule> modules) { this.modules = modules; }
        public void setOnDiagnostic(FeaturevisorDiagnosticHandler onDiagnostic) { this.onDiagnostic = onDiagnostic; }

        // Builder pattern methods
        public FeaturevisorOptions datafile(DatafileContent datafile) {
            this.datafile = datafile;
            return this;
        }

        public FeaturevisorOptions datafileString(String datafileString) {
            this.datafileString = datafileString;
            return this;
        }

        public FeaturevisorOptions context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public FeaturevisorOptions logLevel(FeaturevisorLogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public FeaturevisorOptions stickyFeatures(Map<String, Object> value) { this.stickyFeatures = value; return this; }
        public FeaturevisorOptions stickyVariables(Map<String, Object> value) { this.stickyVariables = value; return this; }
        @Deprecated public FeaturevisorOptions sticky(Map<String, Object> value) { return stickyFeatures(value); }

        public FeaturevisorOptions modules(List<FeaturevisorModule> modules) {
            this.modules = modules;
            return this;
        }

        public FeaturevisorOptions onDiagnostic(FeaturevisorDiagnosticHandler onDiagnostic) {
            this.onDiagnostic = onDiagnostic;
            return this;
        }
    }

    private static class ModuleDiagnosticSubscription {
        private final String id;
        private final String moduleId;
        private final FeaturevisorDiagnosticHandler handler;
        private final FeaturevisorLogLevel logLevel;

        ModuleDiagnosticSubscription(String moduleId, FeaturevisorDiagnosticHandler handler, FeaturevisorLogLevel logLevel) {
            this.id = UUID.randomUUID().toString();
            this.moduleId = moduleId;
            this.handler = handler;
            this.logLevel = logLevel != null ? logLevel : FeaturevisorLogLevel.INFO;
        }
    }

    /**
     * Options for overriding evaluation behavior
     */
    public static class OverrideOptions {
        private Map<String, Object> stickyFeatures;
        private Map<String, Object> stickyVariables;
        private String defaultVariationValue;
        private Object defaultVariableValue;
        private boolean defaultVariableValueSet;

        public OverrideOptions() {}

        // Getters
        Map<String, Object> getInternalStickyFeatures() { return stickyFeatures; }
        Map<String, Object> getInternalStickyVariables() { return stickyVariables; }
        public String getDefaultVariationValue() { return defaultVariationValue; }
        public Object getDefaultVariableValue() { return defaultVariableValue; }
        public boolean hasDefaultVariableValue() { return defaultVariableValueSet; }

        // Setters
        void setInternalStickyFeatures(Map<String, Object> value) { this.stickyFeatures = value; }
        void setInternalStickyVariables(Map<String, Object> value) { this.stickyVariables = value; }
        public void setDefaultVariationValue(String defaultVariationValue) { this.defaultVariationValue = defaultVariationValue; }
        public void setDefaultVariableValue(Object defaultVariableValue) {
            this.defaultVariableValue = defaultVariableValue;
            this.defaultVariableValueSet = true;
        }

        // Builder pattern methods
        public OverrideOptions defaultVariationValue(String defaultVariationValue) {
            this.defaultVariationValue = defaultVariationValue;
            return this;
        }

        public OverrideOptions defaultVariableValue(Object defaultVariableValue) {
            this.defaultVariableValue = defaultVariableValue;
            this.defaultVariableValueSet = true;
            return this;
        }
    }

    /** Options used only when spawning a child instance. */
    public static class SpawnOptions {
        private Map<String, Object> stickyFeatures;
        private Map<String, Object> stickyVariables;

        public Map<String, Object> getStickyFeatures() { return stickyFeatures; }
        public Map<String, Object> getStickyVariables() { return stickyVariables; }
        public void setStickyFeatures(Map<String, Object> value) { this.stickyFeatures = value; }
        public void setStickyVariables(Map<String, Object> value) { this.stickyVariables = value; }
        public SpawnOptions stickyFeatures(Map<String, Object> value) { this.stickyFeatures = value; return this; }
        public SpawnOptions stickyVariables(Map<String, Object> value) { this.stickyVariables = value; return this; }
    }

    /**
     * Constructor
     */
    private Featurevisor(FeaturevisorOptions options) {
        // from options
        if (options.getContext() != null) {
            this.context = new HashMap<>(options.getContext());
        }

        this.diagnostics = DiagnosticReporter.createDiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions()
            .level(options.getLogLevel() != null ? options.getLogLevel() : FeaturevisorLogLevel.INFO)
            .handler((level, message, details) -> {
                Map<String, Object> normalizedDetails = details != null ? new HashMap<>(details) : new HashMap<>();
                Object evaluationValue = normalizedDetails.get("evaluation");
                if (evaluationValue instanceof Evaluation) {
                    Evaluation evaluation = (Evaluation) evaluationValue;
                    normalizedDetails = new HashMap<>();
                    normalizedDetails.put("featureKey", evaluation.getFeatureKey());
                    normalizedDetails.put("variableKey", evaluation.getVariableKey());
                    normalizedDetails.put("reason", evaluation.getReason());
                    normalizedDetails.put("evaluation", evaluation);
                }
                Object reason = normalizedDetails.get("reason");
                Object explicitCode = normalizedDetails.remove("code");
                Object originalError = normalizedDetails.remove("originalError");
                String code = explicitCode != null ? explicitCode.toString() : (reason != null ? reason.toString() : message);
                if ("feature is deprecated".equals(message)) code = "deprecated_feature";
                if ("variable is deprecated".equals(message)) code = "deprecated_variable";
                if ("feature not found".equals(message)) code = "feature_not_found";
                if ("variable schema not found".equals(message)) code = "variable_not_found";
                if ("no variations".equals(message)) code = "no_variations";
                if ("invalid bucketBy".equals(message)) code = "invalid_bucket_by";
                if ("Error parsing conditions".equals(message)) code = "conditions_parse_error";
                if ("error during evaluation".equals(message)) code = "evaluation_error";
                reportDiagnostic(new FeaturevisorDiagnostic(level, code, message)
                    .details(normalizedDetails)
                    .originalError(originalError), null);
            }));

        this.emitter = new Emitter();
        this.stickyFeatures = options.getStickyFeatures();
        this.stickyVariables = options.getStickyVariables();
        this.onDiagnostic = options.getOnDiagnostic();

        // datafile
        this.evaluationData = new InstanceEvaluationDataProvider(new InstanceEvaluationDataProvider.InstanceEvaluationDataProviderOptions()
            .datafile(emptyDatafile)
            .diagnostics(this.diagnostics));

        this.modulesManager = new ModulesManager(new ModulesManager.ModulesManagerOptions()
            .modules(options.getModules() != null ? options.getModules() : new ArrayList<>())
            .diagnosticReporter(this::reportDiagnostic)
            .moduleApiFactory(this::createModuleApi)
            .clearModuleDiagnosticSubscriptions(this::clearModuleDiagnosticSubscriptions));

        if (options.getDatafile() != null) {
            setDatafile(options.getDatafile(), true);
        } else if (options.getDatafileString() != null) {
            setDatafile(options.getDatafileString(), true);
        }

        reportDiagnostic(new FeaturevisorDiagnostic()
            .level(FeaturevisorLogLevel.INFO)
            .code("sdk_initialized")
            .message("SDK initialized"), null);
    }

    /**
     * Set log level
     */
    public void setLogLevel(FeaturevisorLogLevel level) {
        this.diagnostics.setLevel(level);
    }

    private FeaturevisorModuleApi createModuleApi(FeaturevisorModule module) {
        return new FeaturevisorModuleApi() {
            @Override
            public String getRevision() {
                return Featurevisor.this.getRevision();
            }

            @Override
            public Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler) {
                return onDiagnostic(handler, new FeaturevisorModuleDiagnosticOptions());
            }

            @Override
            public Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler, FeaturevisorModuleDiagnosticOptions options) {
                if (handler == null) {
                    return () -> {};
                }

                ModuleDiagnosticSubscription subscription = new ModuleDiagnosticSubscription(
                    module.getId(),
                    handler,
                    options != null ? options.getLogLevel() : FeaturevisorLogLevel.INFO
                );
                moduleDiagnosticSubscriptions.add(subscription);

                return () -> moduleDiagnosticSubscriptions.removeIf(item -> item.id.equals(subscription.id));
            }

            @Override
            public void reportDiagnostic(FeaturevisorDiagnostic diagnostic) {
                Featurevisor.this.reportDiagnostic(diagnostic, module);
            }
        };
    }

    private void clearModuleDiagnosticSubscriptions(FeaturevisorModule module) {
        if (module == null) {
            return;
        }
        moduleDiagnosticSubscriptions.removeIf(item -> item.moduleId.equals(module.getId()));
    }

    private boolean shouldReport(FeaturevisorLogLevel diagnosticLevel, FeaturevisorLogLevel subscriptionLevel) {
        return getLogLevelIndex(diagnosticLevel) >= getLogLevelIndex(subscriptionLevel);
    }

    private int getLogLevelIndex(FeaturevisorLogLevel level) {
        if (level == null) {
            level = FeaturevisorLogLevel.INFO;
        }
        switch (level) {
            case DEBUG: return 0;
            case INFO: return 1;
            case WARN: return 2;
            case ERROR: return 3;
            case FATAL: return 4;
            default: return 1;
        }
    }

    private Map<String, Object> diagnosticDetails(FeaturevisorDiagnostic diagnostic) {
        Map<String, Object> details = new HashMap<>();
        details.put("code", diagnostic.getCode());
        if (diagnostic.getModule() != null) {
            details.put("module", diagnostic.getModule());
        }
        if (diagnostic.getModuleName() != null) {
            details.put("moduleName", diagnostic.getModuleName());
        }
        if (diagnostic.getOriginalError() != null) {
            details.put("originalError", diagnostic.getOriginalError());
        }
        if (diagnostic.getDetails() != null) {
            details.putAll(diagnostic.getDetails());
        }
        return details;
    }

    void reportDiagnostic(FeaturevisorDiagnostic diagnostic) {
        reportDiagnostic(diagnostic, null);
    }

    void reportDiagnostic(FeaturevisorDiagnostic diagnostic, FeaturevisorModule sourceModule) {
        if (diagnostic == null) {
            return;
        }
        if (diagnostic.getLevel() == null) {
            diagnostic.setLevel(FeaturevisorLogLevel.INFO);
        }

        if (sourceModule != null && sourceModule.getName() != null) {
            diagnostic.setModule(sourceModule.getName());
        }

        for (ModuleDiagnosticSubscription subscription : new ArrayList<>(moduleDiagnosticSubscriptions)) {
            if (sourceModule != null && sourceModule.getId().equals(subscription.moduleId)) {
                continue;
            }
            if (shouldReport(diagnostic.getLevel(), subscription.logLevel)) {
                try {
                    subscription.handler.handle(diagnostic);
                } catch (Throwable error) {
                    System.err.println("[Featurevisor] Diagnostic handler failed: " + error);
                }
            }
        }

        if (onDiagnostic != null) {
            if (shouldReport(diagnostic.getLevel(), this.diagnostics.getLevel())) {
                try {
                    onDiagnostic.handle(diagnostic);
                } catch (Throwable error) {
                    System.err.println("[Featurevisor] Diagnostic handler failed: " + error);
                }
            }
        } else if (shouldReport(diagnostic.getLevel(), this.diagnostics.getLevel())) {
            DiagnosticReporter.writeToConsole(diagnostic.getLevel(), diagnostic.getMessage(), diagnosticDetails(diagnostic));
        }

        if (FeaturevisorLogLevel.ERROR.equals(diagnostic.getLevel())) {
            this.emitter.trigger(FeaturevisorEventName.ERROR, new FeaturevisorEventDetails(Map.of("diagnostic", diagnostic)));
        }
    }

    /**
     * Set datafile
     */
    public void setDatafile(DatafileContent datafile) {
        setDatafile(datafile, false);
    }

    public void setDatafile(DatafileContent datafile, boolean replace) {
        if (this.closed) {
            return;
        }
        try {
            if (datafile == null) {
                throw new IllegalArgumentException("Datafile must be an object");
            }
            if (datafile.getSchemaVersion() == null || datafile.getRevision() == null ||
                datafile.getSegments() == null || datafile.getFeatures() == null) {
                throw new IllegalArgumentException("Invalid datafile");
            }
            DatafileContent nextDatafile = replace ? datafile : mergeDatafiles(this.evaluationData.getDatafile(), datafile);
            InstanceEvaluationDataProvider newInstanceEvaluationDataProvider = new InstanceEvaluationDataProvider(new InstanceEvaluationDataProvider.InstanceEvaluationDataProviderOptions()
                .datafile(nextDatafile)
                .diagnostics(this.diagnostics));

            FeaturevisorEventDetails details = Events.getParamsForDatafileSetEvent(
                this.evaluationData.getDatafile(), newInstanceEvaluationDataProvider.getDatafile(), replace);

            this.evaluationData = newInstanceEvaluationDataProvider;

            reportDiagnostic(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.INFO)
                .code("datafile_set")
                .message("Datafile set")
                .details(details), null);
            this.emitter.trigger(FeaturevisorEventName.DATAFILE_SET, details);
        } catch (Exception e) {
            reportDiagnostic(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.ERROR)
                .code("invalid_datafile")
                .message("Could not parse datafile")
                .originalError(e), null);
        }
    }

    /**
     * Set datafile from string
     */
    public void setDatafile(String datafileString) {
        setDatafile(datafileString, false);
    }

    public void setDatafile(String datafileString, boolean replace) {
        try {
            DatafileContent datafile = DatafileContent.fromJson(datafileString);
            setDatafile(datafile, replace);
        } catch (Exception e) {
            reportDiagnostic(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.ERROR)
                .code("invalid_datafile")
                .message("Could not parse datafile")
                .originalError(e), null);
        }
    }

    private DatafileContent mergeDatafiles(DatafileContent previous, DatafileContent incoming) {
        if (previous == null) {
            previous = emptyDatafile;
        }
        if (incoming == null) {
            incoming = emptyDatafile;
        }

        DatafileContent merged = new DatafileContent();
        merged.setSchemaVersion(incoming.getSchemaVersion());
        merged.setRevision(incoming.getRevision());
        merged.setFeaturevisorVersion(incoming.getFeaturevisorVersion());

        Map<String, Segment> segments = new HashMap<>();
        if (previous.getSegments() != null) {
            segments.putAll(previous.getSegments());
        }
        if (incoming.getSegments() != null) {
            segments.putAll(incoming.getSegments());
        }
        merged.setSegments(segments);

        Map<String, Feature> features = new HashMap<>();
        if (previous.getFeatures() != null) {
            features.putAll(previous.getFeatures());
        }
        if (incoming.getFeatures() != null) {
            features.putAll(incoming.getFeatures());
        }
        merged.setFeatures(features);

        Map<String, GlobalVariable> variables = new HashMap<>();
        if (previous.getVariables() != null) { variables.putAll(previous.getVariables()); }
        if (incoming.getVariables() != null) { variables.putAll(incoming.getVariables()); }
        merged.setVariables(variables);

        return merged;
    }

    /**
     * Set sticky features
     */
    public void setStickyFeatures(Map<String, Object> sticky) {
        setStickyFeatures(sticky, false);
    }

    public void setStickyFeatures(Map<String, Object> sticky, boolean replace) {
        Map<String, Object> previousStickyFeatures = this.stickyFeatures != null ? new HashMap<>(this.stickyFeatures) : new HashMap<>();

        if (replace) {
            this.stickyFeatures = new HashMap<>(sticky);
        } else {
            this.stickyFeatures = new HashMap<>(this.stickyFeatures != null ? this.stickyFeatures : new HashMap<>());
            this.stickyFeatures.putAll(sticky);
        }

        FeaturevisorEventDetails params = Events.getParamsForStickySetEvent(
            previousStickyFeatures, this.stickyFeatures, replace);

        reportDiagnostic(new FeaturevisorDiagnostic()
            .level(FeaturevisorLogLevel.INFO)
            .code("sticky_features_set")
            .message("Sticky features set")
            .details(params), null);
        this.emitter.trigger(FeaturevisorEventName.STICKY_FEATURES_SET, params);
    }

    public void setStickyVariables(Map<String, Object> sticky, boolean replace) {
        Map<String, Object> previous = this.stickyVariables != null ? new HashMap<>(this.stickyVariables) : new HashMap<>();
        this.stickyVariables = replace ? new HashMap<>(sticky) : new HashMap<>(previous);
        if (!replace) { this.stickyVariables.putAll(sticky); }
        FeaturevisorEventDetails params = Events.getParamsForStickyVariablesSetEvent(previous, this.stickyVariables, replace);
        reportDiagnostic(new FeaturevisorDiagnostic().level(FeaturevisorLogLevel.INFO).code("sticky_variables_set").message("Sticky variables set").details(params), null);
        this.emitter.trigger(FeaturevisorEventName.STICKY_VARIABLES_SET, params);
    }
    public void setStickyVariables(Map<String, Object> sticky) { setStickyVariables(sticky, false); }
    @Deprecated public void setSticky(Map<String, Object> sticky, boolean replace) { setStickyFeatures(sticky, replace); }
    @Deprecated public void setSticky(Map<String, Object> sticky) { setStickyFeatures(sticky, false); }

    /**
     * Get revision
     */
    public String getRevision() {
        return this.evaluationData.getRevision();
    }

    public String getSchemaVersion() {
        return this.evaluationData.getSchemaVersion();
    }

    public Segment getSegment(String segmentKey) {
        return this.evaluationData.getSegment(segmentKey);
    }

    public List<String> getFeatureKeys() {
        return this.evaluationData.getFeatureKeys();
    }

    public List<String> getVariableKeys(String featureKey) {
        return this.evaluationData.getVariableKeys(featureKey);
    }
    public List<String> getVariableKeys() { return this.evaluationData.getGlobalVariableKeys(); }

    public boolean hasVariations(String featureKey) {
        return this.evaluationData.hasVariations(featureKey);
    }

    /**
     * Get feature
     */
    public Feature getFeature(String featureKey) {
        return this.evaluationData.getFeature(featureKey);
    }

    /**
     * Add module
     */
    public Runnable addModule(FeaturevisorModule module) {
        return this.modulesManager.add(module);
    }

    public void removeModule(String name) {
        this.modulesManager.remove(name);
    }

    /**
     * Subscribe to event
     */
    public FeaturevisorUnsubscribe on(FeaturevisorEventName eventName, FeaturevisorEventHandler callback) {
        return this.emitter.on(eventName, callback);
    }

    /**
     * Close instance
     */
    public void close() {
        this.closed = true;
        this.modulesManager.closeAll();
        this.moduleDiagnosticSubscriptions.clear();
        this.emitter.clearAll();
    }

    /**
     * Context
     */
    public void setContext(Map<String, Object> context) {
        setContext(context, false);
    }

    public void setContext(Map<String, Object> context, boolean replace) {
        if (replace) {
            this.context = new HashMap<>(context);
        } else {
            this.context = new HashMap<>(this.context);
            this.context.putAll(context);
        }

        FeaturevisorEventDetails eventDetails = new FeaturevisorEventDetails();
        eventDetails.put("context", this.context);
        eventDetails.put("replaced", replace);

        this.emitter.trigger(FeaturevisorEventName.CONTEXT_SET, eventDetails);
        reportDiagnostic(new FeaturevisorDiagnostic()
            .level(FeaturevisorLogLevel.DEBUG)
            .code("context_set")
            .message(replace ? "Context replaced" : "Context updated")
            .details(eventDetails), null);
    }

    public Map<String, Object> getContext(Map<String, Object> context) {
        if (context != null && !context.isEmpty()) {
            Map<String, Object> mergedContext = new HashMap<>(this.context);
            mergedContext.putAll(context);
            return mergedContext;
        }
        return new HashMap<>(this.context);
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(this.context);
    }

    /**
     * Spawn child instance
     */
    public ChildInstance spawn(Map<String, Object> context, SpawnOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (options == null) {
            options = new SpawnOptions();
        }

        return new ChildInstance(this, getContext(context), options.getStickyFeatures(), options.getStickyVariables());
    }

    public ChildInstance spawn(Map<String, Object> context) {
        return spawn(context, null);
    }

    public ChildInstance spawn() {
        return spawn(null, null);
    }

    /**
     * Flag
     */
    private EvaluateOptions getEvaluationDependencies(Map<String, Object> context, OverrideOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (options == null) {
            options = new OverrideOptions();
        }

        Map<String, Object> mergedStickyFeatures = options.getInternalStickyFeatures() != null ? options.getInternalStickyFeatures() : this.stickyFeatures;
        Map<String, Object> mergedStickyVariables = options.getInternalStickyVariables() != null ? options.getInternalStickyVariables() : this.stickyVariables;

        return new EvaluateOptions()
            .context(getContext(context))
            .diagnostics(this.diagnostics)
            .modulesManager(this.modulesManager)
            .evaluationData(this.evaluationData)
            .stickyFeatures(mergedStickyFeatures)
            .stickyVariables(mergedStickyVariables)
            .defaultVariationValue(options.getDefaultVariationValue())
            .defaultVariableValue(options.getDefaultVariableValue(), options.hasDefaultVariableValue());
    }

    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_FLAG)
            .featureKey(featureKey);

        return Evaluate.evaluateWithModules(evaluateOptions);
    }

    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context) {
        return evaluateFlag(featureKey, context, null);
    }

    public Evaluation evaluateFlag(String featureKey) {
        return evaluateFlag(featureKey, null, null);
    }

    public boolean isEnabled(String featureKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateFlag(featureKey, context, options);
            return Boolean.TRUE.equals(evaluation.getEnabled());
        } catch (Exception e) {
            this.diagnostics.error("isEnabled", Map.of("featureKey", featureKey, "error", e.getMessage()));
            return false;
        }
    }

    public boolean isEnabled(String featureKey, Map<String, Object> context) {
        return isEnabled(featureKey, context, null);
    }

    public boolean isEnabled(String featureKey) {
        return isEnabled(featureKey, null, null);
    }

    /**
     * Variation
     */
    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_VARIATION)
            .featureKey(featureKey);

        return Evaluate.evaluateWithModules(evaluateOptions);
    }

    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context) {
        return evaluateVariation(featureKey, context, null);
    }

    public Evaluation evaluateVariation(String featureKey) {
        return evaluateVariation(featureKey, null, null);
    }

    public String getVariation(String featureKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateVariation(featureKey, context, options);

            if (evaluation.getVariationValue() != null) {
                return evaluation.getVariationValue();
            }

            if (evaluation.getVariation() != null) {
                return evaluation.getVariation().getValue();
            }

            return null;
        } catch (Exception e) {
            this.diagnostics.error("getVariation", Map.of("featureKey", featureKey, "error", e.getMessage()));
            return null;
        }
    }

    public String getVariation(String featureKey, Map<String, Object> context) {
        return getVariation(featureKey, context, null);
    }

    public String getVariation(String featureKey) {
        return getVariation(featureKey, null, null);
    }

    /**
     * Variable
     */
    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey(featureKey)
            .variableKey(variableKey);

        return Evaluate.evaluateWithModules(evaluateOptions);
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return evaluateVariable(featureKey, variableKey, context, null);
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey) {
        return evaluateVariable(featureKey, variableKey, null, null);
    }

    public Evaluation evaluateVariable(String variableKey, Map<String, Object> context, OverrideOptions options) {
        return Evaluate.evaluateWithModules(getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_VARIABLE).featureKey(null).variableKey(variableKey).globalVariable(true));
    }
    public Evaluation evaluateVariable(String variableKey, Map<String, Object> context) { return evaluateVariable(variableKey, context, null); }
    public Evaluation evaluateVariable(String variableKey) { return evaluateVariable(variableKey, (Map<String, Object>) null, null); }

    public Object getVariable(String variableKey, Map<String, Object> context, OverrideOptions options) {
        Evaluation evaluation = evaluateVariable(variableKey, context, options);
        Object value = evaluation.getVariableValue();
        if (value == null && options != null && options.hasDefaultVariableValue()) value = options.getDefaultVariableValue();
        if (value instanceof String && evaluation.getVariable() != null && VariableType.JSON == evaluation.getVariable().getType()) {
            try { return OBJECT_MAPPER.readValue((String) value, Object.class); } catch (Exception ignored) { return null; }
        }
        return value;
    }
    public Object getVariable(String variableKey, Map<String, Object> context) { return getVariable(variableKey, context, null); }
    public Object getVariable(String variableKey) { return getVariable(variableKey, (Map<String, Object>) null, null); }
    public Boolean getVariableBoolean(String key, Map<String, Object> context, OverrideOptions options) { return Helpers.getValueByType(getVariable(key, context, options), "boolean"); }
    public Boolean getVariableBoolean(String key) { return getVariableBoolean(key, (Map<String, Object>) null, null); }
    public String getVariableString(String key, Map<String, Object> context, OverrideOptions options) { return Helpers.getValueByType(getVariable(key, context, options), "string"); }
    public String getVariableString(String key) { return getVariableString(key, (Map<String, Object>) null, null); }
    public Integer getVariableInteger(String key, Map<String, Object> context, OverrideOptions options) { return (Integer) Helpers.getValueByType(getVariable(key, context, options), "integer"); }
    public Integer getVariableInteger(String key) { return getVariableInteger(key, (Map<String, Object>) null, null); }
    public Double getVariableDouble(String key, Map<String, Object> context, OverrideOptions options) { return (Double) Helpers.getValueByType(getVariable(key, context, options), "double"); }
    public Double getVariableDouble(String key) { return getVariableDouble(key, (Map<String, Object>) null, null); }
    @SuppressWarnings("unchecked") public <T> List<T> getVariableArray(String key, Map<String, Object> context, OverrideOptions options) { return Helpers.getValueByType(getVariable(key, context, options), "array"); }
    public <T> List<T> getVariableArray(String key) { return getVariableArray(key, (Map<String, Object>) null, (OverrideOptions) null); }
    @SuppressWarnings("unchecked") public <T> T getVariableObject(String key, Map<String, Object> context, OverrideOptions options) { return Helpers.getValueByType(getVariable(key, context, options), "object"); }
    public <T> T getVariableObject(String key) { return getVariableObject(key, (Map<String, Object>) null, (OverrideOptions) null); }
    @SuppressWarnings("unchecked") public <T> T getVariableJSON(String key, Map<String, Object> context, OverrideOptions options) { return Helpers.getValueByType(getVariable(key, context, options), "json"); }
    public <T> T getVariableJSON(String key) { return getVariableJSON(key, (Map<String, Object>) null, (OverrideOptions) null); }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateVariable(featureKey, variableKey, context, options);

            if (evaluation.getVariableValue() != null) {
                Object value = evaluation.getVariableValue();
                if (value instanceof String) {
                    String strValue = (String) value;
                    boolean isJsonType = evaluation.getVariableSchema() != null &&
                                         evaluation.getVariableSchema().getType() == VariableType.JSON;
                    if (isJsonType) {
                        try {
                            return OBJECT_MAPPER.readValue(strValue, Object.class);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
                return value;
            }

            return null;
        } catch (Exception e) {
            this.diagnostics.error("getVariable", Map.of("featureKey", featureKey, "variableKey", variableKey, "error", e.getMessage()));
            return null;
        }
    }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariable(featureKey, variableKey, context, null);
    }

    public Object getVariable(String featureKey, String variableKey) {
        return getVariable(featureKey, variableKey, null, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "boolean");
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableBoolean(featureKey, variableKey, context, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey) {
        return getVariableBoolean(featureKey, variableKey, null, null);
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "string");
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableString(featureKey, variableKey, context, null);
    }

    public String getVariableString(String featureKey, String variableKey) {
        return getVariableString(featureKey, variableKey, null, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return (Integer) Helpers.getValueByType(variableValue, "integer");
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableInteger(featureKey, variableKey, context, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey) {
        return getVariableInteger(featureKey, variableKey, null, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return (Double) Helpers.getValueByType(variableValue, "double");
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableDouble(featureKey, variableKey, context, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey) {
        return getVariableDouble(featureKey, variableKey, null, null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "array");
    }

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableArray(featureKey, variableKey, context, (OverrideOptions) null);
    }

    public List<String> getVariableArray(String featureKey, String variableKey) {
        return getVariableArray(featureKey, variableKey, null, (OverrideOptions) null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "object");
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableObject(featureKey, variableKey, context, (OverrideOptions) null);
    }

    public <T> T getVariableObject(String featureKey, String variableKey) {
        return getVariableObject(featureKey, variableKey, null, (OverrideOptions) null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "json");
    }

    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableJSON(featureKey, variableKey, context, null);
    }

    public <T> T getVariableJSON(String featureKey, String variableKey) {
        return getVariableJSON(featureKey, variableKey, null, null);
    }

    public JsonNode getVariableJSONNode(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        if (variableValue == null) {
            return null;
        }

        if (variableValue instanceof JsonNode) {
            return (JsonNode) variableValue;
        }

        return OBJECT_MAPPER.valueToTree(variableValue);
    }

    public JsonNode getVariableJSONNode(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableJSONNode(featureKey, variableKey, context, null);
    }

    public JsonNode getVariableJSONNode(String featureKey, String variableKey) {
        return getVariableJSONNode(featureKey, variableKey, null, null);
    }

    public <T> List<T> getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        OverrideOptions options,
        Class<T> itemType
    ) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        Object arrayValue = Helpers.getValueByType(variableValue, "array");
        if (arrayValue == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.convertValue(
                arrayValue,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, itemType)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> List<T> getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Class<T> itemType
    ) {
        return getVariableArray(featureKey, variableKey, context, null, itemType);
    }

    public <T> List<T> getVariableArray(String featureKey, String variableKey, Class<T> itemType) {
        return getVariableArray(featureKey, variableKey, null, null, itemType);
    }

    public <T> T getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        OverrideOptions options,
        TypeReference<T> typeRef
    ) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        Object arrayValue = Helpers.getValueByType(variableValue, "array");
        if (arrayValue == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.convertValue(arrayValue, typeRef);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        TypeReference<T> typeRef
    ) {
        return getVariableArray(featureKey, variableKey, context, null, typeRef);
    }

    public <T> T getVariableArray(String featureKey, String variableKey, TypeReference<T> typeRef) {
        return getVariableArray(featureKey, variableKey, null, null, typeRef);
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        OverrideOptions options,
        Class<T> type
    ) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        Object objectValue = Helpers.getValueByType(variableValue, "object");
        if (objectValue == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.convertValue(objectValue, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Class<T> type
    ) {
        return getVariableObject(featureKey, variableKey, context, null, type);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Class<T> type) {
        return getVariableObject(featureKey, variableKey, null, null, type);
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        OverrideOptions options,
        TypeReference<T> typeRef
    ) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        Object objectValue = Helpers.getValueByType(variableValue, "object");
        if (objectValue == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.convertValue(objectValue, typeRef);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        TypeReference<T> typeRef
    ) {
        return getVariableObject(featureKey, variableKey, context, null, typeRef);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, TypeReference<T> typeRef) {
        return getVariableObject(featureKey, variableKey, null, null, typeRef);
    }

    /**
     * Get all evaluations
     */
    public EvaluatedFeatures getFeatureEvaluations(Map<String, Object> context, List<String> featureKeys, OverrideOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (featureKeys == null) {
            featureKeys = new ArrayList<>();
        }
        if (options == null) {
            options = new OverrideOptions();
        }

        Map<String, EvaluatedFeature> result = new HashMap<>();

        List<String> keys = featureKeys.isEmpty() ? this.evaluationData.getFeatureKeys() : featureKeys;
        for (String featureKey : keys) {
            // isEnabled
            Evaluation flagEvaluation = evaluateFlag(featureKey, context, options);

            EvaluatedFeature evaluatedFeature = new EvaluatedFeature();
            evaluatedFeature.setEnabled(Boolean.TRUE.equals(flagEvaluation.getEnabled()));

            OverrideOptions opts = new OverrideOptions()
                .defaultVariationValue(options.getDefaultVariationValue());
            if (options.hasDefaultVariableValue()) {
                opts.defaultVariableValue(options.getDefaultVariableValue());
            }
            opts.setInternalStickyFeatures(options.getInternalStickyFeatures());
            opts.setInternalStickyVariables(options.getInternalStickyVariables());

            // variation
            if (this.evaluationData.hasVariations(featureKey)) {
                Object variation = getVariation(featureKey, context, opts);
                if (variation != null) {
                    evaluatedFeature.setVariation(variation.toString());
                }
            }

            // variables
            List<String> variableKeys = this.evaluationData.getVariableKeys(featureKey);
            if (!variableKeys.isEmpty()) {
                Map<String, Object> variables = new HashMap<>();

                for (String variableKey : variableKeys) {
                    variables.put(variableKey, getVariable(featureKey, variableKey, context, opts));
                }

                evaluatedFeature.setVariables(variables);
            }

            result.put(featureKey, evaluatedFeature);
        }

        return EvaluatedFeatures.of(result);
    }

    public EvaluatedFeatures getFeatureEvaluations(Map<String, Object> context, List<String> featureKeys) {
        return getFeatureEvaluations(context, featureKeys, null);
    }

    public EvaluatedFeatures getFeatureEvaluations(Map<String, Object> context) {
        return getFeatureEvaluations(context, null, null);
    }

    public EvaluatedFeatures getFeatureEvaluations() {
        return getFeatureEvaluations(null, null, null);
    }

    public Map<String, Object> getVariableEvaluations(Map<String, Object> context, List<String> variableKeys, OverrideOptions options) {
        Map<String, Object> result = new HashMap<>();
        List<String> keys = variableKeys == null || variableKeys.isEmpty() ? evaluationData.getGlobalVariableKeys() : variableKeys;
        for (String key : keys) result.put(key, getVariable(key, context, options));
        return result;
    }
    public Map<String, Object> getVariableEvaluations() { return getVariableEvaluations(null, null, null); }
    @Deprecated public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> keys, OverrideOptions options) { return getFeatureEvaluations(context, keys, options); }
    @Deprecated public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> keys) { return getFeatureEvaluations(context, keys, null); }
    @Deprecated public EvaluatedFeatures getAllEvaluations(Map<String, Object> context) { return getFeatureEvaluations(context, null, null); }
    @Deprecated public EvaluatedFeatures getAllEvaluations() { return getFeatureEvaluations(); }
}
