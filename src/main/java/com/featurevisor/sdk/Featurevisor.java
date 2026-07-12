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
    private Logger logger;
    private Map<String, Object> sticky;
    private FeaturevisorDiagnosticHandler onDiagnostic;
    private boolean closed = false;

    // internally created
    private DatafileReader datafileReader;
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
        private Map<String, Object> sticky;
        private List<FeaturevisorModule> modules;
        private FeaturevisorDiagnosticHandler onDiagnostic;

        public FeaturevisorOptions() {}

        // Getters
        public DatafileContent getDatafile() { return datafile; }
        public String getDatafileString() { return datafileString; }
        public Map<String, Object> getContext() { return context; }
        public FeaturevisorLogLevel getLogLevel() { return logLevel; }
        public Map<String, Object> getSticky() { return sticky; }
        public List<FeaturevisorModule> getModules() { return modules; }
        public FeaturevisorDiagnosticHandler getOnDiagnostic() { return onDiagnostic; }

        // Setters
        public void setDatafile(DatafileContent datafile) { this.datafile = datafile; }
        public void setDatafileString(String datafileString) { this.datafileString = datafileString; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setLogLevel(FeaturevisorLogLevel logLevel) { this.logLevel = logLevel; }
        public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
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

        public FeaturevisorOptions sticky(Map<String, Object> sticky) {
            this.sticky = sticky;
            return this;
        }

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
        private Map<String, Object> sticky;
        private String defaultVariationValue;
        private Object defaultVariableValue;
        private Evaluation flagEvaluation;

        public OverrideOptions() {}

        // Getters
        Map<String, Object> getInternalSticky() { return sticky; }
        public String getDefaultVariationValue() { return defaultVariationValue; }
        public Object getDefaultVariableValue() { return defaultVariableValue; }
        public Evaluation getFlagEvaluation() { return flagEvaluation; }

        // Setters
        void setInternalSticky(Map<String, Object> sticky) { this.sticky = sticky; }
        public void setDefaultVariationValue(String defaultVariationValue) { this.defaultVariationValue = defaultVariationValue; }
        public void setDefaultVariableValue(Object defaultVariableValue) { this.defaultVariableValue = defaultVariableValue; }
        public void setFlagEvaluation(Evaluation flagEvaluation) { this.flagEvaluation = flagEvaluation; }

        // Builder pattern methods
        public OverrideOptions defaultVariationValue(String defaultVariationValue) {
            this.defaultVariationValue = defaultVariationValue;
            return this;
        }

        public OverrideOptions defaultVariableValue(Object defaultVariableValue) {
            this.defaultVariableValue = defaultVariableValue;
            return this;
        }

        public OverrideOptions flagEvaluation(Evaluation flagEvaluation) {
            this.flagEvaluation = flagEvaluation;
            return this;
        }
    }

    /** Options used only when spawning a child instance. */
    public static class SpawnOptions {
        private Map<String, Object> sticky;

        public Map<String, Object> getSticky() { return sticky; }
        public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
        public SpawnOptions sticky(Map<String, Object> sticky) {
            this.sticky = sticky;
            return this;
        }
    }

    /**
     * Constructor
     */
    private Featurevisor(FeaturevisorOptions options) {
        // from options
        if (options.getContext() != null) {
            this.context = new HashMap<>(options.getContext());
        }

        this.logger = Logger.createLogger(new Logger.CreateLoggerOptions()
            .level(options.getLogLevel() != null ? options.getLogLevel() : FeaturevisorLogLevel.INFO)
            .handler((level, message, details) -> {
                Map<String, Object> normalizedDetails = details != null ? details : new HashMap<>();
                Object reason = normalizedDetails.get("reason");
                String code = reason != null ? reason.toString() : message;
                if ("feature is deprecated".equals(message)) code = "deprecated_feature";
                if ("variable is deprecated".equals(message)) code = "deprecated_variable";
                if ("feature not found".equals(message)) code = "feature_not_found";
                if ("variable schema not found".equals(message)) code = "variable_not_found";
                if ("no variations".equals(message)) code = "no_variations";
                if ("invalid bucketBy".equals(message)) code = "invalid_bucket_by";
                reportDiagnostic(new FeaturevisorDiagnostic(level, code, message).details(normalizedDetails), null);
            }));

        this.emitter = new Emitter();
        this.sticky = options.getSticky();
        this.onDiagnostic = options.getOnDiagnostic();

        // datafile
        this.datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(emptyDatafile)
            .logger(this.logger));

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
        this.logger.setLevel(level);
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

        if (sourceModule != null && diagnostic.getModule() == null) {
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
            if (shouldReport(diagnostic.getLevel(), this.logger.getLevel())) {
                try {
                    onDiagnostic.handle(diagnostic);
                } catch (Throwable error) {
                    System.err.println("[Featurevisor] Diagnostic handler failed: " + error);
                }
            }
        } else if (shouldReport(diagnostic.getLevel(), this.logger.getLevel())) {
            Logger.writeToConsole(diagnostic.getLevel(), diagnostic.getMessage(), diagnosticDetails(diagnostic));
        }

        if (FeaturevisorLogLevel.ERROR.equals(diagnostic.getLevel())) {
            this.emitter.trigger(Emitter.EventName.ERROR, new Emitter.EventDetails(Map.of("diagnostic", diagnostic)));
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
            DatafileContent nextDatafile = replace ? datafile : mergeDatafiles(this.datafileReader.getDatafile(), datafile);
            DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
                .datafile(nextDatafile)
                .logger(this.logger));

            Emitter.EventDetails details = Events.getParamsForDatafileSetEvent(
                this.datafileReader.getDatafile(), newDatafileReader.getDatafile(), replace);

            this.datafileReader = newDatafileReader;

            reportDiagnostic(new FeaturevisorDiagnostic()
                .level(FeaturevisorLogLevel.INFO)
                .code("datafile_set")
                .message("Datafile set")
                .details(details), null);
            this.emitter.trigger(Emitter.EventName.DATAFILE_SET, details);
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

        return merged;
    }

    /**
     * Set sticky features
     */
    public void setSticky(Map<String, Object> sticky, boolean replace) {
        Map<String, Object> previousStickyFeatures = this.sticky != null ?
            new HashMap<>(this.sticky) : new HashMap<>();

        if (replace) {
            this.sticky = new HashMap<>(sticky);
        } else {
            this.sticky = new HashMap<>(this.sticky != null ? this.sticky : new HashMap<>());
            this.sticky.putAll(sticky);
        }

        Emitter.EventDetails params = Events.getParamsForStickySetEvent(
            previousStickyFeatures, this.sticky, replace);

        reportDiagnostic(new FeaturevisorDiagnostic()
            .level(FeaturevisorLogLevel.INFO)
            .code("sticky_set")
            .message("Sticky features set")
            .details(params), null);
        this.emitter.trigger(Emitter.EventName.STICKY_SET, params);
    }

    /**
     * Get revision
     */
    public String getRevision() {
        return this.datafileReader.getRevision();
    }

    public String getSchemaVersion() {
        return this.datafileReader.getSchemaVersion();
    }

    public Segment getSegment(String segmentKey) {
        return this.datafileReader.getSegment(segmentKey);
    }

    public List<String> getFeatureKeys() {
        return this.datafileReader.getFeatureKeys();
    }

    public List<String> getVariableKeys(String featureKey) {
        return this.datafileReader.getVariableKeys(featureKey);
    }

    public boolean hasVariations(String featureKey) {
        return this.datafileReader.hasVariations(featureKey);
    }

    /**
     * Get feature
     */
    public Feature getFeature(String featureKey) {
        return this.datafileReader.getFeature(featureKey);
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
    public Emitter.UnsubscribeFunction on(Emitter.EventName eventName, Emitter.EventCallback callback) {
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
    public void setContext(Map<String, Object> context, boolean replace) {
        if (replace) {
            this.context = new HashMap<>(context);
        } else {
            this.context = new HashMap<>(this.context);
            this.context.putAll(context);
        }

        Emitter.EventDetails eventDetails = new Emitter.EventDetails();
        eventDetails.put("context", this.context);
        eventDetails.put("replaced", replace);

        this.emitter.trigger(Emitter.EventName.CONTEXT_SET, eventDetails);
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

        return new ChildInstance(this, getContext(context), options.getSticky());
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

        Map<String, Object> mergedSticky = options.getInternalSticky() != null ? options.getInternalSticky() : this.sticky;

        return new EvaluateOptions()
            .context(getContext(context))
            .logger(this.logger)
            .modulesManager(this.modulesManager)
            .datafileReader(this.datafileReader)
            .sticky(mergedSticky)
            .defaultVariationValue(options.getDefaultVariationValue())
            .defaultVariableValue(options.getDefaultVariableValue())
            .flagEvaluation(options.getFlagEvaluation());
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
            this.logger.error("isEnabled", Map.of("featureKey", featureKey, "error", e.getMessage()));
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
            this.logger.error("getVariation", Map.of("featureKey", featureKey, "error", e.getMessage()));
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
            this.logger.error("getVariable", Map.of("featureKey", featureKey, "variableKey", variableKey, "error", e.getMessage()));
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
    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys, OverrideOptions options) {
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

        List<String> keys = featureKeys.isEmpty() ? this.datafileReader.getFeatureKeys() : featureKeys;
        for (String featureKey : keys) {
            // isEnabled
            Evaluation flagEvaluation = evaluateFlag(featureKey, context, options);

            EvaluatedFeature evaluatedFeature = new EvaluatedFeature();
            evaluatedFeature.setEnabled(Boolean.TRUE.equals(flagEvaluation.getEnabled()));

            OverrideOptions opts = new OverrideOptions()
                .defaultVariationValue(options.getDefaultVariationValue())
                .defaultVariableValue(options.getDefaultVariableValue())
                .flagEvaluation(flagEvaluation);
            opts.setInternalSticky(options.getInternalSticky());

            // variation
            if (this.datafileReader.hasVariations(featureKey)) {
                Object variation = getVariation(featureKey, context, opts);
                if (variation != null) {
                    evaluatedFeature.setVariation(variation.toString());
                }
            }

            // variables
            List<String> variableKeys = this.datafileReader.getVariableKeys(featureKey);
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

    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys) {
        return getAllEvaluations(context, featureKeys, null);
    }

    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context) {
        return getAllEvaluations(context, null, null);
    }

    public EvaluatedFeatures getAllEvaluations() {
        return getAllEvaluations(null, null, null);
    }
}
