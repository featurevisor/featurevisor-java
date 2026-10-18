package com.featurevisor.sdk;

import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.VariableSchema;
import com.featurevisor.sdk.Force;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * Main evaluation logic for Featurevisor SDK
 * Handles the evaluation of features, variations, and variables
 */
final class Evaluate {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * Evaluate with modules
     * @param opts The evaluation options
     * @return The evaluation result
     */
    public static Evaluation evaluateWithModules(EvaluateOptions opts) {
        EvaluationSession session = opts.getSession();
        boolean ownsRoot = session.beginRoot(DependencyOf.from(opts));
        session.retainCallbackOptions(opts);
        try {
            ModulesManager modulesManager = opts.getModulesManager();

            // run before modules
            EvaluateOptions options = opts;
            if (modulesManager != null) {
                for (FeaturevisorModule module : modulesManager.phaseModules()) {
                    if (module.getBeforeEvaluation() != null) {
                        Map<String, Object> previousFeatures = options.getStickyFeatures();
                        Map<String, Object> previousVariables = options.getStickyVariables();
                        EvaluateOptions.StickySource source = options.getStickySource();
                        boolean inheritedFeatures = options.inheritsStickyFeatures();
                        boolean inheritedVariables = options.inheritsStickyVariables();
                        options = module.getBeforeEvaluation().apply(options);
                        session.retainCallbackOptions(options);
                        options.setSession(session);
                        options.setDependencyOf(opts.getDependencyOf());
                        options.setDependencyPurpose(opts.getDependencyPurpose());
                        if (options.getStickySource() == null) options.setStickySource(source, inheritedFeatures, inheritedVariables);
                        if (options.getStickyFeatures() != previousFeatures) options.setInheritedStickyFeatures(false);
                        if (options.getStickyVariables() != previousVariables) options.setInheritedStickyVariables(false);
                    }
                }
            }
            options.refreshSticky();
            session.updateCurrent(DependencyOf.from(options));

            // evaluate
            Evaluation evaluation = evaluate(options);
            session.throwIfFatal();
            if (options.isGlobalVariable() && evaluation.getVariable() != null
                    && Boolean.TRUE.equals(evaluation.getVariable().getDeprecated())
                    && !Evaluation.REASON_STICKY.equals(evaluation.getReason())) {
                Map<String, Object> details = new HashMap<>();
                details.put("code", "deprecated_variable");
                details.put("variableKey", options.getVariableKey());
                details.put("evaluation", evaluation);
                options.getDiagnostics().warn("Variable is deprecated", details);
            }

            // default: variation
            if (options.getDefaultVariationValue() != null &&
                Evaluation.TYPE_VARIATION.equals(evaluation.getType()) &&
                evaluation.getVariationValue() == null && evaluation.getVariation() == null) {
                evaluation.variationValue(options.getDefaultVariationValue());
            }

            // default: variable
            if (options.hasDefaultVariableValue() &&
                Evaluation.TYPE_VARIABLE.equals(evaluation.getType()) &&
                !evaluation.hasVariableValue()) {
                evaluation.variableValue(options.getDefaultVariableValue());
            }

            // run after modules
            if (modulesManager != null) {
                evaluation = modulesManager.executeAfterModules(evaluation, options);
            }
            session.throwIfFatal();

            return evaluation;
        } catch (Exception | StackOverflowError thrown) {
            // A small thread stack fails the evaluation like the logical depth bound,
            // instead of escaping a public getter.
            Exception e = thrown instanceof StackOverflowError
                ? new EvaluationSession.RequirementFailure(new IllegalStateException(
                    "Evaluation dependency depth exceeded the available thread stack"))
                : (Exception) thrown;
            if (e instanceof EvaluationSession.RequirementFailure) {
                session.recordRequirementFailure((EvaluationSession.RequirementFailure) e);
            }
            String type = opts.getType();
            String featureKey = opts.getFeatureKey();
            String variableKey = opts.getVariableKey();
            DiagnosticReporter diagnostics = opts.getDiagnostics();

            Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_ERROR)
                .variableKey(variableKey)
                .error(e);

            Map<String, Object> details = new HashMap<>();
            details.put("code", "evaluation_error");
            details.put("originalError", e);
            details.put("featureKey", featureKey);
            details.put("variableKey", variableKey);
            details.put("reason", Evaluation.REASON_ERROR);
            details.put("evaluation", evaluation);
            // A dependency failure fails the complete public call. Nested getters rethrow
            // it, so only the call that started the session reports it.
            boolean dependencyFailure = e instanceof EvaluationSession.Cycle
                || e instanceof EvaluationSession.RequirementFailure;
            if (ownsRoot || !dependencyFailure) diagnostics.error("Error during evaluation", details);

            return evaluation;
        } finally {
            if (ownsRoot) session.endRoot();
        }
    }

    /**
     * Main evaluation function
     * @param options The evaluation options
     * @return The evaluation result
     */
    public static Evaluation evaluate(EvaluateOptions options) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        DiagnosticReporter diagnostics = options.getDiagnostics();

        Evaluation evaluation;

        if (options.isGlobalVariable()) {
            return evaluateGlobalVariable(options);
        }
            // root
            Evaluation flag;

            if (!Evaluation.TYPE_FLAG.equals(type)) {
                // needed by variation and variable evaluations
                // The flag check is part of the outer getter, not a new requester.
                flag = evaluate(options.copy().type(Evaluation.TYPE_FLAG));

                Evaluation disabledEvaluation = EvaluateDisabled.evaluateDisabled(options, flag);
                if (disabledEvaluation != null) {
                    return disabledEvaluation;
                }
            }

            // sticky
            Evaluation stickyEvaluation = EvaluateSticky.evaluateSticky(options);
            if (stickyEvaluation != null) {
                return stickyEvaluation;
            }

            // not found
            EvaluateNotFound.EvaluateNotFoundResult notFoundResult = EvaluateNotFound.evaluateNotFound(options);

            if (notFoundResult.getEvaluation() != null) {
                return notFoundResult.getEvaluation();
            }

            Feature feature = notFoundResult.getFeature();
            VariableSchema variableSchema = notFoundResult.getVariableSchema();

            // forced
            EvaluateForced.EvaluateForcedResult forcedResult = EvaluateForced.evaluate(options, feature, variableSchema);
            Force force = forcedResult.getForce();

            if (forcedResult.getEvaluation() != null) {
                return forcedResult.getEvaluation();
            }

            if (Evaluation.TYPE_FLAG.equals(type) && feature.hasConditions()
                    && !conditionsAreMatched(feature.getConditions(), options)) {
                return new Evaluation(type, featureKey, variableKey)
                    .reason(Evaluation.REASON_CONDITIONS_UNMET)
                    .conditions(feature.getConditions()).enabled(false);
            }

            // bucket
            EvaluateByBucketing.EvaluateByBucketingResult bucketingResult = EvaluateByBucketing.evaluateByBucketing(options, feature, variableSchema, force);
            String bucketKey = bucketingResult.getBucketKey();
            Integer bucketValue = bucketingResult.getBucketValue();

            if (bucketingResult.getEvaluation() != null) {
                return bucketingResult.getEvaluation();
            }

            if (Evaluation.TYPE_VARIABLE.equals(type) && variableSchema == null && variableKey != null
                    && feature.getVariablesSchema() != null) {
                variableSchema = feature.getVariablesSchema().get(variableKey);
            }
            if (Evaluation.TYPE_VARIABLE.equals(type) && variableSchema != null) {
                Evaluation variableDefaultEvaluation = new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_VARIABLE_DEFAULT)
                    .bucketKey(bucketKey)
                    .bucketValue(bucketValue)
                    .variableKey(variableKey)
                    .variableSchema(variableSchema);
                if (variableSchema.hasDefaultValue()) {
                    variableDefaultEvaluation.variableValue(variableSchema.getDefaultValue());
                }
                return variableDefaultEvaluation;
            }

            // nothing matched
            evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_NO_MATCH)
                .variableKey(variableKey)
                .bucketKey(bucketKey)
                .bucketValue(bucketValue)
                .enabled(false);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("bucketKey", bucketKey);
            details.put("bucketValue", bucketValue);
            diagnostics.debug("nothing matched", details);
        return evaluation;
    }

    private static final class InvalidSelector extends RuntimeException {}

    static boolean conditionsAreMatched(Object expression, EvaluateOptions options) {
        try { return condition(expression, options); }
        catch (InvalidSelector error) { return false; }
    }

    private static boolean attributeOnly(Object expression) {
        if (expression == Normalize.INVALID) return false;
        if (expression instanceof Condition) return true;
        if (expression instanceof List) {
            for (Object child : (List<?>) expression) if (!attributeOnly(child)) return false;
            return true;
        }
        if (!(expression instanceof Map)) return false;
        Map<?, ?> value = (Map<?, ?>) expression;
        if (value.containsKey("variable") || value.containsKey("feature")) return false;
        if (value.containsKey("attribute")) return true;
        for (String group : List.of("and", "or", "not")) {
            if (value.containsKey(group)) return value.get(group) instanceof List && attributeOnly(value.get(group));
        }
        return false;
    }

    private static boolean condition(Object expression, EvaluateOptions options) {
        if (expression == Normalize.INVALID) throw new InvalidSelector();
        if ("*".equals(expression)) return true;
        if (expression instanceof String) {
            String key = (String) expression;
            Segment segment = options.getCurrentDatafile().getSegment(key);
            if (segment == null) return false;
            if (segment.getConditions() == Normalize.INVALID) throw new InvalidSelector();
            if (attributeOnly(segment.getConditions())) return condition(segment.getConditions(), options);
            return options.getSession().segment(key, () -> condition(segment.getConditions(), options));
        }
        if (expression instanceof List) {
            List<?> entries = (List<?>) expression;
            if (entries.isEmpty()) return false;
            for (Object entry : entries) if (!condition(entry, options)) return false;
            return true;
        }
        if (expression instanceof Condition) {
            return options.getInstanceEvaluationDataProvider().allConditionsAreMatched(expression, options.getContext());
        }
        if (!(expression instanceof Map)) return false;
        Map<?, ?> value = (Map<?, ?>) expression;
        if (value.containsKey("variable")) {
            try { return variableCondition(value, options); }
            catch (EvaluationSession.Cycle | EvaluationSession.RequirementFailure error) { throw error; }
            catch (RuntimeException error) { throw new EvaluationSession.RequirementFailure(error); }
        }
        if (value.containsKey("feature")) {
            if (!(value.get("feature") instanceof String)) return false;
            EvaluateOptions clean = options.copy().featureKey((String) value.get("feature"))
                .globalVariable(false).variableKey(null).defaultVariationValue(null)
                .defaultVariableValue(null, false);
            boolean enabled = !value.containsKey("enabled") || Boolean.TRUE.equals(value.get("enabled"));
            if (Boolean.TRUE.equals(readDependency(clean.type(Evaluation.TYPE_FLAG))) != enabled) return false;
            return !value.containsKey("variation")
                || java.util.Objects.equals(value.get("variation"), readDependency(clean.copy().type(Evaluation.TYPE_VARIATION)));
        }
        if (value.containsKey("attribute")) {
            return options.getInstanceEvaluationDataProvider().allConditionsAreMatched(expression, options.getContext());
        }
        for (String group : List.of("and", "or", "not")) {
            if (!value.containsKey(group)) continue;
            if (!(value.get(group) instanceof List) || ((List<?>) value.get(group)).isEmpty()) return false;
            boolean result = !"or".equals(group);
            for (Object child : (List<?>) value.get(group)) {
                boolean matched = condition(child, options);
                if ("or".equals(group) && matched) return true;
                if (!"or".equals(group) && !matched) { result = false; break; }
            }
            return "not".equals(group) ? !result : result;
        }
        return false;
    }

    private static boolean variableCondition(Object required, EvaluateOptions options) {
        if (!(required instanceof Map)) return false;
        Map<?, ?> map = (Map<?, ?>) required;
        Object variableValue = map.get("variable");
        if (!(variableValue instanceof String)) return false;
        String variableKey = (String) variableValue;
        Object featureValue = map.get("feature");
        Object value;
        EvaluateOptions clean = options.copy()
            .defaultVariableValue(null, false)
            .defaultVariationValue(null)
            .type(Evaluation.TYPE_VARIABLE)
            .variableKey(variableKey);
        if (featureValue instanceof String) {
            value = readDependency(clean.featureKey((String) featureValue).globalVariable(false));
        } else {
            value = readDependency(clean.featureKey(null).globalVariable(true));
        }
        boolean present = true;
        if (map.containsKey("path")) {
            Object pathValue = map.get("path");
            if (!(pathValue instanceof List)) throw new IllegalArgumentException("Variable requirement path must be compiled");
            for (Object part : (List<?>) pathValue) {
                if (!(part instanceof String) && !(part instanceof Number)) {
                    throw new IllegalArgumentException("Variable requirement path must be compiled");
                }
                if (part instanceof String && List.of("__proto__", "prototype", "constructor").contains(part)) {
                    throw new IllegalArgumentException("Unsafe variable requirement path");
                }
                if (part instanceof Number) {
                    double numeric = ((Number) part).doubleValue();
                    if (!(value instanceof List) || numeric < 0 || numeric != Math.rint(numeric)
                            || numeric > Integer.MAX_VALUE || (int) numeric >= ((List<?>) value).size()) {
                        present = false;
                    } else {
                        value = ((List<?>) value).get((int) numeric);
                    }
                } else {
                    String key = (String) part;
                    if (value instanceof List) {
                        if ("length".equals(key)) {
                            value = ((List<?>) value).size();
                        } else {
                            Integer index = arrayPropertyIndex(key);
                            if (index == null || index >= ((List<?>) value).size()) present = false;
                            else value = ((List<?>) value).get(index);
                        }
                    } else if (!(value instanceof Map) || !((Map<?, ?>) value).containsKey(key)) {
                        present = false;
                    } else {
                        value = ((Map<?, ?>) value).get(key);
                    }
                }
                if (!present) { value = null; break; }
            }
        }
        Object operatorValue = map.get("operator");
        if (!(operatorValue instanceof String)) return false;
        Operator operator = Operator.fromValue((String) operatorValue);
        String regexFlags = map.get("regexFlags") instanceof String ? (String) map.get("regexFlags") : "";
        return Conditions.valueIsMatched(value, present, operator, map.get("value"), regexFlags,
            (pattern, flags) -> options.getInstanceEvaluationDataProvider().getRegex(pattern, flags));
    }

    private static Integer arrayPropertyIndex(String key) {
        if (key.isEmpty() || (key.length() > 1 && key.charAt(0) == '0')) return null;
        for (int index = 0; index < key.length(); index++) {
            if (key.charAt(index) < '0' || key.charAt(index) > '9') return null;
        }
        try {
            long value = Long.parseLong(key);
            return value <= Integer.MAX_VALUE ? (int) value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Object readDependency(EvaluateOptions requested) {
        return readDependency(requested, "condition");
    }

    private static Object readDependency(EvaluateOptions requested, String purpose) {
        requested.setDependencyPurpose(purpose);
        // Keep the active getter's snapshot, but refresh each new dependency read.
        requested.refreshDatafile();
        requested.refreshSticky();
        EvaluationSession session = requested.getSession();
        DependencyOf parent = session.current();
        return session.read(requested, () -> {
            requested.setDependencyOf(parent);
            Evaluation evaluation = evaluateWithModules(requested);
            switch (requested.getType()) {
                case Evaluation.TYPE_FLAG: return Boolean.TRUE.equals(evaluation.getEnabled());
                case Evaluation.TYPE_VARIATION:
                    return evaluation.getVariationValue() != null ? evaluation.getVariationValue()
                        : evaluation.getVariation() != null ? evaluation.getVariation().getValue() : null;
                case Evaluation.TYPE_VARIABLE: return normalizeGetterValue(evaluation, requested.getDiagnostics());
                default: return null;
            }
        });
    }

    static Object normalizeGetterValue(Evaluation evaluation, DiagnosticReporter diagnostics) {
        Object value = evaluation.getVariableValue();
        boolean json = evaluation.getVariableSchema() != null
            && evaluation.getVariableSchema().getType() == VariableType.JSON;
        if (!json && evaluation.getVariable() != null) json = evaluation.getVariable().getType() == VariableType.JSON;
        if (json && value instanceof String && (evaluation.getVariable() == null || evaluation.getVariable().getCompose() == null)) {
            try { return OBJECT_MAPPER.readValue((String) value, Object.class); }
            catch (Exception error) {
                Map<String, Object> details = new HashMap<>();
                details.put("code", "evaluation_error");
                details.put("originalError", error);
                details.put("variableKey", evaluation.getVariableKey());
                if (evaluation.getFeatureKey() != null) details.put("featureKey", evaluation.getFeatureKey());
                diagnostics.error("getVariable failed", details);
                return null;
            }
        }
        return value;
    }

    static boolean variableOverrideIsMatched(VariableOverride override, EvaluateOptions options) {
        return conditionsAreMatched(override.getConditions(), options);
    }

    private static Evaluation evaluateGlobalVariable(EvaluateOptions options) {
        String key = options.getVariableKey();
        GlobalVariable variable = options.getInstanceEvaluationDataProvider().getGlobalVariable(key);
        if (options.getStickyVariables() != null && options.getStickyVariables().containsKey(key)) {
            return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                .reason(Evaluation.REASON_STICKY).variableKey(key)
                .variableValue(options.getStickyVariables().get(key)).variable(variable);
        }
        if (variable == null) {
            return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                .reason(Evaluation.REASON_VARIABLE_NOT_FOUND).variableKey(key);
        }
        boolean composed = variable.getCompose() != null;
        if (variable.hasConditions() && !conditionsAreMatched(variable.getConditions(), options)) {
            boolean useDefault = !composed && Boolean.TRUE.equals(variable.getUseDefaultWhenDisabled());
            Evaluation evaluation = new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                .reason(Evaluation.REASON_CONDITIONS_UNMET).conditions(variable.getConditions())
                .variableKey(key).variable(variable);
            if (useDefault ? variable.hasDefaultValue() : variable.hasDisabledValue()) {
                evaluation.variableValue(useDefault ? variable.getDefaultValue() : variable.getDisabledValue());
            } else if (composed) evaluation.variableValue(null);
            return freshMap(evaluation, variable);
        }
        Object initial = composed ? compose(variable.getCompose(), options) : variable.getDefaultValue();
        List<VariableOverride> program = variable.getOverrideProgram();
        List<VariableOverride> overrides = program != null ? program : variable.getOverrides();
        if (overrides != null) {
            Object accumulated = initial;
            List<List<String>> appliedPaths = program != null ? new ArrayList<>() : null;
            Evaluation matchedEvaluation = null;
            for (int index = 0; index < overrides.size();) {
                VariableOverride override = overrides.get(index);
                if (variableOverrideIsMatched(override, options)) {
                    if (program != null) {
                        accumulated = VariableMutations.apply(accumulated, override.getOperations());
                        appliedPaths.add(override.getKeyPath() != null ? override.getKeyPath() : List.of(override.getKey()));
                    }
                    matchedEvaluation = new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                        .reason(Evaluation.REASON_VARIABLE_OVERRIDE_RULE).variableKey(key).variable(variable)
                        .variableValue(program != null ? accumulated : override.getValue()).variableOverrideIndex(index)
                        .variableOverrideKey(override.getKey()).variableOverridePath(override.getKeyPath());
                    if (program != null) matchedEvaluation.variableOverridePaths(new ArrayList<>(appliedPaths));
                    if (program == null) return freshMap(matchedEvaluation, variable);
                }
                boolean matched = matchedEvaluation != null && matchedEvaluation.getVariableOverrideIndex() == index;
                int next = program == null ? index + 1
                    : matched ? valueOrInvalid(override.getOnMatch()) : valueOrInvalid(override.getOnMiss());
                if (program != null && (next <= index || next > overrides.size())) {
                    throw new IllegalArgumentException("Invalid variable override program jump");
                }
                index = next;
            }
            if (matchedEvaluation != null) return freshMap(matchedEvaluation, variable);
        }
        Evaluation evaluation = new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
            .reason(Evaluation.REASON_VARIABLE_DEFAULT).variableKey(key).variable(variable);
        if (composed) evaluation.reason(Evaluation.REASON_VARIABLE_COMPOSED).variableValue(initial);
        else if (variable.hasDefaultValue()) evaluation.variableValue(variable.getDefaultValue());
        return evaluation;
    }

    private static Evaluation freshMap(Evaluation result, GlobalVariable variable) {
        if (variable.getCompose() instanceof List && result.getVariableValue() instanceof Map) {
            result.variableValue(new java.util.LinkedHashMap<>((Map<?, ?>) result.getVariableValue()));
        }
        return result;
    }

    private static Object compose(Object expression, EvaluateOptions options) {
        EvaluateOptions clean = options.copy().featureKey(null).variableKey(null)
            .defaultVariableValue(null, false).defaultVariationValue(null);
        if (expression instanceof String && !((String) expression).isEmpty()) {
            return readDependency(clean.type(Evaluation.TYPE_VARIABLE).variableKey((String) expression).globalVariable(true), "composition");
        }
        if (!(expression instanceof List) || ((List<?>) expression).isEmpty()) {
            throw new IllegalArgumentException("Invalid composition");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Object member : (List<?>) expression) {
            if (!(member instanceof Map)) throw new IllegalArgumentException("Invalid composition reference");
            Map<?, ?> entry = (Map<?, ?>) member;
            Object key = entry.get("key");
            if (!(key instanceof String) || ((String) key).isEmpty()
                    || List.of("__proto__", "constructor", "prototype").contains(key)) {
                throw new IllegalArgumentException("Invalid composition property");
            }
            EvaluateOptions source = clean.copy();
            if (entry.get("variable") instanceof String) {
                source.type(Evaluation.TYPE_VARIABLE).variableKey((String) entry.get("variable"));
                source.globalVariable(!(entry.get("feature") instanceof String));
                source.featureKey((String) entry.get("feature"));
            } else if (entry.get("feature") instanceof String) {
                source.featureKey((String) entry.get("feature")).globalVariable(false)
                    .type(Boolean.TRUE.equals(entry.get("variation")) ? Evaluation.TYPE_VARIATION : Evaluation.TYPE_FLAG);
            } else throw new IllegalArgumentException("Invalid composition reference");
            result.put((String) key, readDependency(source, "composition"));
        }
        return result;
    }

    private static int valueOrInvalid(Object value) {
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) return -1;
        return (int) number;
    }
}
