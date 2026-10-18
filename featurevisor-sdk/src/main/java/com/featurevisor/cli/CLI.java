package com.featurevisor.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import com.featurevisor.sdk.Conditions;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Segment;
import com.featurevisor.sdk.Evaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.featurevisor.sdk.FeaturevisorModule;

/**
 * Command Line Interface for Featurevisor Java Library
 *
 * This class provides CLI access to the Featurevisor functions.
 * It is only used when running the application from command line.
 */
@Command(
    name = "featurevisor",
    mixinStandardHelpOptions = true,
    version = "5.0.0",
    description = "Featurevisor Java Library CLI - Test runner, benchmark, and distribution assessment",
    footer = {
        "Node.js owns project parsing, test expansion and datafile building; Java evaluates the inputs.",
        "Values accept --name=value or --name value. Repeat --target or --populate-uuid for multiple values.",
        "Distribution also supports --variation and feature --variable as Java extensions."
    }
)
public class CLI implements java.util.concurrent.Callable<Integer> {
    @FunctionalInterface
    interface NodeCommand {
        String execute(String directory, String command) throws IOException;
    }

    @Parameters(
        index = "0",
        description = "Command to execute: test, benchmark, or assess-distribution"
    )
    private String command;

    @Option(names = {"--assertion-pattern", "--assertionPattern"}, description = "Assertion pattern filter")
    private String assertionPattern;

    @Option(names = {"--context"}, description = "Context as JSON string")
    private String context;

    @Option(names = {"--environment"}, description = "Environment name")
    private String environment;

    @Option(names = {"--feature"}, description = "Feature key")
    private String feature;

    @Option(names = {"--key-pattern", "--keyPattern"}, description = "Key pattern filter")
    private String keyPattern;

    @Option(names = {"-n", "--n"}, description = "Number of iterations")
    private Integer n = 1000;

    @Option(names = {"--only-failures", "--onlyFailures"}, description = "Show only failures")
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
    private Integer inflate;

    @Option(names = {"--with-scopes"}, description = "Legacy option accepted for compatibility and ignored")
    private Boolean withScopes = false;

    @Option(names = {"--with-tags"}, description = "Legacy option accepted for compatibility and ignored")
    private Boolean withTags = false;

    @Option(names = {"--show-datafile", "--showDatafile"}, description = "Show datafile content for assertion")
    private Boolean showDatafile = false;

    @Option(names = {"--schemaVersion", "--schema-version"}, description = "Legacy option accepted for compatibility and ignored")
    private String schemaVersion;

    private String rootDirectoryPath;

    @Option(names = {"--project-directory-path", "--projectDirectoryPath", "--root-directory-path", "--rootDirectoryPath"}, description = "Featurevisor project directory")
    private String projectDirectoryPath;

    @Option(names = {"--populate-uuid", "--populateUuid"}, arity = "1", description = "Context attribute to populate with a UUID; repeat for multiple attributes")
    private List<String> populateUuid = new ArrayList<>();

    @Option(names = {"--target"}, arity = "1", description = "Target datafile; repeat for multiple targets")
    private List<String> targets = new ArrayList<>();

    @Option(names = {"--entity-type", "--entityType"}, description = "Test entity type: feature, segment, variable or scenario")
    private String entityType;

    @Option(names = {"--set"}, description = "Project set")
    private String set;

    @Option(names = {"--case"}, description = "Exact expanded test case ID")
    private String caseId;

    @Option(names = {"--json"}, description = "Print a versioned JSON report")
    private Boolean json = false;

    @Option(names = {"--pretty"}, description = "Pretty print JSON output")
    private Boolean pretty = false;

    @Option(names = {"--coverage"}, description = "Report observed rule and override selections")
    private Boolean coverage = false;

    @Option(names = {"--uncovered"}, description = "Report features and variables without tests")
    private Boolean uncovered = false;

    private String cwd;
    private ObjectMapper objectMapper;
    private final NodeCommand nodeCommand;
    private Map<String, String> segmentHelperKeys = Collections.emptyMap();
    private final List<Map<String, Object>> coverageSelections = new ArrayList<>();
    private String activeSet;
    private String activeEnvironment;
    private String activeTarget;

    public CLI() {
        this(null);
    }

    CLI(NodeCommand nodeCommand) {
        this.cwd = System.getProperty("user.dir");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
        this.nodeCommand = nodeCommand;
        // Configure ObjectMapper to handle null values properly
        this.objectMapper.setDefaultSetterInfo(com.fasterxml.jackson.annotation.JsonSetter.Value.forValueNulls(com.fasterxml.jackson.annotation.Nulls.AS_EMPTY));
    }

    @Override
    public Integer call() {
        if (!Arrays.asList("test", "benchmark", "assess-distribution").contains(command)) {
            System.err.println("Unknown command: " + command + ". Use --help for available commands.");
            return 1;
        }
        if (n == null || n < 1) {
            System.err.println("--n must be a positive integer");
            return 1;
        }
        if (entityType != null && !Arrays.asList("feature", "segment", "variable", "scenario").contains(entityType)) {
            System.err.println("--entity-type must be feature, segment, variable or scenario");
            return 1;
        }
        for (String value : Arrays.asList(projectDirectoryPath, environment, feature, variable, set, caseId)) {
            if (value != null && value.isEmpty()) {
                System.err.println("Option values must not be empty");
                return 1;
            }
        }
        if (targets.contains("") || populateUuid.contains("")) {
            System.err.println("--target and --populate-uuid require nonempty values");
            return 1;
        }
        if (context != null) {
            try {
                JsonNode parsed = objectMapper.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(context);
                if (parsed == null || !parsed.isObject()) throw new IOException("Expected an object");
            } catch (Exception error) {
                System.err.println("--context must be a JSON object");
                return 1;
            }
        }
        if (!"test".equals(command) && variation && variable != null) {
            System.err.println("--variation and --variable cannot be combined");
            return 1;
        }
        if (inflate != null && inflate < 1) {
            System.err.println("--inflate must be a positive integer");
            return 1;
        }
        if (rootDirectoryPath == null && projectDirectoryPath != null) {
            rootDirectoryPath = projectDirectoryPath;
        }
        if (rootDirectoryPath == null) {
            rootDirectoryPath = cwd;
        }

        switch (command) {
            case "test":
                return testReport();
            case "benchmark":
                return benchmark();
            case "assess-distribution":
                return assessDistribution();
            default:
                return 1;
        }
    }

    /**
     * Execute a command and return the output
     */
    String executeCommand(String command) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(org.apache.commons.exec.CommandLine.parse(command));
            System.err.print(errorStream.toString(StandardCharsets.UTF_8));
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (ExecuteException e) {
            throw new IOException("Command execution failed: " + e.getMessage() + ": "
                + (errorStream.toString(StandardCharsets.UTF_8) + "\n"
                    + outputStream.toString(StandardCharsets.UTF_8)).trim(), e);
        }
    }

    /**
     * Runs the project's own Node CLI. Node starts the local package entry directly,
     * which works the same on every platform. Without a local package, npx resolves
     * the CLI as before, through its .cmd wrapper on Windows.
     */
    static org.apache.commons.exec.CommandLine nodeCommandLine(String directory, String command) {
        org.apache.commons.exec.CommandLine parsed = org.apache.commons.exec.CommandLine.parse(command);
        String[] arguments = parsed.getArguments();
        if (!"npx".equals(parsed.getExecutable()) || arguments.length == 0
                || !"featurevisor".equals(arguments[0])) {
            return parsed;
        }
        String entry = findLocalCliEntry(directory);
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        org.apache.commons.exec.CommandLine resolved = new org.apache.commons.exec.CommandLine(
            entry != null ? "node" : windows ? "npx.cmd" : "npx");
        resolved.addArgument(entry != null ? entry : "featurevisor", false);
        for (int index = 1; index < arguments.length; index++) resolved.addArgument(arguments[index], false);
        return resolved;
    }

    static String findLocalCliEntry(String directory) {
        for (java.io.File current = new java.io.File(directory).getAbsoluteFile(); current != null;
                current = current.getParentFile()) {
            java.io.File candidate = new java.io.File(current,
                "node_modules" + java.io.File.separator + "@featurevisor" + java.io.File.separator
                    + "cli" + java.io.File.separator + "bin.js");
            if (candidate.isFile()) return candidate.getPath();
        }
        return null;
    }

    String executeCommandInDirectory(String directory, String command) throws IOException {
        return executeCommandInDirectory(directory, command, Collections.emptyList());
    }

    String executeCommandInDirectory(String directory, String command, List<String> rawArguments)
            throws IOException {
        if (nodeCommand != null) {
            String delegatedCommand = rawArguments.isEmpty()
                ? command : command + " " + String.join(" ", rawArguments);
            return nodeCommand.execute(directory, delegatedCommand);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWorkingDirectory(new java.io.File(directory));

        try {
            org.apache.commons.exec.CommandLine commandLine = nodeCommandLine(directory, command);
            for (String argument : rawArguments) commandLine.addArgument(argument, false);
            executor.execute(commandLine);
            System.err.print(errorStream.toString(StandardCharsets.UTF_8));
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (ExecuteException e) {
            throw new IOException("Command execution failed: " + e.getMessage() + ": "
                + (errorStream.toString(StandardCharsets.UTF_8) + "\n"
                    + outputStream.toString(StandardCharsets.UTF_8)).trim(), e);
        }
    }

    /**
     * Get config from featurevisor project
     */
    private Map<String, Object> getConfig(String featurevisorProjectPath) throws IOException {
        progress("Getting config...");
        String configOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor config --json");
        return objectMapper.readValue(configOutput, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Get segments from featurevisor project
     */
    private Map<String, Segment> getSegments(String featurevisorProjectPath) throws IOException {
        progress("Getting segments...");
        String segmentsOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor list --segments --json", selectionArguments());
        List<Segment> segments = objectMapper.readValue(segmentsOutput, new TypeReference<List<Segment>>() {});
        Map<String, Segment> segmentsByKey = new HashMap<>();
        for (Segment segment : segments) {
            segmentsByKey.put(segment.getKey(), segment);
        }
        return segmentsByKey;
    }

    String getEnvironmentKey(String environment) {
        return targetDatafileCacheKey(environment, null);
    }

    String targetDatafileCacheKey(String environment, String target) {
        return objectMapper.createArrayNode().add(environment).add(target).toString();
    }

    String selectDatafileKeyForAssertion(Map<String, Object> assertion, Map<String, DatafileContent> datafileCache) {
        String assertionEnvironment = assertion.get("environment") instanceof String
            ? (String) assertion.get("environment")
            : null;
        String target = assertion.get("target") instanceof String ? (String) assertion.get("target") : null;

        if (target != null) {
            return targetDatafileCacheKey(assertionEnvironment, target);
        }

        return getEnvironmentKey(assertionEnvironment);
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
        String target
    ) throws IOException {
        List<String> arguments = new ArrayList<>(Arrays.asList("build", "--json"));
        if (environment != null && !environment.isEmpty()) {
            arguments.add("--environment=" + environment);
        }
        if (target != null) {
            arguments.add("--target=" + target);
        }
        if (inflate != null) {
            arguments.add("--inflate=" + inflate);
        }
        if (set != null) arguments.add("--set=" + set);
        String datafileOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor", arguments);
        return parseDatafileContent(datafileOutput, String.join(" ", arguments));
    }

    private Map<String, DatafileContent> buildBaseDatafiles(
        String featurevisorProjectPath,
        List<String> environments
    ) throws IOException {
        Map<String, DatafileContent> datafilesByEnvironment = new HashMap<>();
        for (String env : environments) {
            progress("Building datafile for environment: " + (env == null ? "default" : env) + "...");
            datafilesByEnvironment.put(getEnvironmentKey(env), buildDatafile(featurevisorProjectPath, env, null));
        }
        return datafilesByEnvironment;
    }

    private Map<String, DatafileContent> buildTargetDatafiles(
        String featurevisorProjectPath,
        List<String> environments,
        List<String> targets
    ) throws IOException {
        Map<String, DatafileContent> datafilesByTarget = new HashMap<>();
        for (String env : environments) {
            for (String target : targets) {
                progress("Building datafile for target: " + target + " environment: " + (env == null ? "default" : env) + "...");
                datafilesByTarget.put(targetDatafileCacheKey(env, target), buildDatafile(featurevisorProjectPath, env, target));
            }
        }
        return datafilesByTarget;
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

    private List<String> getTargets(String featurevisorProjectPath) throws IOException {
        progress("Getting targets...");
        String targetsOutput = executeCommandInDirectory(featurevisorProjectPath, "npx featurevisor list --targets --json", selectionArguments());
        JsonNode targetsNode = objectMapper.readTree(targetsOutput);
        List<String> targets = new ArrayList<>();

        if (targetsNode.isArray()) {
            for (JsonNode targetNode : targetsNode) {
                if (targetNode.isTextual()) {
                    targets.add(targetNode.asText());
                } else if (targetNode.has("name")) {
                    targets.add(targetNode.get("name").asText());
                } else if (targetNode.has("key")) {
                    targets.add(targetNode.get("key").asText());
                }
            }
        } else if (targetsNode.isObject()) {
            Iterator<String> fieldNames = targetsNode.fieldNames();
            while (fieldNames.hasNext()) {
                targets.add(fieldNames.next());
            }
        }

        return targets;
    }

    /**
     * Get diagnostics level based on CLI options
     */
    private FeaturevisorLogLevel getDiagnosticsLevel() {
        if (verbose) {
            return FeaturevisorLogLevel.DEBUG;
        } else if (quiet) {
            return FeaturevisorLogLevel.ERROR;
        } else {
            return FeaturevisorLogLevel.WARN;
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
        return getTests(featurevisorProjectPath, true);
    }

    private List<Map<String, Object>> getTests(String featurevisorProjectPath, boolean includeCase) throws IOException {
        List<String> rawArguments = new ArrayList<>();
        if (keyPattern != null) {
            rawArguments.add("--keyPattern=" + keyPattern);
        }
        if (assertionPattern != null) {
            rawArguments.add("--assertionPattern=" + assertionPattern);
        }
        if (entityType != null) {
            rawArguments.add("--entityType=" + entityType);
        }
        String command = "npx featurevisor list --tests --execution-protocol --apply-matrix --execution-metadata --json";
        rawArguments.addAll(selectionArguments());
        if (includeCase && caseId != null) rawArguments.add("--case=" + caseId);
        String testsOutput = executeCommandInDirectory(featurevisorProjectPath, command, rawArguments);
        JsonNode document = objectMapper.readTree(testsOutput);
        if (!document.isObject()) throw new IOException("Test execution protocol output must be an object");
        if (!document.has("kind") || !document.get("kind").isTextual()
                || !"featurevisor.test-execution".equals(document.get("kind").textValue())) {
            throw new IOException("Test execution protocol kind must be featurevisor.test-execution");
        }
        JsonNode version = document.get("schemaVersion");
        if (version == null || !version.isNumber() || version.doubleValue() != 1.0) {
            throw new IOException("Test execution protocol schemaVersion must be the number 1");
        }
        JsonNode tests = document.get("tests");
        if (tests == null || !tests.isArray()) {
            throw new IOException("Test execution protocol tests must be an array of objects");
        }
        for (JsonNode test : tests) {
            if (!test.isObject()) throw new IOException("Test execution protocol tests must be an array of objects");
        }
        return objectMapper.convertValue(tests, new TypeReference<List<Map<String, Object>>>() {});
    }

    private void progress(String message) {
        if (!Boolean.TRUE.equals(json)) System.out.println(message);
    }

    private List<String> selectionArguments() {
        return set == null ? Collections.emptyList() : Collections.singletonList("--set=" + set);
    }

    /**
     * Test a feature
     */
    private TestResult testFeature(Map<String, Object> assertion, String featureKey, Object f, FeaturevisorLogLevel level) {
        return testFeature(assertion, featureKey, f, level, Collections.emptyList());
    }

    private TestResult testFeature(Map<String, Object> assertion, String featureKey, Object f,
            FeaturevisorLogLevel level, List<Object> prefix) {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> sticky = (Map<String, Object>) assertion.getOrDefault("stickyFeatures", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> stickyVariables =
            (Map<String, Object>) assertion.getOrDefault("stickyVariables", new HashMap<>());

        // Update the SDK instance context and sticky values for this assertion
        if (f instanceof Featurevisor) {
            ((Featurevisor) f).setContext(context, true);
            ((Featurevisor) f).setStickyFeatures(sticky, true);
            ((Featurevisor) f).setStickyVariables(stickyVariables, true);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            ((com.featurevisor.sdk.ChildInstance) f).setContext(context, true);
            ((com.featurevisor.sdk.ChildInstance) f).setStickyFeatures(sticky, true);
            ((com.featurevisor.sdk.ChildInstance) f).setStickyVariables(stickyVariables, true);
        }

        boolean hasError = false;
        StringBuilder errors = new StringBuilder();
        List<TestError> reportErrors = new ArrayList<>();
        long startTime = System.nanoTime();

        if (assertion.containsKey("expectedToBeEnabled")) {
            boolean isEnabled = isEnabled(f, featureKey, context);
            boolean expected = (Boolean) assertion.get("expectedToBeEnabled");
            if (isEnabled != expected) {
                hasError = true;
                String message = "expectedToBeEnabled: expected " + expected + " but received " + isEnabled;
                errors.append("      ✘ ").append(message).append("\n");
                reportErrors.add(new TestError(message, "flag", appendPath(prefix, "expectedToBeEnabled"),
                    expected, isEnabled, childDetails(prefix)));
            }
        }

        if (assertion.containsKey("expectedVariation")) {
            Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
            if (assertion.containsKey("defaultVariationValue")) {
                options.setDefaultVariationValue(assertion.get("defaultVariationValue").toString());
            }

            Object variation = getVariation(f, featureKey, context, options);
            Object expected = assertion.get("expectedVariation");
            if (!Objects.equals(variation, expected)) {
                hasError = true;
                String message = "expectedVariation: expected " + formatTestValue(expected)
                    + " but received " + formatTestValue(variation);
                errors.append("      ✘ ").append(message).append("\n");
                reportErrors.add(new TestError(message, "variation", appendPath(prefix, "expectedVariation"),
                    expected, variation, childDetails(prefix)));
            }
        }

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
                    String message = "expectedVariables." + variableKey + ": expected "
                        + formatTestValue(expectedValue) + " but received " + formatTestValue(actualValue);
                    errors.append("      ✘ ").append(message).append("\n");
                    Map<String, Object> details = childDetails(prefix);
                    details.put("variableKey", variableKey);
                    reportErrors.add(new TestError(message, "variable",
                        appendPath(prefix, "expectedVariables", variableKey), expectedValue, actualValue, details));
                }
            }
        }

        if (assertion.containsKey("expectedEvaluations")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedEvaluations = (Map<String, Object>) assertion.get("expectedEvaluations");

            if (expectedEvaluations.containsKey("flag")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> flagEvaluation = (Map<String, Object>) expectedEvaluations.get("flag");
                com.featurevisor.sdk.Evaluation actualEvaluation = evaluateFlag(f, featureKey, context);
                if (actualEvaluation != null) {
                    for (Map.Entry<String, Object> entry : flagEvaluation.entrySet()) {
                        Object actualValue = getEvaluationValue(actualEvaluation, entry.getKey());
                        if (!Objects.equals(actualValue, entry.getValue())) {
                            hasError = true;
                            String message = "expectedEvaluations.flag." + entry.getKey() + ": expected "
                                + formatTestValue(entry.getValue()) + " but received " + formatTestValue(actualValue);
                            errors.append("      ✘ ").append(message).append("\n");
                            Map<String, Object> details = childDetails(prefix);
                            details.put("evaluationType", "flag");
                            details.put("evaluationKey", entry.getKey());
                            reportErrors.add(new TestError(message, "evaluation",
                                appendPath(prefix, "expectedEvaluations", "flag", entry.getKey()),
                                entry.getValue(), actualValue, details));
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
                        Object actualValue = getEvaluationValue(actualEvaluation, entry.getKey());
                        if (!Objects.equals(actualValue, entry.getValue())) {
                            hasError = true;
                            String message = "expectedEvaluations.variation." + entry.getKey() + ": expected "
                                + formatTestValue(entry.getValue()) + " but received " + formatTestValue(actualValue);
                            errors.append("      ✘ ").append(message).append("\n");
                            Map<String, Object> details = childDetails(prefix);
                            details.put("evaluationType", "variation");
                            details.put("evaluationKey", entry.getKey());
                            reportErrors.add(new TestError(message, "evaluation",
                                appendPath(prefix, "expectedEvaluations", "variation", entry.getKey()),
                                entry.getValue(), actualValue, details));
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
                            Object actualValue = getEvaluationValue(actualEvaluation, evalEntry.getKey());
                            if (!Objects.equals(actualValue, evalEntry.getValue())) {
                                hasError = true;
                                String message = "expectedEvaluations.variables." + variableKey + "."
                                    + evalEntry.getKey() + ": expected " + formatTestValue(evalEntry.getValue())
                                    + " but received " + formatTestValue(actualValue);
                                errors.append("      ✘ ").append(message).append("\n");
                                Map<String, Object> details = childDetails(prefix);
                                details.put("variableKey", variableKey);
                                details.put("evaluationType", "variable");
                                details.put("evaluationKey", evalEntry.getKey());
                                reportErrors.add(new TestError(message, "evaluation",
                                    appendPath(prefix, "expectedEvaluations", "variables", variableKey, evalEntry.getKey()),
                                    evalEntry.getValue(), actualValue, details));
                            }
                        }
                    }
                }
            }
        }

        double duration = (System.nanoTime() - startTime) / 1_000_000.0; // Convert to milliseconds

        if (assertion.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) assertion.get("children");
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                Map<String, Object> child = children.get(childIndex);
                @SuppressWarnings("unchecked")
                Map<String, Object> childContext = (Map<String, Object>) child.getOrDefault("context", new HashMap<>());
                com.featurevisor.sdk.ChildInstance childF = spawn(f, childContext);
                TestResult childResult;
                try {
                    childResult = testFeature(child, featureKey, childF, level,
                        appendPath(prefix, "children", childIndex));
                } finally {
                    childF.close();
                }
                duration += childResult.duration;
                hasError = hasError || childResult.hasError;

                if (childResult.hasError) {
                    errors.append(childResult.errors);
                }
                reportErrors.addAll(childResult.reportErrors);
            }
        }

        return new TestResult(hasError, errors.toString(), duration, reportErrors);
    }

    private TestResult testGlobalVariable(Map<String, Object> assertion, String variableKey, Featurevisor f,
            DatafileContent datafile, List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics,
            Map<String, Object> segmentDefinitions) {
        @SuppressWarnings("unchecked") Map<String, Object> context = (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        f.setContext(context, true);
        Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
        if (assertion.containsKey("defaultVariableValue")) options.setDefaultVariableValue(assertion.get("defaultVariableValue"));
        long startTime = System.nanoTime();
        Evaluation evaluation = f.evaluateVariable(variableKey, new HashMap<>(), options);
        boolean hasError = false;
        StringBuilder errors = new StringBuilder();
        List<TestError> reportErrors = new ArrayList<>();
        if (assertion.containsKey("expectedValue") && !Objects.equals(assertion.get("expectedValue"), evaluation.getVariableValue())) {
            hasError = true;
            String message = "expectedValue: expected " + formatTestValue(assertion.get("expectedValue"))
                + " but received " + formatTestValue(evaluation.getVariableValue());
            errors.append("      ✘ ").append(message).append("\n");
            reportErrors.add(expectationError(message, "variable", Arrays.asList("expectedValue"),
                assertion.get("expectedValue"), evaluation.getVariableValue(),
                linkedMap("variableKey", variableKey)));
        }
        if (assertion.containsKey("expectedEvaluation")) {
            @SuppressWarnings("unchecked") Map<String, Object> expected = (Map<String, Object>) assertion.get("expectedEvaluation");
            for (Map.Entry<String, Object> entry : expected.entrySet()) {
                Object actual = getEvaluationValue(evaluation, entry.getKey());
                if (!Objects.equals(entry.getValue(), actual)) {
                    hasError = true;
                    errors.append("      ✘ expectedEvaluation.").append(entry.getKey()).append(": expected ")
                        .append(formatTestValue(entry.getValue())).append(" but received ").append(formatTestValue(actual)).append("\n");
                    reportErrors.add(expectationError("expectedEvaluation." + entry.getKey() + ": expected "
                        + formatTestValue(entry.getValue()) + " but received " + formatTestValue(actual),
                        "evaluation", Arrays.asList("expectedEvaluation", entry.getKey()), entry.getValue(), actual,
                        linkedMap("evaluationType", "variable", "evaluationKey", entry.getKey())));
                }
            }
        }
        if (assertion.containsKey("checks")) {
            List<VariableChecks.CheckFailure> checkErrors = testCheckFailures(f, assertion, variableKey, datafile,
                diagnostics, segmentDefinitions, context);
            if (!checkErrors.isEmpty()) {
                hasError = true;
                for (VariableChecks.CheckFailure error : checkErrors) {
                    errors.append("      ✘ ").append(error.message).append("\n");
                    reportErrors.add(checkError(error, null));
                }
            }
        }
        if (assertion.containsKey("children")) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>) assertion.get("children");
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                Map<String, Object> childAssertion = children.get(childIndex);
                @SuppressWarnings("unchecked") Map<String, Object> childContext = (Map<String, Object>) childAssertion.getOrDefault("context", new HashMap<>());
                @SuppressWarnings("unchecked") Map<String, Object> childStickyFeatures = (Map<String, Object>) childAssertion.get("stickyFeatures");
                @SuppressWarnings("unchecked") Map<String, Object> childStickyVariables = (Map<String, Object>) childAssertion.get("stickyVariables");
                if (childStickyFeatures == null) childStickyFeatures = new HashMap<>();
                if (childStickyVariables == null) childStickyVariables = new HashMap<>();
                Featurevisor.SpawnOptions spawnOptions = new Featurevisor.SpawnOptions()
                    .stickyFeatures(childStickyFeatures)
                    .stickyVariables(childStickyVariables);
                com.featurevisor.sdk.ChildInstance child = f.spawn(childContext, spawnOptions);
                try {
                    TestResult childResult = testGlobalVariableChild(childAssertion, variableKey, child, childIndex,
                        datafile, diagnostics, segmentDefinitions, mergeContexts(context, childContext));
                    hasError = hasError || childResult.hasError;
                    errors.append(childResult.errors);
                    reportErrors.addAll(childResult.reportErrors);
                } finally {
                    child.close();
                }
            }
        }
        return new TestResult(hasError, errors.toString(), (System.nanoTime() - startTime) / 1_000_000.0, reportErrors);
    }

    private TestResult testGlobalVariableChild(Map<String, Object> assertion, String variableKey,
            com.featurevisor.sdk.ChildInstance child, int childIndex, DatafileContent datafile,
            List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics, Map<String, Object> segmentDefinitions,
            Map<String, Object> effectiveContext) {
        Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
        if (assertion.containsKey("defaultVariableValue")) options.setDefaultVariableValue(assertion.get("defaultVariableValue"));
        Evaluation evaluation = child.evaluateVariable(variableKey, new HashMap<>(), options);
        boolean hasError = false;
        StringBuilder errors = new StringBuilder();
        List<TestError> reportErrors = new ArrayList<>();
        String prefix = "children[" + childIndex + "].";
        if (assertion.containsKey("expectedValue") && !Objects.equals(assertion.get("expectedValue"), evaluation.getVariableValue())) {
            hasError = true;
            errors.append("      ✘ ").append(prefix).append("expectedValue: expected ")
                .append(formatTestValue(assertion.get("expectedValue"))).append(" but received ")
                .append(formatTestValue(evaluation.getVariableValue())).append("\n");
            reportErrors.add(expectationError(prefix + "expectedValue: expected "
                + formatTestValue(assertion.get("expectedValue")) + " but received "
                + formatTestValue(evaluation.getVariableValue()),
                "variable", Arrays.asList("children", childIndex, "expectedValue"),
                assertion.get("expectedValue"), evaluation.getVariableValue(),
                linkedMap("variableKey", variableKey, "childIndex", childIndex)));
        }
        if (assertion.containsKey("expectedEvaluation")) {
            @SuppressWarnings("unchecked") Map<String, Object> expected = (Map<String, Object>) assertion.get("expectedEvaluation");
            for (Map.Entry<String, Object> entry : expected.entrySet()) {
                Object actual = getEvaluationValue(evaluation, entry.getKey());
                if (!Objects.equals(entry.getValue(), actual)) {
                    hasError = true;
                    errors.append("      ✘ ").append(prefix).append("expectedEvaluation.").append(entry.getKey())
                        .append(": expected ").append(formatTestValue(entry.getValue()))
                        .append(" but received ").append(formatTestValue(actual)).append("\n");
                    reportErrors.add(expectationError(prefix + "expectedEvaluation." + entry.getKey()
                        + ": expected " + formatTestValue(entry.getValue()) + " but received " + formatTestValue(actual),
                        "evaluation", Arrays.asList("children", childIndex, "expectedEvaluation", entry.getKey()),
                        entry.getValue(), actual,
                        linkedMap("evaluationType", "variable", "evaluationKey", entry.getKey(), "childIndex", childIndex)));
                }
            }
        }
        if (assertion.containsKey("checks")) {
            List<VariableChecks.CheckFailure> checkErrors = testCheckFailures(child, assertion, variableKey, datafile,
                diagnostics, segmentDefinitions, effectiveContext);
            if (!checkErrors.isEmpty()) {
                hasError = true;
                for (VariableChecks.CheckFailure error : checkErrors) {
                    errors.append("      ✘ ").append(prefix).append(error.message).append("\n");
                    reportErrors.add(checkError(error, childIndex));
                }
            }
        }
        return new TestResult(hasError, errors.toString(), 0, reportErrors);
    }

    private TestResult testScenario(Map<String, Object> assertion, Featurevisor f, DatafileContent datafile,
            List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics, Map<String, Object> segmentDefinitions) {
        long startTime = System.nanoTime();
        @SuppressWarnings("unchecked") Map<String, Object> rootContext =
            (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        boolean hasChecks = assertion.containsKey("checks");
        boolean hasChildren = assertion.get("children") instanceof List && !((List<?>) assertion.get("children")).isEmpty();
        List<VariableChecks.CheckFailure> failures = hasChecks
            ? testCheckFailures(f, assertion, null, datafile, diagnostics, segmentDefinitions, rootContext)
            : new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        List<TestError> reportErrors = new ArrayList<>();
        if (!hasChecks && !hasChildren) {
            String message = "scenario assertion requires checks or children";
            errors.append("      ✘ ").append(message).append("\n");
            reportErrors.add(expectationError(message, "check", Collections.emptyList(),
                "checks or children", null, null));
        }
        for (VariableChecks.CheckFailure failure : failures) {
            errors.append("      ✘ ").append(failure.message).append("\n");
            reportErrors.add(checkError(failure, null));
        }
        if (hasChildren) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>) assertion.get("children");
            for (int index = 0; index < children.size(); index++) {
                Map<String, Object> childAssertion = children.get(index);
                @SuppressWarnings("unchecked") Map<String, Object> childContext = (Map<String, Object>) childAssertion.getOrDefault("context", new HashMap<>());
                @SuppressWarnings("unchecked") Map<String, Object> stickyFeatures = (Map<String, Object>) childAssertion.get("stickyFeatures");
                @SuppressWarnings("unchecked") Map<String, Object> stickyVariables = (Map<String, Object>) childAssertion.get("stickyVariables");
                com.featurevisor.sdk.ChildInstance child = f.spawn(childContext, new Featurevisor.SpawnOptions()
                    .stickyFeatures(stickyFeatures == null ? new HashMap<>() : stickyFeatures)
                    .stickyVariables(stickyVariables == null ? new HashMap<>() : stickyVariables));
                try {
                    List<VariableChecks.CheckFailure> childFailures = childAssertion.containsKey("checks")
                        ? testCheckFailures(child, childAssertion, null, datafile, diagnostics, segmentDefinitions,
                            mergeContexts(rootContext, childContext))
                        : new ArrayList<>();
                    if (!childAssertion.containsKey("checks")) {
                        String message = "children[" + index + "].child requires checks";
                        errors.append("      ✘ ").append(message).append("\n");
                        reportErrors.add(expectationError(message, "check", Arrays.asList("children", index),
                            "checks", null, linkedMap("childIndex", index)));
                    }
                    for (VariableChecks.CheckFailure failure : childFailures) {
                        errors.append("      ✘ children[").append(index).append("].").append(failure.message).append("\n");
                        reportErrors.add(checkError(failure, index));
                    }
                    failures.addAll(childFailures);
                } finally { child.close(); }
            }
        }
        boolean hasError = !failures.isEmpty() || !reportErrors.isEmpty();
        return new TestResult(hasError, errors.toString(), (System.nanoTime() - startTime) / 1_000_000.0, reportErrors);
    }

    private List<String> testChecks(Object sdk, Map<String, Object> assertion, String ownerVariable,
            DatafileContent datafile, List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics) {
        List<String> messages = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<String, Object> context =
            (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
        for (VariableChecks.CheckFailure failure : testCheckFailures(sdk, assertion, ownerVariable, datafile,
                diagnostics, Collections.emptyMap(), context)) {
            messages.add(failure.message);
        }
        return messages;
    }

    private List<VariableChecks.CheckFailure> testCheckFailures(Object sdk, Map<String, Object> assertion,
            String ownerVariable, DatafileContent datafile,
            List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics, Map<String, Object> segmentDefinitions,
            Map<String, Object> effectiveContext) {
        return new VariableChecks(reference -> {
            String featureKey = reference.get("feature") instanceof String ? (String) reference.get("feature") : null;
            String variableKey = reference.get("variable") instanceof String ? (String) reference.get("variable") : ownerVariable;
            if (variableKey == null || variableKey.isEmpty()) return VariableChecks.Resolution.error("variable is required");
            if (featureKey != null && !reference.containsKey("variable")) {
                return VariableChecks.Resolution.error("feature references require an explicit variable");
            }
            if (!reference.containsKey("variable") && ownerVariable != null) reference.put("variable", ownerVariable);
            @SuppressWarnings("unchecked") Map<String, Object> context = (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
            try {
                diagnostics.clear();
                Evaluation evaluation;
                Object value;
                if (featureKey == null) {
                    if (datafile.getVariables() == null || !datafile.getVariables().containsKey(variableKey)) {
                        return VariableChecks.Resolution.error("global variable \"" + variableKey + "\" is absent from the selected datafile");
                    }
                    evaluation = evaluateGlobalVariable(sdk, variableKey, context);
                    if ("error".equals(evaluation.getReason()) || evaluation.getError() != null) {
                        return VariableChecks.Resolution.error("global variable \"" + variableKey + "\" evaluation failed: "
                            + (evaluation.getError() == null ? evaluation.getReason() : evaluation.getError().getMessage()));
                    }
                    value = getGlobalVariable(sdk, variableKey, context);
                } else {
                    com.featurevisor.sdk.Feature featureData = datafile.getFeature(featureKey);
                    if (featureData == null || featureData.getVariablesSchema() == null
                            || !featureData.getVariablesSchema().containsKey(variableKey)) {
                        return VariableChecks.Resolution.error("feature variable \"" + featureKey + "." + variableKey
                            + "\" is absent from the selected datafile");
                    }
                    evaluation = evaluateVariable(sdk, featureKey, variableKey, context, new Featurevisor.OverrideOptions());
                    if ("error".equals(evaluation.getReason()) || evaluation.getError() != null) {
                        return VariableChecks.Resolution.error("feature variable \"" + featureKey + "." + variableKey
                            + "\" evaluation failed: " + (evaluation.getError() == null ? evaluation.getReason() : evaluation.getError().getMessage()));
                    }
                    value = getVariable(sdk, featureKey, variableKey, context, new Featurevisor.OverrideOptions());
                }
                for (com.featurevisor.sdk.FeaturevisorDiagnostic diagnostic : diagnostics) {
                    if (diagnostic.getLevel() == FeaturevisorLogLevel.ERROR) {
                        return VariableChecks.Resolution.error("getter failed: " + diagnostic.getCode() + ": " + diagnostic.getMessage());
                    }
                }
                return VariableChecks.Resolution.value(value);
            } catch (Exception error) {
                return VariableChecks.Resolution.error("getter failed: " + error.getMessage());
            }
        }, (featureKey, getter) -> {
            if (datafile.getFeature(featureKey) == null) {
                return VariableChecks.Resolution.error("feature is absent from the selected datafile");
            }
            @SuppressWarnings("unchecked") Map<String, Object> context =
                (Map<String, Object>) assertion.getOrDefault("context", new HashMap<>());
            diagnostics.clear();
            try {
                Object value = getter.equals("enabled")
                    ? isEnabled(sdk, featureKey, context)
                    : getVariation(
                        sdk, featureKey, context, new Featurevisor.OverrideOptions()
                    );
                String diagnostic = getterDiagnosticError(diagnostics);
                return diagnostic == null
                    ? VariableChecks.Resolution.value(value)
                    : VariableChecks.Resolution.error(diagnostic);
            } catch (Exception error) {
                return VariableChecks.Resolution.error(error.getMessage());
            }
        }, segmentKey -> {
            String error = SegmentEvaluations.validate(segmentKey, datafile, segmentDefinitions);
            if (error != null) return VariableChecks.Resolution.error(error);
            String helper = segmentHelperKeys.get(segmentKey);
            if (helper == null) return VariableChecks.Resolution.error("segment definition is missing");
            diagnostics.clear();
            Evaluation evaluation = sdk instanceof Featurevisor
                ? ((Featurevisor) sdk).evaluateFlag(helper, effectiveContext)
                : ((com.featurevisor.sdk.ChildInstance) sdk).evaluateFlag(helper, effectiveContext);
            String diagnostic = getterDiagnosticError(diagnostics);
            if (evaluation.getError() != null || diagnostic != null) {
                return VariableChecks.Resolution.error(diagnostic != null ? diagnostic : evaluation.getError().getMessage());
            }
            return VariableChecks.Resolution.value(Boolean.TRUE.equals(evaluation.getEnabled()));
        }, segmentKey -> SegmentEvaluations.validate(segmentKey, datafile, segmentDefinitions))
            .evaluateStructured(assertion.get("checks"));
    }

    private Map<String, Object> mergeContexts(Map<String, Object> root, Map<String, Object> child) {
        Map<String, Object> merged = new LinkedHashMap<>(root);
        merged.putAll(child);
        return merged;
    }

    private Map<String, Object> segmentDefinitions(Map<String, Object> test) {
        if (!(test.get("_segments") instanceof Map)) return Collections.emptyMap();
        @SuppressWarnings("unchecked") Map<String, Object> definitions = (Map<String, Object>) test.get("_segments");
        return definitions;
    }

    private String validateSegmentDefinition(Map<String, Object> definitions, String segmentKey) {
        Object rawDefinition = definitions.get(segmentKey);
        if (!(rawDefinition instanceof Map)) {
            return "segment \"" + segmentKey + "\" definition is missing";
        }
        if (!((Map<?, ?>) rawDefinition).containsKey("conditions")) {
            return "segment \"" + segmentKey + "\" definition has no conditions";
        }
        return null;
    }

    private TestError checkError(VariableChecks.CheckFailure failure, Integer childIndex) {
        Map<String, Object> details = failure.details == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(failure.details);
        if (childIndex != null) details.put("childIndex", childIndex);
        List<Object> path = new ArrayList<>();
        if (childIndex != null) path.addAll(Arrays.asList("children", childIndex));
        path.addAll(failure.path);
        Object evaluatedValue = details.get("evaluated");
        if (evaluatedValue instanceof List) {
            List<Map<String, Object>> evaluated = new ArrayList<>();
            for (Object raw : (List<?>) evaluatedValue) {
                if (!(raw instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> leaf = new LinkedHashMap<>((Map<String, Object>) raw);
                List<Object> leafPath = new ArrayList<>();
                if (childIndex != null) leafPath.addAll(Arrays.asList("children", childIndex));
                leafPath.add("checks");
                if (leaf.get("path") instanceof List) leafPath.addAll((List<?>) leaf.get("path"));
                leaf.put("path", leafPath);
                evaluated.add(leaf);
            }
            details.put("evaluated", evaluated);
        }
        return new TestError(failure.message, "check", path, failure.expected, failure.actual, details);
    }

    private TestError expectationError(String message, String type, List<Object> path, Object expected,
            Object actual, Map<String, Object> details) {
        return new TestError(message, type, path, expected, actual, details);
    }

    private List<Object> appendPath(List<Object> prefix, Object... parts) {
        List<Object> path = new ArrayList<>(prefix);
        path.addAll(Arrays.asList(parts));
        return path;
    }

    private Map<String, Object> childDetails(List<Object> prefix) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (prefix.size() >= 2 && "children".equals(prefix.get(prefix.size() - 2))) {
            details.put("childIndex", prefix.get(prefix.size() - 1));
        }
        return details;
    }

    private String getterDiagnosticError(List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics) {
        for (com.featurevisor.sdk.FeaturevisorDiagnostic diagnostic : diagnostics) {
            if (diagnostic.getLevel() == FeaturevisorLogLevel.ERROR) {
                return diagnostic.getCode() + ": " + diagnostic.getMessage();
            }
        }
        return null;
    }

    private String formatTestValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Featurevisor createInstanceForAssertion(DatafileContent datafile, Map<String, Object> assertion,
            FeaturevisorLogLevel level, List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics) {
        Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
            .datafile(datafile)
            .logLevel(level)
            .context((Map<String, Object>) assertion.getOrDefault("context", new HashMap<>()))
            .stickyFeatures((Map<String, Object>) assertion.get("stickyFeatures"))
            .stickyVariables((Map<String, Object>) assertion.get("stickyVariables"))
            .onDiagnostic(diagnostics::add);
        List<FeaturevisorModule> modules = new ArrayList<>();
        if (assertion.get("at") instanceof Number) {
            double at = ((Number) assertion.get("at")).doubleValue();
            modules.add(new FeaturevisorModule("test-module").bucketValue(bucket -> (int) (at * 1000)));
        }
        if (Boolean.TRUE.equals(coverage)) {
            modules.add(new FeaturevisorModule("test-coverage").afterEvaluation((evaluation, evaluateOptions) -> {
                recordCoverage(evaluation);
                return evaluation;
            }));
        }
        if (!modules.isEmpty()) options.modules(modules);
        return Featurevisor.createFeaturevisor(options);
    }

    private void recordCoverage(Evaluation evaluation) {
        if (segmentHelperKeys.containsValue(evaluation.getFeatureKey())) return;
        List<List<String>> paths = evaluation.getVariableOverridePaths();
        if (paths != null && !paths.isEmpty()) {
            for (List<String> path : paths) addCoverageSelection(evaluation, new ArrayList<>(path));
            return;
        }
        if (evaluation.getVariableOverridePath() != null) {
            addCoverageSelection(evaluation, new ArrayList<>(evaluation.getVariableOverridePath()));
            return;
        }
        if (evaluation.getVariableOverrideKey() != null) {
            addCoverageSelection(evaluation, Collections.singletonList(evaluation.getVariableOverrideKey()));
            return;
        }
        if (evaluation.getRuleKey() != null || evaluation.getVariableOverrideIndex() != null) addCoverageSelection(evaluation, null);
    }

    private void addCoverageSelection(Evaluation evaluation, List<String> overridePath) {
        if (evaluation.getRuleKey() == null && overridePath == null && evaluation.getVariableOverrideIndex() == null) return;
        Map<String, Object> selection = linkedMap(
            "set", activeSet,
            "environment", activeEnvironment,
            "target", activeTarget
        );
        if (evaluation.getFeatureKey() != null) selection.put("featureKey", evaluation.getFeatureKey());
        if (evaluation.getVariableKey() != null) selection.put("variableKey", evaluation.getVariableKey());
        if (evaluation.getRuleKey() != null) selection.put("ruleKey", evaluation.getRuleKey());
        if (evaluation.getVariation() != null) selection.put("variation", evaluation.getVariation().getValue());
        if (overridePath != null) selection.put("overridePath", overridePath);
        else if (evaluation.getVariableOverrideIndex() != null) selection.put("overrideIndex", evaluation.getVariableOverrideIndex());
        coverageSelections.add(selection);
    }

    private TestResult missingDatafileResult(Map<String, Object> assertion) {
        String environmentName = assertion.get("environment") instanceof String ? assertion.get("environment").toString() : "none";
        String targetName = assertion.get("target") instanceof String ? " and target \"" + assertion.get("target") + "\"" : "";
        String message = "datafile not found for environment \"" + environmentName + "\"" + targetName;
        Map<String, Object> details = linkedMap(
            "environment", assertion.get("environment") instanceof String ? assertion.get("environment") : null,
            "target", assertion.get("target") instanceof String ? assertion.get("target") : null
        );
        return new TestResult(true, "      ✘ " + message + "\n", 0,
            Collections.singletonList(new TestError(message, "datafile", Collections.emptyList(),
                "available datafile", null, details)));
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

    private Object getGlobalVariable(Object f, String variableKey, Map<String, Object> context) {
        if (f instanceof Featurevisor) return ((Featurevisor) f).getVariable(variableKey, context);
        if (f instanceof com.featurevisor.sdk.ChildInstance) return ((com.featurevisor.sdk.ChildInstance) f).getVariable(variableKey, context);
        return null;
    }

    private Evaluation evaluateGlobalVariable(Object f, String variableKey, Map<String, Object> context) {
        if (f instanceof Featurevisor) return ((Featurevisor) f).evaluateVariable(variableKey, context);
        if (f instanceof com.featurevisor.sdk.ChildInstance) return ((com.featurevisor.sdk.ChildInstance) f).evaluateVariable(variableKey, context);
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateFlag(Object f, String featureKey, Map<String, Object> context) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateFlag(featureKey, context);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).evaluateFlag(featureKey, context);
        }
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateVariation(Object f, String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateVariation(featureKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).evaluateVariation(featureKey, context, options);
        }
        return null;
    }

    private com.featurevisor.sdk.Evaluation evaluateVariable(Object f, String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        if (f instanceof Featurevisor) {
            return ((Featurevisor) f).evaluateVariable(featureKey, variableKey, context, options);
        } else if (f instanceof com.featurevisor.sdk.ChildInstance) {
            return ((com.featurevisor.sdk.ChildInstance) f).evaluateVariable(featureKey, variableKey, context, options);
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
            case "variableOverrideKey": return evaluation.getVariableOverrideKey();
            case "variableOverridePath": return evaluation.getVariableOverridePath();
            case "variableOverridePaths": return evaluation.getVariableOverridePaths();
            case "bucketKey": return evaluation.getBucketKey();
            case "bucketValue": return evaluation.getBucketValue();
            case "ruleKey": return evaluation.getRuleKey();
            case "forceIndex": return evaluation.getForceIndex();
            case "force": return evaluation.getForce();
            case "stickyFeature": return evaluation.getStickyFeature();
            case "traffic": return evaluation.getTraffic();
            case "conditions": return evaluation.getConditions();
            case "error": return evaluation.getError();
            default: return null;
        }
    }

    private int testReport() {
        coverageSelections.clear();
        Map<String, Object> report = createReport();
        String originalSet = set;
        try {
            Map<String, Object> config = getConfig(rootDirectoryPath);
            List<String> reportSets = resolveReportSets(config);
            if (caseId != null && originalSet == null && Boolean.TRUE.equals(config.get("sets"))) {
                String caseSet = findCaseSet(reportSets);
                if (caseSet == null) throw new IOException("No test case matches --case and the selected filters");
                reportSets = Collections.singletonList(caseSet);
            }
            for (String reportSet : reportSets) {
                set = reportSet;
                runReportSet(report, config, reportSet);
                if (Boolean.TRUE.equals(uncovered)) appendUncovered(report, reportSet);
            }
            if (caseId != null && summaryCount(report, "assertions") == 0) {
                throw new IOException("No test case matches --case and the selected filters");
            }
            if (Boolean.TRUE.equals(coverage)) report.put("coverage", coverageReport());
        } catch (Exception error) {
            report.put("passed", false);
            @SuppressWarnings("unchecked") List<Map<String, Object>> errors = (List<Map<String, Object>>) report.get("errors");
            errors.add(linkedMap("message", error.getMessage() == null ? error.toString() : error.getMessage()));
        } finally {
            set = originalSet;
        }

        try {
            if (Boolean.TRUE.equals(json)) {
                String output = Boolean.TRUE.equals(pretty)
                    ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
                    : objectMapper.writeValueAsString(report);
                System.out.println(output);
            } else {
                printHumanReport(report);
            }
        } catch (Exception error) {
            if (!Boolean.TRUE.equals(json)) System.err.println("Error writing test report: " + error.getMessage());
            return 1;
        }
        return Boolean.TRUE.equals(report.get("passed")) ? 0 : 1;
    }

    private Map<String, Object> createReport() {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String key : Arrays.asList("specs", "assertions", "entitySpecs", "entityAssertions", "scenarioSpecs", "scenarioCases")) {
            summary.put(key, linkedMap("passed", 0, "failed", 0));
        }
        return linkedMap(
            "schemaVersion", 1,
            "runner", "java",
            "passed", true,
            "summary", summary,
            "results", new ArrayList<>(),
            "errors", new ArrayList<>()
        );
    }

    private List<String> resolveReportSets(Map<String, Object> config) throws IOException {
        if (set != null && Boolean.FALSE.equals(config.get("sets"))) {
            throw new IOException("This project does not use Sets. Omit --set.");
        }
        if (set != null) return Collections.singletonList(set);
        if (!Boolean.TRUE.equals(config.get("sets"))) return Collections.singletonList(null);
        String configured = config.get("setsDirectoryPath") instanceof String
            ? (String) config.get("setsDirectoryPath") : "sets";
        File directory = new File(configured);
        if (!directory.isAbsolute()) directory = new File(rootDirectoryPath, configured);
        File[] children = directory.listFiles(child -> java.nio.file.Files.isDirectory(
            child.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        if (children == null || children.length == 0) throw new IOException("No project sets found in " + directory);
        List<String> names = new ArrayList<>();
        for (File child : children) names.add(child.getName());
        Collections.sort(names);
        return names;
    }

    private String findCaseSet(List<String> reportSets) throws IOException {
        for (String reportSet : reportSets) {
            set = reportSet;
            for (Map<String, Object> test : getTests(rootDirectoryPath, false)) {
                String type = test.containsKey("scenario") ? "scenario" : test.containsKey("feature")
                    ? "feature" : test.containsKey("segment") ? "segment" : "variable";
                @SuppressWarnings("unchecked") List<Map<String, Object>> assertions = test.get("assertions") instanceof List
                    ? (List<Map<String, Object>>) test.get("assertions") : Collections.emptyList();
                for (Map<String, Object> assertion : assertions) {
                    @SuppressWarnings("unchecked") Map<String, Object> metadata = assertion.get("_case") instanceof Map
                        ? (Map<String, Object>) assertion.get("_case") : Collections.emptyMap();
                    if (caseId.equals(metadata.get("id")) && assertionMatchesTargets(type, assertion)) return reportSet;
                }
            }
        }
        return null;
    }

    private boolean assertionMatchesTargets(String type, Map<String, Object> assertion) {
        return type.equals("segment") || targets.isEmpty() || assertion.get("target") == null
            || targets.contains(String.valueOf(assertion.get("target")));
    }

    private void runReportSet(Map<String, Object> report, Map<String, Object> config, String reportSet) throws IOException {
        activeSet = reportSet;
        List<Map<String, Object>> tests = getTests(rootDirectoryPath);
        validateNormalizedTests(tests);
        if (caseId != null && !hasExecutableAssertion(tests)) {
            throw new IOException("No test case matches --case and the selected filters");
        }
        List<String> environments = getEnvironmentList(config);
        List<String> availableTargets = getTargets(rootDirectoryPath);
        List<String> selectedTargets = targets.isEmpty()
            ? availableTargets : new ArrayList<>(new LinkedHashSet<>(targets));
        Map<String, Segment> segments = getSegments(rootDirectoryPath);
        Map<String, DatafileContent> datafiles = new HashMap<>();
        datafiles.putAll(buildBaseDatafiles(rootDirectoryPath, environments));
        datafiles.putAll(buildTargetDatafiles(rootDirectoryPath, environments, selectedTargets));

        FeaturevisorLogLevel level = getDiagnosticsLevel();
        for (Map<String, Object> test : tests) {
            String type = test.containsKey("scenario") ? "scenario" : test.containsKey("feature")
                ? "feature" : test.containsKey("segment") ? "segment" : "variable";
            if (entityType != null && !entityType.equals(type)) continue;
            String key = String.valueOf(test.get(type));
            @SuppressWarnings("unchecked") Map<String, Object> metadata = test.get("_test") instanceof Map
                ? (Map<String, Object>) test.get("_test") : Collections.emptyMap();
            Map<String, Object> spec = linkedMap(
                "type", type,
                "key", key,
                "source", metadata.getOrDefault("source", ""),
                "set", metadata.containsKey("set") ? metadata.get("set") : reportSet,
                "passed", true,
                "cases", new ArrayList<>()
            );
            @SuppressWarnings("unchecked") List<Map<String, Object>> assertions = test.get("assertions") instanceof List
                ? (List<Map<String, Object>>) test.get("assertions") : Collections.emptyList();
            int assertionIndex = 0;
            for (Map<String, Object> assertion : assertions) {
                if (!assertionMatchesTargets(type, assertion)) {
                    assertionIndex++;
                    continue;
                }
                activeEnvironment = assertion.get("environment") instanceof String ? (String) assertion.get("environment") : null;
                activeTarget = assertion.get("target") instanceof String ? (String) assertion.get("target") : null;
                TestResult result = executeReportCase(type, key, assertion, datafiles, segments, level,
                    segmentDefinitions(test));
                Map<String, Object> reportCase = reportCase(type, assertion, assertionIndex, reportSet, result);
                @SuppressWarnings("unchecked") List<Map<String, Object>> cases = (List<Map<String, Object>>) spec.get("cases");
                cases.add(reportCase);
                if (result.hasError) spec.put("passed", false);
                assertionIndex++;
            }
            @SuppressWarnings("unchecked") List<Map<String, Object>> cases = (List<Map<String, Object>>) spec.get("cases");
            if (!cases.isEmpty()) appendReportSpec(report, spec);
        }
    }

    private void validateNormalizedTests(List<Map<String, Object>> tests) throws IOException {
        for (Map<String, Object> test : tests) {
            int discriminators = 0;
            for (String candidate : Arrays.asList("feature", "segment", "variable", "scenario")) {
                if (test.containsKey(candidate)) discriminators++;
            }
            if (discriminators != 1) {
                throw new IOException("Test specification requires exactly one of feature, segment, variable, or scenario");
            }
            if (!(test.get("assertions") instanceof List) || ((List<?>) test.get("assertions")).isEmpty()) {
                throw new IOException("Test specification requires assertions");
            }
        }
    }

    private boolean hasExecutableAssertion(List<Map<String, Object>> tests) {
        for (Map<String, Object> test : tests) {
            String type = test.containsKey("scenario") ? "scenario" : test.containsKey("feature")
                ? "feature" : test.containsKey("segment") ? "segment" : "variable";
            @SuppressWarnings("unchecked") List<Map<String, Object>> assertions = test.get("assertions") instanceof List
                ? (List<Map<String, Object>>) test.get("assertions") : Collections.emptyList();
            for (Map<String, Object> assertion : assertions) {
                if (assertionMatchesTargets(type, assertion)) return true;
            }
        }
        return false;
    }

    private TestResult executeReportCase(String type, String key, Map<String, Object> assertion,
            Map<String, DatafileContent> datafiles, Map<String, Segment> segments, FeaturevisorLogLevel level,
            Map<String, Object> segmentDefinitions) throws IOException {
        List<com.featurevisor.sdk.FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        DatafileContent datafile = datafiles.get(selectDatafileKeyForAssertion(assertion, datafiles));
        Map<String, Object> definitions = new LinkedHashMap<>(segmentDefinitions);
        if (type.equals("segment") && !definitions.containsKey(key) && segments.containsKey(key)) {
            definitions.put(key, objectMapper.convertValue(segments.get(key), Map.class));
        }
        if (datafile == null) {
            if (!type.equals("segment") || assertion.containsKey("environment") || assertion.containsKey("target")) {
                return missingDatafileResult(assertion);
            }
            datafile = new DatafileContent("4", "segment-tests");
            datafile.setFeatures(new LinkedHashMap<>()); datafile.setSegments(new LinkedHashMap<>());
        }
        if (type.equals("segment")) {
            String error = SegmentEvaluations.validate(key, datafile, definitions);
            if (error != null) return new TestResult(true, error, 0, List.of(new TestError(
                error, "segment", List.of("expectedToMatch"), "valid segment references", null, null)));
        }
        String ownerVariable = type.equals("variable") ? key : null;
        List<VariableChecks.CheckFailure> preflight = preflightExpandedAssertion(
            assertion, ownerVariable, datafile, segmentDefinitions
        );
        if (!preflight.isEmpty()) {
            StringBuilder messages = new StringBuilder();
            List<TestError> reportErrors = new ArrayList<>();
            for (VariableChecks.CheckFailure failure : preflight) {
                messages.append("      ✘ ").append(failure.message).append("\n");
                Object childIndex = failure.details == null ? null : failure.details.get("childIndex");
                reportErrors.add(checkError(failure, childIndex instanceof Number
                    ? ((Number) childIndex).intValue() : null));
            }
            return new TestResult(true, messages.toString(), 0, reportErrors);
        }
        if (Boolean.TRUE.equals(showDatafile) && !Boolean.TRUE.equals(json)) {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(datafile));
        }
        SegmentEvaluations prepared = new SegmentEvaluations(datafile, definitions);
        segmentHelperKeys = prepared.featureKeys;
        datafile = prepared.datafile;
        Featurevisor sdk = createInstanceForAssertion(datafile, assertion, level, diagnostics);
        try {
            if (type.equals("segment")) {
                Evaluation evaluation = sdk.evaluateFlag(segmentHelperKeys.get(key));
                boolean actual = Boolean.TRUE.equals(evaluation.getEnabled());
                boolean failed = evaluation.getError() != null || getterDiagnosticError(diagnostics) != null
                    || assertion.containsKey("expectedToMatch") && !Objects.equals(assertion.get("expectedToMatch"), actual);
                String message = evaluation.getError() != null ? evaluation.getError().getMessage()
                    : getterDiagnosticError(diagnostics) != null ? getterDiagnosticError(diagnostics)
                    : "expectedToMatch: expected " + assertion.get("expectedToMatch") + " but received " + actual;
                return new TestResult(failed, failed ? message : "", 0, failed ? List.of(new TestError(
                    message, "segment", List.of("expectedToMatch"), assertion.get("expectedToMatch"), actual, null)) : List.of());
            }
            if (type.equals("feature")) return testFeature(assertion, key, sdk, level);
            if (type.equals("variable")) {
                return testGlobalVariable(assertion, key, sdk, datafile, diagnostics, segmentDefinitions);
            }
            return testScenario(assertion, sdk, datafile, diagnostics, segmentDefinitions);
        } finally {
            sdk.close();
        }
    }

    private List<VariableChecks.CheckFailure> preflightExpandedAssertion(
            Map<String, Object> assertion, String ownerVariable, DatafileContent datafile,
            Map<String, Object> segmentDefinitions) {
        List<VariableChecks.CheckFailure> failures = new ArrayList<>();
        if (assertion.containsKey("checks")) {
            failures.addAll(VariableChecks.preflight(assertion.get("checks"), ownerVariable,
                datafile, segmentDefinitions, "checks"));
        }
        if (assertion.get("children") instanceof List) {
            int index = 0;
            for (Object raw : (List<?>) assertion.get("children")) {
                if (raw instanceof Map) {
                    @SuppressWarnings("unchecked") Map<String, Object> child = (Map<String, Object>) raw;
                    if (child.containsKey("checks")) {
                        for (VariableChecks.CheckFailure failure : VariableChecks.preflight(
                                child.get("checks"), ownerVariable, datafile, segmentDefinitions,
                                "children[" + index + "].checks")) {
                            Map<String, Object> details = failure.details == null
                                ? new LinkedHashMap<>() : new LinkedHashMap<>(failure.details);
                            details.put("childIndex", index);
                            failures.add(new VariableChecks.CheckFailure(failure.message, failure.path,
                                failure.expected, failure.actual, details));
                        }
                    }
                }
                index++;
            }
        }
        return failures;
    }

    private Map<String, Object> reportCase(String type, Map<String, Object> assertion, int index,
            String reportSet, TestResult result) {
        @SuppressWarnings("unchecked") Map<String, Object> metadata = assertion.get("_case") instanceof Map
            ? (Map<String, Object>) assertion.get("_case") : Collections.emptyMap();
        String id = metadata.get("id") instanceof String ? (String) metadata.get("id")
            : fallbackCaseId(reportSet, type, index);
        int authoredAssertionIndex = metadata.get("assertionIndex") instanceof Number
            ? ((Number) metadata.get("assertionIndex")).intValue() : index;
        List<String> args = new ArrayList<>();
        args.add("test");
        args.add("--case=" + id);
        if (projectDirectoryPath != null) args.add("--projectDirectoryPath=" + projectDirectoryPath);
        if (reportSet != null) args.add("--set=" + reportSet);
        for (String target : new LinkedHashSet<>(targets)) args.add("--target=" + target);
        @SuppressWarnings("unchecked") Map<String, Object> contextValue = assertion.get("context") instanceof Map
            ? (Map<String, Object>) assertion.get("context") : new LinkedHashMap<>();
        Object matrixValues = metadata.get("matrixValues") instanceof Map
            ? metadata.get("matrixValues") : new LinkedHashMap<>();
        return linkedMap(
            "id", id,
            "description", assertion.get("description"),
            "environment", assertion.get("environment") instanceof String ? assertion.get("environment") : null,
            "target", assertion.get("target") instanceof String ? assertion.get("target") : null,
            "context", contextValue,
            "matrixValues", matrixValues,
            "passed", !result.hasError,
            "errors", structuredErrors(result.reportErrors, type, authoredAssertionIndex),
            "rerun", linkedMap(
                "command", "mvn -q -pl featurevisor-sdk exec:java -Dexec.mainClass=com.featurevisor.cli.CLI",
                "args", args,
                "argumentEncoding", "maven-exec"
            )
        );
    }

    private String fallbackCaseId(String reportSet, String type, int index) {
        try {
            return objectMapper.writeValueAsString(Arrays.asList(reportSet, type, "unknown", index, null));
        } catch (Exception ignored) {
            return type + ":" + index;
        }
    }

    private List<Map<String, Object>> structuredErrors(List<TestError> failures, String type,
            int authoredAssertionIndex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (TestError failure : failures) {
            Map<String, Object> error = linkedMap("message", failure.message);
            if (failure.type != null) error.put("type", failure.type);
            List<Object> prefix = type.equals("scenario")
                ? Collections.emptyList() : Arrays.asList("assertions", authoredAssertionIndex);
            if (failure.path != null && !failure.path.isEmpty()) {
                error.put("path", appendPath(prefix, failure.path.toArray()));
            }
            if (failure.hasExpected) error.put("expected", reportValue(failure.expected));
            if (failure.hasActual) error.put("actual", reportValue(failure.actual));
            if (failure.details != null && !failure.details.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>(failure.details);
                if (!prefix.isEmpty() && details.get("evaluated") instanceof List) {
                    List<Map<String, Object>> evaluated = new ArrayList<>();
                    for (Object raw : (List<?>) details.get("evaluated")) {
                        if (!(raw instanceof Map)) continue;
                        @SuppressWarnings("unchecked") Map<String, Object> leaf = new LinkedHashMap<>((Map<String, Object>) raw);
                        if (leaf.get("path") instanceof List) {
                            leaf.put("path", appendPath(prefix, ((List<?>) leaf.get("path")).toArray()));
                        }
                        evaluated.add(leaf);
                    }
                    details.put("evaluated", evaluated);
                }
                error.put("details", reportValue(details));
            }
            errors.add(error);
        }
        return errors;
    }

    private Object reportValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) return linkedMap("$type", String.valueOf(value));
            return value;
        }
        if (value instanceof Throwable) {
            Throwable error = (Throwable) value;
            return linkedMap("name", error.getClass().getSimpleName(), "message", error.getMessage());
        }
        if (value instanceof Map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), reportValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Iterable) {
            List<Object> normalized = new ArrayList<>();
            for (Object entry : (Iterable<?>) value) normalized.add(reportValue(entry));
            return normalized;
        }
        return String.valueOf(value);
    }

    private void appendReportSpec(Map<String, Object> report, Map<String, Object> spec) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> results = (List<Map<String, Object>>) report.get("results");
        results.add(spec);
        boolean passed = Boolean.TRUE.equals(spec.get("passed"));
        if (!passed) report.put("passed", false);
        incrementSummary(report, "specs", passed);
        incrementSummary(report, spec.get("type").equals("scenario") ? "scenarioSpecs" : "entitySpecs", passed);
        @SuppressWarnings("unchecked") List<Map<String, Object>> cases = (List<Map<String, Object>>) spec.get("cases");
        for (Map<String, Object> reportCase : cases) {
            boolean casePassed = Boolean.TRUE.equals(reportCase.get("passed"));
            incrementSummary(report, "assertions", casePassed);
            incrementSummary(report, spec.get("type").equals("scenario") ? "scenarioCases" : "entityAssertions", casePassed);
        }
    }

    private void incrementSummary(Map<String, Object> report, String key, boolean passed) {
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        @SuppressWarnings("unchecked") Map<String, Object> counts = (Map<String, Object>) summary.get(key);
        String status = passed ? "passed" : "failed";
        counts.put(status, ((Number) counts.get(status)).intValue() + 1);
    }

    private int summaryCount(Map<String, Object> report, String key) {
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        @SuppressWarnings("unchecked") Map<String, Object> counts = (Map<String, Object>) summary.get(key);
        return ((Number) counts.get("passed")).intValue() + ((Number) counts.get("failed")).intValue();
    }

    private void appendUncovered(Map<String, Object> report, String reportSet) throws IOException {
        List<String> features = uncoveredKeys("features");
        List<String> variables = uncoveredKeys("variables");
        @SuppressWarnings("unchecked") List<Map<String, Object>> entries = (List<Map<String, Object>>) report.computeIfAbsent("uncovered", key -> new ArrayList<>());
        entries.add(linkedMap("set", reportSet, "features", features, "variables", variables));
    }

    private List<String> uncoveredKeys(String entity) throws IOException {
        String output = executeCommandInDirectory(rootDirectoryPath,
            "npx featurevisor list --" + entity + " --without-tests --json", selectionArguments());
        JsonNode root = objectMapper.readTree(output);
        List<String> keys = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                if (item.isTextual()) keys.add(item.asText());
                else if (item.has("key")) keys.add(item.get("key").asText());
                else if (item.has("name")) keys.add(item.get("name").asText());
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private Map<String, Object> coverageReport() {
        TreeMap<String, Map<String, Object>> unique = new TreeMap<>();
        for (Map<String, Object> selection : coverageSelections) {
            try {
                unique.put(objectMapper.writeValueAsString(selection), selection);
            } catch (Exception ignored) {
                unique.put(selection.toString(), selection);
            }
        }
        return linkedMap("mode", "observed-selections", "selections", new ArrayList<>(unique.values()));
    }

    private void printHumanReport(Map<String, Object> report) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> results = (List<Map<String, Object>>) report.get("results");
        for (Map<String, Object> spec : results) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> cases = (List<Map<String, Object>>) spec.get("cases");
            boolean failed = !Boolean.TRUE.equals(spec.get("passed"));
            if (Boolean.TRUE.equals(onlyFailures) && !failed) continue;
            String source = spec.get("source") instanceof String && !((String) spec.get("source")).isEmpty()
                ? (String) spec.get("source") : String.valueOf(spec.get("key"));
            System.out.println("\nTesting: " + source);
            System.out.println("  " + spec.get("type") + " \"" + spec.get("key") + "\":");
            for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
                Map<String, Object> reportCase = cases.get(caseIndex);
                if (Boolean.TRUE.equals(onlyFailures) && Boolean.TRUE.equals(reportCase.get("passed"))) continue;
                System.out.println("  " + (Boolean.TRUE.equals(reportCase.get("passed")) ? "✔ " : "✘ ") + humanCaseLabel(reportCase, caseIndex));
                @SuppressWarnings("unchecked") List<Map<String, Object>> errors = (List<Map<String, Object>>) reportCase.get("errors");
                for (Map<String, Object> error : errors) {
                    if ("check".equals(error.get("type")) && error.get("details") instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> details = (Map<String, Object>) error.get("details");
                        String noun = "check";
                        StringBuilder label = new StringBuilder();
                        if (details.get("childIndex") instanceof Number) {
                            label.append("Child ").append(((Number) details.get("childIndex")).intValue() + 1).append(", ");
                        }
                        label.append(noun);
                        if (details.get("checkKey") instanceof String) {
                            label.append(" \"").append(details.get("checkKey")).append("\"");
                        } else if (details.get("checkIndex") instanceof Number) {
                            label.append(" ").append(((Number) details.get("checkIndex")).intValue() + 1);
                        }
                        if (details.get("checkDescription") instanceof String) {
                            label.append(" (").append(details.get("checkDescription")).append(")");
                        }
                        System.out.println("      ✘ " + label + ": " + humanCheckMessage(error.get("message")));
                    } else {
                        System.out.println("      ✘ " + humanExpectationError(error));
                    }
                    if (error.get("path") instanceof List) System.out.println("         at " + humanPath((List<?>) error.get("path")));
                }
                if (!errors.isEmpty()) {
                    @SuppressWarnings("unchecked") Map<String, Object> rerun = (Map<String, Object>) reportCase.get("rerun");
                    @SuppressWarnings("unchecked") List<String> args = (List<String>) rerun.get("args");
                    System.out.println("    Rerun (POSIX shell): " + rerun.get("command") + " "
                        + joinShellArgs(Collections.singletonList(mavenExecArgument(args))));
                }
            }
        }
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        System.out.println();
        for (Map.Entry<String, String> entry : linkedStringMap(
                "entitySpecs", "Entity specs", "entityAssertions", "Entity assertions",
                "scenarioSpecs", "Scenario specs", "scenarioCases", "Scenario cases", "assertions", "Total cases").entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> counts = (Map<String, Object>) summary.get(entry.getKey());
            System.out.println(entry.getValue() + ": " + counts.get("passed") + " passed, " + counts.get("failed") + " failed");
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> rootErrors = (List<Map<String, Object>>) report.get("errors");
        for (Map<String, Object> error : rootErrors) System.err.println("Error running tests: " + error.get("message"));
        if (report.get("uncovered") instanceof List) {
            for (Object raw : (List<?>) report.get("uncovered")) {
                if (!(raw instanceof Map)) continue;
                Map<?, ?> entry = (Map<?, ?>) raw;
                System.out.println("Uncovered" + (entry.get("set") == null ? "" : " (set " + entry.get("set") + ")")
                    + ": features " + entry.get("features") + ", variables " + entry.get("variables"));
            }
        }
        if (report.get("coverage") instanceof Map) {
            Object selections = ((Map<?, ?>) report.get("coverage")).get("selections");
            System.out.println("Observed rule and override selections (not branch coverage):");
            if (!(selections instanceof List) || ((List<?>) selections).isEmpty()) System.out.println("  No selections observed");
            else for (Object raw : (List<?>) selections) {
                if (raw instanceof Map) System.out.println("  " + humanCoverageSelection((Map<?, ?>) raw));
            }
        }
    }

    private String humanCaseLabel(Map<String, Object> reportCase, int index) {
        String description = reportCase.get("description") instanceof String ? (String) reportCase.get("description") : "";
        String label = description.isEmpty() ? "assertion " + (index + 1) : description;
        List<String> details = new ArrayList<>();
        if (reportCase.get("matrixValues") instanceof Map && !((Map<?, ?>) reportCase.get("matrixValues")).isEmpty()) {
            Map<?, ?> values = (Map<?, ?>) reportCase.get("matrixValues");
            List<String> entries = new ArrayList<>();
            values.keySet().stream().map(String::valueOf).sorted().forEach(key -> {
                try { entries.add(key + "=" + objectMapper.writeValueAsString(values.get(key))); }
                catch (Exception ignored) { entries.add(key + "=" + values.get(key)); }
            });
            details.add("matrix " + String.join(", ", entries));
        }
        if (reportCase.get("target") instanceof String) details.add("target " + reportCase.get("target"));
        return details.isEmpty() ? label : label + " (" + String.join("; ", details) + ")";
    }

    private String humanPath(List<?> path) {
        List<String> parts = new ArrayList<>();
        for (Object part : path) parts.add(part instanceof Number ? String.valueOf(((Number) part).intValue() + 1) : String.valueOf(part));
        return String.join(".", parts);
    }

    private String humanExpectationError(Map<String, Object> error) {
        if (!(error.get("path") instanceof List) || !error.containsKey("expected")) return String.valueOf(error.get("message"));
        List<?> path = (List<?>) error.get("path");
        int offset = 0;
        if (path.size() > 1 && "assertions".equals(path.get(0))) offset = 2;
        String prefix = "";
        if (path.size() > offset + 1 && "children".equals(path.get(offset)) && path.get(offset + 1) instanceof Number) {
            prefix = "Child " + (((Number) path.get(offset + 1)).intValue() + 1) + ", ";
            offset += 2;
        }
        if (offset >= path.size()) return String.valueOf(error.get("message"));
        String section = humanPath(path.subList(offset, path.size()));
        return prefix + section + ": expected " + humanValue(error.get("expected"))
            + ", received " + humanValue(error.get("actual"));
    }

    private String humanValue(Object value) {
        if (value instanceof String) return (String) value;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ignored) { return String.valueOf(value); }
    }

    private String humanCheckMessage(Object message) {
        return String.valueOf(message)
            .replaceFirst("^(?:children\\[[0-9]+\\]\\.)?checks\\[[0-9]+\\].*? (?:failed|error):\\s*", "")
            .replaceFirst("^(?:children\\[[0-9]+\\]\\.)?checks\\[[0-9]+\\]:\\s*", "");
    }

    private String humanCoverageSelection(Map<?, ?> selection) {
        String owner = selection.get("featureKey") == null ? "global" : "feature " + selection.get("featureKey");
        if (selection.get("variableKey") != null) owner += ", variable " + selection.get("variableKey");
        if (selection.get("variation") != null) owner += ", variation " + selection.get("variation");
        if (selection.get("ruleKey") != null) owner += ", rule " + selection.get("ruleKey");
        if (selection.get("overridePath") instanceof List) owner += ", override " + humanPath((List<?>) selection.get("overridePath"));
        else if (selection.get("overrideIndex") instanceof Number) owner += ", override " + (((Number) selection.get("overrideIndex")).intValue() + 1);
        return owner;
    }

    private String joinShellArgs(List<String> args) {
        List<String> quoted = new ArrayList<>();
        for (String arg : args) quoted.add("'" + arg.replace("'", "'\\''") + "'");
        return String.join(" ", quoted);
    }

    private String mavenExecArgument(List<String> args) {
        List<String> quoted = new ArrayList<>();
        for (String arg : args) quoted.add("'" + arg.replace("'", "'\"'\"'") + "'");
        return "-Dexec.args=" + String.join(" ", quoted);
    }

    private Map<String, String> linkedStringMap(String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]);
        return map;
    }

    private Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put((String) values[i], values[i + 1]);
        return map;
    }

    /**
     * Run benchmark
     */
    private int benchmark() {
        return benchmark(false);
    }

    private int runRuntimeSelections(java.util.function.IntSupplier evaluate) throws IOException {
        String originalSet = set;
        List<String> originalTargets = targets;
        List<String> selectedSets = resolveReportSets(getConfig(rootDirectoryPath));
        List<String> selectedTargets = targets.isEmpty() ? Collections.singletonList(null)
            : new ArrayList<>(new java.util.LinkedHashSet<>(targets));
        try {
            for (String selectedSet : selectedSets) {
                set = selectedSet;
                for (String target : selectedTargets) {
                    targets = target == null ? Collections.emptyList() : Collections.singletonList(target);
                    if (evaluate.getAsInt() != 0) return 1;
                }
            }
            return 0;
        } finally {
            set = originalSet;
            targets = originalTargets;
        }
    }

    private int benchmark(boolean selectionResolved) {
        try {
            if (n == null || n < 1) {
                System.err.println("Iterations must be a positive integer");
                return 1;
            }

            if (feature == null && variable == null) {
                System.err.println("Feature or variable is required");
                return 1;
            }
            if (variation && (feature == null || variable != null)) {
                System.err.println("Variation requires a feature and cannot be combined with a variable");
                return 1;
            }

            if (!selectionResolved) return runRuntimeSelections(() -> benchmark(true));

            Map<String, Object> contextMap = new HashMap<>();
            if (context != null) {
                contextMap = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            }

            FeaturevisorLogLevel level = getDiagnosticsLevel();
            String target = targets.isEmpty() ? null : targets.get(0);
            DatafileContent datafile = buildDatafile(rootDirectoryPath, environment, target);

            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile)
                .logLevel(level));

            Object value = null;

            System.out.println("Benchmark Featurevisor feature");
            if (feature != null) System.out.println("  Feature: " + feature);
            System.out.println("  Environment: " + (environment == null ? "none" : environment));
            if (set != null) System.out.println("  Set: " + set);
            if (target != null) System.out.println("  Target: " + target);
            System.out.println("  Iterations: " + n);

            if (variation) {
                System.out.println("Benchmarking variation for feature '" + feature + "'...");
            } else if (variable != null && feature != null) {
                System.out.println("Benchmarking variable '" + variable + "' for feature '" + feature + "'...");
            } else if (variable != null) {
                System.out.println("Benchmarking global variable '" + variable + "'...");
            } else {
                System.out.println("Benchmarking flag for feature '" + feature + "'...");
            }

            System.out.println("Against context: " + contextMap);
            System.out.println("Running " + n + " times...");

            long totalDurationNs = 0L;
            long minDurationNs = 0L;
            long maxDurationNs = 0L;
            for (int i = 0; i < n; i++) {
                long evaluationStartTime = System.nanoTime();
                if (variation) {
                    value = f.getVariation(feature, contextMap);
                } else if (variable != null && feature != null) {
                    value = f.getVariable(feature, variable, contextMap);
                } else if (variable != null) {
                    value = f.getVariable(variable, contextMap);
                } else {
                    value = f.isEnabled(feature, contextMap);
                }
                long evaluationDurationNs = System.nanoTime() - evaluationStartTime;

                totalDurationNs += evaluationDurationNs;
                if (i == 0 || evaluationDurationNs < minDurationNs) {
                    minDurationNs = evaluationDurationNs;
                }
                if (evaluationDurationNs > maxDurationNs) {
                    maxDurationNs = evaluationDurationNs;
                }
            }

            double duration = totalDurationNs / 1_000_000.0; // Convert to milliseconds

            System.out.println("Evaluated value: " + value);
            System.out.println("Total duration: " + String.format(Locale.ROOT, "%.3f", duration) + "ms");
            System.out.println("Minimum duration: " + String.format(Locale.ROOT, "%.6f", minDurationNs / 1_000_000.0) + "ms");
            System.out.println("Average duration: " + String.format(Locale.ROOT, "%.6f", duration / n) + "ms");
            System.out.println("Maximum duration: " + String.format(Locale.ROOT, "%.6f", maxDurationNs / 1_000_000.0) + "ms");
            f.close();
            return 0;
        } catch (Exception e) {
            System.err.println("Error running benchmark: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Assess distribution
     */
    private int assessDistribution() {
        return assessDistribution(false);
    }

    private int assessDistribution(boolean selectionResolved) {
        try {
            if (n == null || n < 1) {
                System.err.println("Iterations must be a positive integer");
                return 1;
            }

            if (feature == null) {
                System.err.println("Feature is required");
                return 1;
            }

            if (!selectionResolved) return runRuntimeSelections(() -> assessDistribution(true));

            Map<String, Object> contextMap = new HashMap<>();
            if (context != null) {
                contextMap = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            }

            String target = targets.isEmpty() ? null : targets.get(0);
            DatafileContent datafile = buildDatafile(rootDirectoryPath, environment, target);

            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile)
                .logLevel(getDiagnosticsLevel()));

            Object value = null;

            System.out.println("Assess Featurevisor distribution");
            System.out.println("  Feature: " + feature);
            System.out.println("  Environment: " + (environment == null ? "none" : environment));
            if (set != null) System.out.println("  Set: " + set);
            if (target != null) System.out.println("  Target: " + target);
            System.out.println("  Iterations: " + n);

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
                System.out.println("  - " + val + ": " + count + " (" + String.format(Locale.ROOT, "%.2f", percentage) + "%)");
            }
            f.close();
            return 0;
        } catch (Exception e) {
            System.err.println("Error assessing distribution: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Test result class
     */
    private static class TestResult {
        public final boolean hasError;
        public final String errors;
        public final double duration;
        public final List<TestError> reportErrors;

        public TestResult(boolean hasError, String errors, double duration) {
            this(hasError, errors, duration, new ArrayList<>());
        }

        public TestResult(boolean hasError, String errors, double duration, List<TestError> reportErrors) {
            this.hasError = hasError;
            this.errors = errors;
            this.duration = duration;
            this.reportErrors = reportErrors;
        }
    }

    private static class TestError {
        final String message;
        final String type;
        final List<Object> path;
        final Object expected;
        final Object actual;
        final Map<String, Object> details;
        final boolean hasExpected;
        final boolean hasActual;

        TestError(String message, String type, List<Object> path, Object expected, Object actual,
                Map<String, Object> details) {
            this.message = message;
            this.type = type;
            this.path = path;
            this.expected = expected;
            this.actual = actual;
            this.details = details;
            this.hasExpected = true;
            this.hasActual = true;
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
