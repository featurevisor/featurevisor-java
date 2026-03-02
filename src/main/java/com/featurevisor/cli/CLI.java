package com.featurevisor.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.Logger;
import com.featurevisor.sdk.DatafileReader;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Segment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import com.featurevisor.sdk.HooksManager;

/**
 * Command Line Interface for Featurevisor Java Library
 *
 * This class provides CLI access to the Featurevisor functions.
 * It is only used when running the application from command line.
 */
@Command(
    name = "featurevisor",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Featurevisor Java Library CLI - Test runner, benchmark, and distribution assessment"
)
public class CLI implements Runnable {

    @Parameters(
        index = "0",
        description = "Command to execute: test, benchmark, or assess-distribution"
    )
    private String command;

    @Option(names = {"--assertionPattern"}, description = "Assertion pattern filter")
    private String assertionPattern;

    @Option(names = {"--context"}, description = "Context as JSON string")
    private String context;

    @Option(names = {"--environment"}, description = "Environment name")
    private String environment;

    @Option(names = {"--feature"}, description = "Feature key")
    private String feature;

    @Option(names = {"--keyPattern"}, description = "Key pattern filter")
    private String keyPattern;

    @Option(names = {"-n"}, description = "Number of iterations")
    private Integer n = 1000;

    @Option(names = {"--onlyFailures"}, description = "Show only failures")
    private Boolean onlyFailures = false;

    @Option(names = {"--quiet"}, description = "Quiet mode")
    private Boolean quiet = false;

    @Option(names = {"--variable"}, description = "Variable key")
    private String variable;

    @Option(names = {"--variation"}, description = "Variation flag")
    private Boolean variation = false;

    @Option(names = {"--verbose"}, description = "Verbose mode")
    private Boolean verbose = false;

    @Option(names = {"--inflate"}, description = "Inflate number")
    private Integer inflate = 0;

    @Option(names = {"--with-scopes"}, description = "Test with scoped datafiles")
    private Boolean withScopes = false;

    @Option(names = {"--with-tags"}, description = "Test with tagged datafiles")
    private Boolean withTags = false;

    @Option(names = {"--showDatafile"}, description = "Show datafile content for assertion")
    private Boolean showDatafile = false;

    @Option(names = {"--schemaVersion"}, description = "Datafile schema version")
    private String schemaVersion;

    @Option(names = {"--rootDirectoryPath"}, description = "Root directory path")
    private String rootDirectoryPath;

    @Option(names = {"--projectDirectoryPath"}, description = "Project directory path")
    private String projectDirectoryPath;

    @Option(names = {"--populateUuid"}, description = "Populate UUID for specified keys")
    private List<String> populateUuid = new ArrayList<>();

    private String cwd;
    private ObjectMapper objectMapper;

    public CLI() {
        this.cwd = System.getProperty("user.dir");
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle null values properly
        this.objectMapper.setDefaultSetterInfo(com.fasterxml.jackson.annotation.JsonSetter.Value.forValueNulls(com.fasterxml.jackson.annotation.Nulls.AS_EMPTY));
    }

    @Override
    public void run() {
        if (rootDirectoryPath == null && projectDirectoryPath != null) {
            rootDirectoryPath = projectDirectoryPath;
        }
        if (rootDirectoryPath == null) {
            rootDirectoryPath = cwd;
        }

        switch (command) {
            case "test":
                test();
                break;
            case "benchmark":
                benchmark();
                break;
            case "assess-distribution":
                assessDistribution();
                break;
            default:
                System.out.println("Learn more at https://featurevisor.com/docs/sdks/java/");
                break;
        }
    }

    /**
     * Execute a command and return the output
     */
    private String executeCommand(String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(org.apache.commons.exec.CommandLine.parse(command));
            return outputStream.toString();
        } catch (ExecuteException e) {
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        }
    }

    private String executeCommandInDirectory(String directory, String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWorkingDirectory(new java.io.File(directory));

        try {
            executor.execute(org.apache.commons.exec.CommandLine.parse(command));
            return outputStream.toString();
        } catch (ExecuteException e) {
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get config from featurevisor project
     */
    private Map<String, Object> getConfig(String featurevisorProjectPath) throws IOException {
        System.out.println("Getting config...");
        String configOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor config --json");
        return objectMapper.readValue(configOutput, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Get segments from featurevisor project
     */
    private Map<String, Segment> getSegments(String featurevisorProjectPath) throws IOException {
        System.out.println("Getting segments...");
        String segmentsOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor list --segments --json");
        List<Segment> segments = objectMapper.readValue(segmentsOutput, new TypeReference<List<Segment>>() {});
        Map<String, Segment> segmentsByKey = new HashMap<>();
        for (Segment segment : segments) {
            segmentsByKey.put(segment.getKey(), segment);
        }
        return segmentsByKey;
    }

    private String getEnvironmentKey(String environment) {
        return environment == null ? "__no_environment__" : environment;
    }

    private String scopedDatafileCacheKey(String environment, String scope) {
        return getEnvironmentKey(environment) + "-scope-" + scope;
    }

    private String taggedDatafileCacheKey(String environment, String tag) {
        return getEnvironmentKey(environment) + "-tag-" + tag;
    }

    private DatafileContent parseDatafileContent(String datafileOutput, String contextForError) throws IOException {
        try {
            return DatafileContent.fromJson(datafileOutput);
        } catch (Exception e) {
            throw new IOException("Failed to parse datafile for " + contextForError + ": " + e.getMessage(), e);
        }
    }

    private DatafileContent buildDatafile(
        String featurevisorProjectPath,
        String environment,
        String tag
    ) throws IOException {
        StringBuilder command = new StringBuilder("npx featurevisor build --json");
        if (environment != null) {
            command.append(" --environment=").append(environment);
        }
        if (tag != null) {
            command.append(" --tag=").append(tag);
        }
        if (schemaVersion != null && !schemaVersion.isBlank()) {
            command.append(" --schema-version=").append(schemaVersion);
        }
        if (inflate != null && inflate > 0) {
            command.append(" --inflate=").append(inflate);
        }

        String datafileOutput = executeCommandInDirectory(featurevisorProjectPath, command.toString());
        return parseDatafileContent(datafileOutput, command.toString());
    }

    private Map<String, DatafileContent> buildBaseDatafiles(
        String featurevisorProjectPath,
        List<String> environments
    ) throws IOException {
        Map<String, DatafileContent> datafilesByEnvironment = new HashMap<>();
        for (String env : environments) {
            System.out.println("Building datafile for environment: " + (env == null ? "default" : env) + "...");
            datafilesByEnvironment.put(getEnvironmentKey(env), buildDatafile(featurevisorProjectPath, env, null));
        }
        return datafilesByEnvironment;
    }

    private List<String> getEnvironmentList(Map<String, Object> config) {
        Object environmentsValue = config.get("environments");
        if (Boolean.FALSE.equals(environmentsValue)) {
            return Collections.singletonList(null);
        }

        if (environmentsValue instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> environments = (List<String>) environmentsValue;
            if (!environments.isEmpty()) {
                return environments;
            }
        }

        return Collections.singletonList(null);
    }

    private List<String> getTags(Map<String, Object> config) {
        Object tagsValue = config.get("tags");
        if (!(tagsValue instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Object> rawTags = (List<Object>) tagsValue;
        List<String> tags = new ArrayList<>();
        for (Object tag : rawTags) {
            if (tag instanceof String) {
                tags.add((String) tag);
            }
        }
        return tags;
    }

    private Map<String, Map<String, Object>> getScopesByName(Map<String, Object> config) {
        Object scopesValue = config.get("scopes");
        if (!(scopesValue instanceof List)) {
            return Collections.emptyMap();
        }

        @SuppressWarnings("unchecked")
        List<Object> scopes = (List<Object>) scopesValue;
        Map<String, Map<String, Object>> scopesByName = new HashMap<>();

        for (Object scopeObj : scopes) {
            if (!(scopeObj instanceof Map)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> scope = (Map<String, Object>) scopeObj;
            Object name = scope.get("name");
            if (name instanceof String && !((String) name).isBlank()) {
                scopesByName.put((String) name, scope);
            }
        }

        return scopesByName;
    }

    private Path getScopedDatafilePath(String featurevisorProjectPath, Map<String, Object> config, String environment, String scopeName) {
        String datafilesDirectory = "datafiles";
        Object configuredDir = config.get("datafilesDirectoryPath");
        if (configuredDir instanceof String && !((String) configuredDir).isBlank()) {
            datafilesDirectory = (String) configuredDir;
        }

        if (environment != null) {
            return Paths.get(featurevisorProjectPath, datafilesDirectory, environment, "featurevisor-scope-" + scopeName + ".json");
        }

        return Paths.get(featurevisorProjectPath, datafilesDirectory, "featurevisor-scope-" + scopeName + ".json");
    }

    /**
     * Get logger level based on CLI options
     */
    private Logger.LogLevel getLoggerLevel() {
        if (verbose) {
            return Logger.LogLevel.DEBUG;
        } else if (quiet) {
            return Logger.LogLevel.ERROR;
        } else {
            return Logger.LogLevel.WARN;
        }
    }

    /**
     * Generate UUID
     */
    private String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get tests from featurevisor project
     */
    private List<Map<String, Object>> getTests(String featurevisorProjectPath) throws IOException {
        StringBuilder testsSuffix = new StringBuilder();
        if (keyPattern != null) {
            testsSuffix.append(" --keyPattern=").append(keyPattern);
        }
        if (assertionPattern != null) {
            testsSuffix.append(" --assertionPattern=").append(assertionPattern);
        }

        String testsOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor list --tests --applyMatrix --json" + testsSuffix);
        return objectMapper.readValue(testsOutput, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Test a feature
     */
    private TestResult testFeature(Map<String, Object> assertion, String featureKey, Object f, Logger.LogLevel level) {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> sticky = (Map<String, Object>) assertion.getOrDefault("sticky", new HashMap<>());

        // Update the SDK instance context and sticky values for this assertion
        if (f instanceof Featurevisor) {
            ((Featurevisor) f).setContext(context, true);
            ((Featurevisor) f).setSticky(sticky, true);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            ((com.featurevisor.sdk.ChildInstance) f).setContext(context, true);
            ((com.featurevisor.sdk.ChildInstance) f).setSticky(sticky, true);
        }

        boolean hasError = false;
        StringBuilder errors = new StringBuilder();
        long startTime = System.nanoTime();

        // Test expectedToBeEnabled
        if (assertion.containsKey("expectedToBeEnabled")) {
            boolean isEnabled = isEnabled(f, featureKey, context);
            boolean expected = (Boolean) assertion.get("expectedToBeEnabled");
            if (isEnabled != expected) {
                hasError = true;
                errors.append("      ✘ expectedToBeEnabled: expected ").append(expected).append(" but received ").append(isEnabled).append("\n");
            }
        }

        // Test expectedVariation
        if (assertion.containsKey("expectedVariation")) {
            Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
            if (assertion.containsKey("defaultVariationValue")) {
                options.setDefaultVariationValue(assertion.get("defaultVariationValue").toString());
            }

            Object variation = getVariation(f, featureKey, context, options);
            Object expected = assertion.get("expectedVariation");
            if (!Objects.equals(variation, expected)) {
                hasError = true;
                errors.append("      ✘ expectedVariation: expected ").append(expected).append(" but received ").append(variation).append("\n");
            }
        }

        // Test expectedVariables
        if (assertion.containsKey("expectedVariables")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedVariables = (Map<String, Object>) assertion.get("expectedVariables");
            @SuppressWarnings("unchecked")
            Map<String, Object> defaultVariableValues = (Map<String, Object>) assertion.getOrDefault("defaultVariableValues", new HashMap<>());

            for (String variableKey : expectedVariables.keySet()) {
                Object expectedValue = expectedVariables.get(variableKey);
                if (expectedValue instanceof String) {
                    String strValue = (String) expectedValue;
                    if ((strValue.startsWith("{") && strValue.endsWith("}")) ||
                        (strValue.startsWith("[") && strValue.endsWith("]"))) {
                        try {
                            expectedValue = objectMapper.readValue(strValue, Object.class);
                        } catch (Exception e) {
                            // Keep as string if parsing fails
                        }
                    }
                }

                Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
                if (defaultVariableValues.containsKey(variableKey)) {
                    options.setDefaultVariableValue(defaultVariableValues.get(variableKey));
                }

                Object actualValue = getVariable(f, featureKey, variableKey, context, options);
                if (!Objects.equals(actualValue, expectedValue)) {
                    hasError = true;
                    errors.append("      ✘ expectedVariables.").append(variableKey).append(": expected ").append(expectedValue).append(" but received ").append(actualValue).append("\n");
                }
            }
        }

        // Test expectedEvaluations
        if (assertion.containsKey("expectedEvaluations")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedEvaluations = (Map<String, Object>) assertion.get("expectedEvaluations");

                    if (expectedEvaluations.containsKey("flag")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> flagEvaluation = (Map<String, Object>) expectedEvaluations.get("flag");
            com.featurevisor.sdk.Evaluation actualEvaluation = evaluateFlag(f, featureKey, context);

            if (actualEvaluation != null) {
                for (Map.Entry<String, Object> entry : flagEvaluation.entrySet()) {
                    String key = entry.getKey();
                    Object expectedValue = entry.getValue();
                    Object actualValue = getEvaluationValue(actualEvaluation, key);
                    if (!Objects.equals(actualValue, expectedValue)) {
                        hasError = true;
                        errors.append("      ✘ expectedEvaluations.flag.").append(key).append(": expected ").append(expectedValue).append(" but received ").append(actualValue).append("\n");
                    }
                }
            }
        }

                    if (expectedEvaluations.containsKey("variation")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variationEvaluation = (Map<String, Object>) expectedEvaluations.get("variation");
            Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
            if (assertion.containsKey("defaultVariationValue")) {
                options.setDefaultVariationValue(assertion.get("defaultVariationValue").toString());
            }

            com.featurevisor.sdk.Evaluation actualEvaluation = evaluateVariation(f, featureKey, context, options);

            if (actualEvaluation != null) {
                for (Map.Entry<String, Object> entry : variationEvaluation.entrySet()) {
                    String key = entry.getKey();
                    Object expectedValue = entry.getValue();
                    Object actualValue = getEvaluationValue(actualEvaluation, key);
                    if (!Objects.equals(actualValue, expectedValue)) {
                        hasError = true;
                        errors.append("      ✘ expectedEvaluations.variation.").append(key).append(": expected ").append(expectedValue).append(" but received ").append(actualValue).append("\n");
                    }
                }
            }
        }

            if (expectedEvaluations.containsKey("variables")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> variablesEvaluation = (Map<String, Object>) expectedEvaluations.get("variables");
                @SuppressWarnings("unchecked")
                Map<String, Object> defaultVariableValues = (Map<String, Object>) assertion.getOrDefault("defaultVariableValues", new HashMap<>());

                for (Map.Entry<String, Object> entry : variablesEvaluation.entrySet()) {
                    String variableKey = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> expectedEvaluation = (Map<String, Object>) entry.getValue();

                    Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
                    if (defaultVariableValues.containsKey(variableKey)) {
                        options.setDefaultVariableValue(defaultVariableValues.get(variableKey));
                    }

                    com.featurevisor.sdk.Evaluation actualEvaluation = evaluateVariable(f, featureKey, variableKey, context, options);

                    if (actualEvaluation != null) {
                        for (Map.Entry<String, Object> evalEntry : expectedEvaluation.entrySet()) {
                            String key = evalEntry.getKey();
                            Object expectedValue = evalEntry.getValue();
                            Object actualValue = getEvaluationValue(actualEvaluation, key);
                            if (!Objects.equals(actualValue, expectedValue)) {
                                hasError = true;
                                errors.append("      ✘ expectedEvaluations.variables.").append(variableKey).append(".").append(key).append(": expected ").append(expectedValue).append(" but received ").append(actualValue).append("\n");
                            }
                        }
                    }
                }
            }
        }

        double duration = (System.nanoTime() - startTime) / 1_000_000.0; // Convert to milliseconds

        // Test children
        if (assertion.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) assertion.get("children");
            for (Map<String, Object> child : children) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childContext = (Map<String, Object>) child.getOrDefault("context", new HashMap<>());
                com.featurevisor.sdk.ChildInstance childF = spawn(f, childContext);
                TestResult childResult = testFeature(child, featureKey, childF, level);
                duration += childResult.duration;
                hasError = hasError || childResult.hasError;

                if (childResult.hasError) {
                    errors.append(childResult.errors);
                }
            }
        }

        return new TestResult(hasError, errors.toString(), duration);
    }

    /**
     * Helper methods to work with both Instance and ChildInstance
     */
    private boolean isEnabled(Object f, String featureKey, Map<String, Object> context) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).isEnabled(featureKey, context);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).isEnabled(featureKey, context);
        }
        return false;
    }

    private Object getVariation(Object f, String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).getVariation(featureKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).getVariation(featureKey, context, options);
        }
        return null;
    }

    private Object getVariable(Object f, String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).getVariable(featureKey, variableKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).getVariable(featureKey, variableKey, context, options);
        }
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateFlag(Object f, String featureKey, Map<String, Object> context) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateFlag(featureKey, context);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            // ChildInstance doesn't have evaluateFlag, so we'll skip this test for child instances
            return null;
        }
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateVariation(Object f, String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateVariation(featureKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            // ChildInstance doesn't have evaluateVariation, so we'll skip this test for child instances
            return null;
        }
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateVariable(Object f, String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateVariable(featureKey, variableKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            // ChildInstance doesn't have evaluateVariable, so we'll skip this test for child instances
            return null;
        }
        return null;
    }

    private com.featurevisor.sdk.ChildInstance spawn(Object f, Map<String, Object> context) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).spawn(context);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            // ChildInstance doesn't have spawn, so we'll return null
            return null;
        }
        return null;
    }

    /**
     * Get evaluation value by key
     */
    private Object getEvaluationValue(com.featurevisor.sdk.Evaluation evaluation, String key) {
        switch (key) {
            case "type": return evaluation.getType();
            case "featureKey": return evaluation.getFeatureKey();
            case "reason": return evaluation.getReason();
            case "enabled": return evaluation.getEnabled();
            case "variation": return evaluation.getVariation();
            case "variationValue": return evaluation.getVariationValue();
            case "variableKey": return evaluation.getVariableKey();
            case "variableValue": return evaluation.getVariableValue();
            case "variableOverrideIndex": return evaluation.getVariableOverrideIndex();
            case "bucketKey": return evaluation.getBucketKey();
            case "bucketValue": return evaluation.getBucketValue();
            case "ruleKey": return evaluation.getRuleKey();
            case "forceIndex": return evaluation.getForceIndex();
            case "force": return evaluation.getForce();
            case "sticky": return evaluation.getSticky();
            case "traffic": return evaluation.getTraffic();
            case "required": return evaluation.getRequired() != null ? evaluation.getRequired() : new ArrayList<>();
            case "error": return evaluation.getError();
            default: return null;
        }
    }

    /**
     * Test a segment
     */
    private TestResult testSegment(Map<String, Object> assertion, Segment segment, Logger.LogLevel level) {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        Object conditions = segment.getConditions();

        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2");
        datafile.setRevision("tester");
        datafile.setFeatures(new HashMap<>());
        datafile.setSegments(new HashMap<>());

        DatafileReader datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafile)
            .logger(Logger.createLogger(new Logger.CreateLoggerOptions().level(level))));

        boolean hasError = false;
        StringBuilder errors = new StringBuilder();
        long startTime = System.nanoTime();

        if (assertion.containsKey("expectedToMatch")) {
            boolean actual = datafileReader.allConditionsAreMatched(conditions, context);
            boolean expected = (Boolean) assertion.get("expectedToMatch");
            if (actual != expected) {
                hasError = true;
                errors.append("      ✘ expectedToMatch: expected ").append(expected).append(" but received ").append(actual).append("\n");
            }
        }

        double duration = (System.nanoTime() - startTime) / 1_000_000.0; // Convert to milliseconds
        return new TestResult(hasError, errors.toString(), duration);
    }

    /**
     * Run tests
     */
    private void test() {
        try {
            String featurevisorProjectPath = rootDirectoryPath;

            Map<String, Object> config = getConfig(featurevisorProjectPath);
            List<String> environments = getEnvironmentList(config);
            List<String> tags = getTags(config);
            Map<String, Map<String, Object>> scopesByName = getScopesByName(config);

            Map<String, Segment> segmentsByKey = getSegments(featurevisorProjectPath);
            Map<String, DatafileContent> datafileCache = new HashMap<>();

            datafileCache.putAll(buildBaseDatafiles(featurevisorProjectPath, environments));

            if (Boolean.TRUE.equals(withTags)) {
                for (String env : environments) {
                    for (String tag : tags) {
                        datafileCache.put(
                            taggedDatafileCacheKey(env, tag),
                            buildDatafile(featurevisorProjectPath, env, tag)
                        );
                    }
                }
            }

            if (Boolean.TRUE.equals(withScopes) && !scopesByName.isEmpty()) {
                // Ensure scoped datafiles are materialized on disk.
                executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor build");

                for (String env : environments) {
                    for (String scopeName : scopesByName.keySet()) {
                        Path scopedPath = getScopedDatafilePath(featurevisorProjectPath, config, env, scopeName);
                        if (!Files.exists(scopedPath)) {
                            continue;
                        }

                        String scopedDatafileOutput = Files.readString(scopedPath);
                        datafileCache.put(
                            scopedDatafileCacheKey(env, scopeName),
                            parseDatafileContent(scopedDatafileOutput, scopedPath.toString())
                        );
                    }
                }
            }

            System.out.println();

            Logger.LogLevel level = getLoggerLevel();
            List<Map<String, Object>> tests = getTests(featurevisorProjectPath);

            if (tests.isEmpty()) {
                System.out.println("No tests found");
                return;
            }

            int passedTestsCount = 0;
            int failedTestsCount = 0;
            int passedAssertionsCount = 0;
            int failedAssertionsCount = 0;

            for (Map<String, Object> test : tests) {
                String testKey = (String) test.get("key");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> assertions = (List<Map<String, Object>>) test.get("assertions");



                StringBuilder results = new StringBuilder();
                boolean testHasError = false;
                double testDuration = 0;

                for (Map<String, Object> assertion : assertions) {
                        TestResult testResult;

                    if (test.containsKey("feature")) {
                        String assertionEnvironment = assertion.get("environment") instanceof String
                            ? (String) assertion.get("environment")
                            : null;
                        String baseDatafileKey = getEnvironmentKey(assertionEnvironment);
                        String selectedDatafileKey = baseDatafileKey;

                        String scope = assertion.get("scope") instanceof String ? (String) assertion.get("scope") : null;
                        String tag = assertion.get("tag") instanceof String ? (String) assertion.get("tag") : null;

                        if (scope != null && datafileCache.containsKey(scopedDatafileCacheKey(assertionEnvironment, scope))) {
                            selectedDatafileKey = scopedDatafileCacheKey(assertionEnvironment, scope);
                        }

                        if (scope == null && tag != null && datafileCache.containsKey(taggedDatafileCacheKey(assertionEnvironment, tag))) {
                            selectedDatafileKey = taggedDatafileCacheKey(assertionEnvironment, tag);
                        }

                        DatafileContent selectedDatafile = datafileCache.get(selectedDatafileKey);
                        if (selectedDatafile == null) {
                            selectedDatafile = datafileCache.get(baseDatafileKey);
                        }

                        if (selectedDatafile == null) {
                            throw new IOException("No datafile found for assertion environment: " + assertionEnvironment);
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> effectiveAssertion = objectMapper.convertValue(
                            assertion,
                            new TypeReference<Map<String, Object>>() {}
                        );

                        if (scope != null && !Boolean.TRUE.equals(withScopes) && scopesByName.containsKey(scope)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> currentContext = (Map<String, Object>) effectiveAssertion.getOrDefault("context", new HashMap<>());
                            @SuppressWarnings("unchecked")
                            Map<String, Object> scopeContext = (Map<String, Object>) scopesByName.get(scope).getOrDefault("context", new HashMap<>());

                            Map<String, Object> mergedContext = new HashMap<>(scopeContext);
                            mergedContext.putAll(currentContext);
                            effectiveAssertion.put("context", mergedContext);
                        }

                        if (Boolean.TRUE.equals(showDatafile)) {
                            System.out.println();
                            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(selectedDatafile));
                            System.out.println();
                        }

                        Featurevisor f = Featurevisor.createInstance(new Featurevisor.Options()
                            .datafile(selectedDatafile)
                            .logLevel(level));

                        // If "at" parameter is provided, create a new SDK instance with the specific hook
                        if (effectiveAssertion.containsKey("at")) {
                            Object atObj = effectiveAssertion.get("at");
                            double atValue;

                            if (atObj instanceof Number) {
                                atValue = ((Number) atObj).doubleValue();
                            } else {
                                atValue = Double.parseDouble(atObj.toString());
                            }

                            // Create a hook that sets the bucket value to at * 1000
                            Logger logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(level));
                            HooksManager hooksManager = new HooksManager(new HooksManager.HooksManagerOptions(logger));

                            hooksManager.add(new HooksManager.Hook("at-parameter")
                                .bucketValue((options) -> (int) (atValue * 1000)));

                            f = Featurevisor.createInstance(new Featurevisor.Options()
                                .datafile(selectedDatafile)
                                .logLevel(level)
                                .hooks(hooksManager.getAll()));
                        }

                        testResult = testFeature(effectiveAssertion, (String) test.get("feature"), f, level);


                    } else if (test.containsKey("segment")) {
                        testResult = testSegment(assertion, segmentsByKey.get(test.get("segment")), level);
                    } else {
                        continue;
                    }

                    testDuration += testResult.duration;

                    if (testResult.hasError) {
                        results.append("  ✘ ").append(assertion.get("description")).append(" (").append(String.format("%.2f", testResult.duration)).append("ms)\n");
                        results.append(testResult.errors);
                        testHasError = true;
                        failedAssertionsCount++;
                    } else {
                        results.append("  ✔ ").append(assertion.get("description")).append(" (").append(String.format("%.2f", testResult.duration)).append("ms)\n");
                        passedAssertionsCount++;
                    }
                }

                if (!onlyFailures || (onlyFailures && testHasError)) {
                    System.out.println("\nTesting: " + testKey + " (" + String.format("%.2f", testDuration) + "ms)");
                    System.out.print(results);
                }

                if (testHasError) {
                    failedTestsCount++;
                } else {
                    passedTestsCount++;
                }
            }

            System.out.println();
            System.out.println("Test specs: " + passedTestsCount + " passed, " + failedTestsCount + " failed");
            System.out.println("Assertions: " + passedAssertionsCount + " passed, " + failedAssertionsCount + " failed");
            System.out.println();

            if (failedTestsCount > 0) {
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error running tests: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Run benchmark
     */
    private void benchmark() {
        try {
            if (environment == null) {
                System.out.println("Environment is required");
                return;
            }

            if (feature == null) {
                System.out.println("Feature is required");
                return;
            }

            Map<String, Object> contextMap = new HashMap<>();
            if (context != null) {
                contextMap = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            }

            Logger.LogLevel level = getLoggerLevel();
            DatafileContent datafile = buildDatafile(rootDirectoryPath, environment, null);

            Featurevisor f = Featurevisor.createInstance(new Featurevisor.Options()
                .datafile(datafile)
                .logLevel(level));

            Object value = null;

            if (variation) {
                System.out.println("Benchmarking variation for feature '" + feature + "'...");
            } else if (variable != null) {
                System.out.println("Benchmarking variable '" + variable + "' for feature '" + feature + "'...");
            } else {
                System.out.println("Benchmarking flag for feature '" + feature + "'...");
            }

            System.out.println("Against context: " + contextMap);
            System.out.println("Running " + n + " times...");

            long startTime = System.nanoTime();
            for (int i = 0; i < n; i++) {
                if (variation) {
                    value = f.getVariation(feature, contextMap);
                } else if (variable != null) {
                    value = f.getVariable(feature, variable, contextMap);
                } else {
                    value = f.isEnabled(feature, contextMap);
                }
            }

            double duration = (System.nanoTime() - startTime) / 1_000_000.0; // Convert to milliseconds

            System.out.println("Evaluated value: " + value);
            System.out.println("Total duration: " + String.format("%.3f", duration) + "ms");
            System.out.println("Average duration: " + String.format("%.3f", duration / n) + "ms");

        } catch (Exception e) {
            System.err.println("Error running benchmark: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Assess distribution
     */
    private void assessDistribution() {
        try {
            if (environment == null) {
                System.out.println("Environment is required");
                return;
            }

            if (feature == null) {
                System.out.println("Feature is required");
                return;
            }

            Map<String, Object> contextMap = new HashMap<>();
            if (context != null) {
                contextMap = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            }

            DatafileContent datafile = buildDatafile(rootDirectoryPath, environment, null);

            Featurevisor f = Featurevisor.createInstance(new Featurevisor.Options()
                .datafile(datafile)
                .logLevel(getLoggerLevel()));

            Object value = null;

            if (variation) {
                System.out.println("Assessing distribution for feature '" + feature + "'...");
            } else if (variable != null) {
                System.out.println("Assessing distribution for variable '" + variable + "' for feature '" + feature + "'...");
            } else {
                System.out.println("Assessing distribution for flag for feature '" + feature + "'...");
            }

            System.out.println("Against context: " + contextMap);
            System.out.println("Running " + n + " times...");

            Map<Object, Integer> values = new HashMap<>();

            for (int i = 0; i < n; i++) {
                Map<String, Object> currentContext = new HashMap<>(contextMap);

                if (!populateUuid.isEmpty()) {
                    for (String key : populateUuid) {
                        currentContext.put(key, generateUuid());
                    }
                }

                if (variation) {
                    value = f.getVariation(feature, currentContext);
                } else if (variable != null) {
                    value = f.getVariable(feature, variable, currentContext);
                } else {
                    value = f.isEnabled(feature, currentContext);
                }

                values.put(value, values.getOrDefault(value, 0) + 1);
            }

            System.out.println("Values:");
            for (Map.Entry<Object, Integer> entry : values.entrySet()) {
                Object val = entry.getKey();
                Integer count = entry.getValue();
                double percentage = (count.doubleValue() / n) * 100;
                System.out.println("  - " + val + ": " + count + " (" + String.format("%.2f", percentage) + "%)");
            }

        } catch (Exception e) {
            System.err.println("Error assessing distribution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Test result class
     */
    private static class TestResult {
        public final boolean hasError;
        public final String errors;
        public final double duration;

        public TestResult(boolean hasError, String errors, double duration) {
            this.hasError = hasError;
            this.errors = errors;
            this.duration = duration;
        }
    }

    /**
     * Main method for CLI execution
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new picocli.CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }
}
