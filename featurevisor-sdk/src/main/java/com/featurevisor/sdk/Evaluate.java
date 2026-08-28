package com.featurevisor.sdk;

import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.VariableSchema;
import com.featurevisor.sdk.Force;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Main evaluation logic for Featurevisor SDK
 * Handles the evaluation of features, variations, and variables
 */
final class Evaluate {

    /**
     * Evaluate with modules
     * @param opts The evaluation options
     * @return The evaluation result
     */
    public static Evaluation evaluateWithModules(EvaluateOptions opts) {
        try {
            ModulesManager modulesManager = opts.getModulesManager();

            // run before modules
            EvaluateOptions options = opts;
            if (modulesManager != null) {
                options = modulesManager.executeBeforeModules(options);
            }

            // evaluate
            Evaluation evaluation = evaluate(options);

            // default: variation
            if (options.getDefaultVariationValue() != null &&
                Evaluation.TYPE_VARIATION.equals(evaluation.getType()) &&
                evaluation.getVariationValue() == null && evaluation.getVariation() == null) {
                evaluation.variationValue(options.getDefaultVariationValue());
            }

            // default: variable
            if (options.hasDefaultVariableValue() &&
                Evaluation.TYPE_VARIABLE.equals(evaluation.getType()) &&
                evaluation.getVariableValue() == null) {
                evaluation.variableValue(options.getDefaultVariableValue());
            }

            // run after modules
            if (modulesManager != null) {
                evaluation = modulesManager.executeAfterModules(evaluation, options);
            }

            return evaluation;
        } catch (Exception e) {
            String type = opts.getType();
            String featureKey = opts.getFeatureKey();
            String variableKey = opts.getVariableKey();
            DiagnosticReporter diagnostics = opts.getDiagnostics();

            Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_ERROR)
                .error(e);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("variableKey", variableKey);
            details.put("error", e.getMessage());
            diagnostics.error("error during evaluation", details);

            return evaluation;
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

        try {
            if (options.isGlobalVariable()) {
                return evaluateGlobalVariable(options);
            }
            // root
            Evaluation flag;

            if (!Evaluation.TYPE_FLAG.equals(type)) {
                // needed by variation and variable evaluations
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

            // required (only for flag evaluations)
            if (Evaluation.TYPE_FLAG.equals(type)) {
                if (feature.getRequiredFeatures() != null) {
                    if (!requiredFeaturesAreMatched(feature.getRequiredFeatures(), options)) {
                        return new Evaluation(type, featureKey, variableKey)
                            .reason(Evaluation.REASON_REQUIRED)
                            .requiredFeatures(feature.getRequiredFeatures())
                            .enabled(false);
                    }
                } else {
                Evaluation requiredEvaluation = evaluateRequired(options, feature);
                if (requiredEvaluation != null) {
                    return requiredEvaluation;
                }
                }
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
                return new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_VARIABLE_DEFAULT)
                    .bucketKey(bucketKey)
                    .bucketValue(bucketValue)
                    .variableKey(variableKey)
                    .variableValue(variableSchema.getDefaultValue())
                    .variableSchema(variableSchema);
            }

            // nothing matched
            evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_NO_MATCH)
                .bucketKey(bucketKey)
                .bucketValue(bucketValue)
                .enabled(false);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("bucketKey", bucketKey);
            details.put("bucketValue", bucketValue);
            diagnostics.debug("nothing matched", details);

            return evaluation;
        } catch (Exception e) {
            evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_ERROR)
                .error(e);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("variableKey", variableKey);
            details.put("error", e.getMessage());
            diagnostics.error("error during evaluation", details);

            return evaluation;
        }
    }

    static boolean requiredFeaturesAreMatched(List<Object> requirements, EvaluateOptions options) {
        if (requirements == null || requirements.isEmpty()) { return true; }
        EvaluateOptions clean = options.copy()
            .defaultVariableValue(null, false)
            .defaultVariationValue(null)
            .globalVariable(false);
        clean.setVariableKey(null);
        for (Object item : requirements) {
            String key;
            boolean enabled = true;
            String variation = null;
            if (item instanceof String) {
                key = (String) item;
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> value = (Map<String, Object>) item;
                key = (String) value.get("feature");
                if (value.containsKey("enabled")) { enabled = Boolean.TRUE.equals(value.get("enabled")); }
                variation = (String) value.get("variation");
            } else { return false; }
            if (key == null) { return false; }
            Evaluation flag = evaluateWithModules(clean.copy().type(Evaluation.TYPE_FLAG).featureKey(key));
            if (Boolean.TRUE.equals(flag.getEnabled()) != enabled) { return false; }
            if (variation != null) {
                Evaluation variationEvaluation = evaluateWithModules(clean.copy().type(Evaluation.TYPE_VARIATION).featureKey(key));
                String actualVariation = variationEvaluation.getVariationValue();
                if (actualVariation == null && variationEvaluation.getVariation() != null) {
                    actualVariation = variationEvaluation.getVariation().getValue();
                }
                if (!variation.equals(actualVariation)) { return false; }
            }
        }
        return true;
    }

    static boolean variableOverrideIsMatched(VariableOverride override, EvaluateOptions options) {
        InstanceEvaluationDataProvider data = options.getInstanceEvaluationDataProvider();
        Map<String, Object> context = options.getContext();
        if (override.getConditions() != null && !data.allConditionsAreMatched(data.parseConditionsIfStringified(override.getConditions()), context)) { return false; }
        if (override.getSegments() != null && !data.allSegmentsAreMatched(data.parseSegmentsIfStringified(override.getSegments()), context)) { return false; }
        if (override.getRequiredFeatures() != null && !requiredFeaturesAreMatched(override.getRequiredFeatures(), options)) { return false; }
        return override.getConditions() != null || override.getSegments() != null || override.getRequiredFeatures() != null;
    }

    private static Evaluation evaluateGlobalVariable(EvaluateOptions options) {
        String key = options.getVariableKey();
        if (options.getStickyVariables() != null && options.getStickyVariables().containsKey(key)) {
            return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                .reason(Evaluation.REASON_STICKY).variableValue(options.getStickyVariables().get(key));
        }
        GlobalVariable variable = options.getInstanceEvaluationDataProvider().getGlobalVariable(key);
        if (variable == null) {
            return new Evaluation(Evaluation.TYPE_VARIABLE, null, key).reason(Evaluation.REASON_VARIABLE_NOT_FOUND);
        }
        if (!requiredFeaturesAreMatched(variable.getRequiredFeatures(), options)) {
            Object value = Boolean.TRUE.equals(variable.getUseDefaultWhenDisabled()) ? variable.getDefaultValue() : variable.getDisabledValue();
            return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                .reason(Evaluation.REASON_REQUIRED_FEATURES_UNMET).variable(variable)
                .requiredFeatures(variable.getRequiredFeatures()).variableValue(value);
        }
        if (variable.getOverrides() != null) {
            for (int index = 0; index < variable.getOverrides().size(); index++) {
                VariableOverride override = variable.getOverrides().get(index);
                if (variableOverrideIsMatched(override, options)) {
                    return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
                        .reason(Evaluation.REASON_VARIABLE_OVERRIDE_RULE).variable(variable)
                        .variableValue(override.getValue()).variableOverrideIndex(index)
                        .variableOverrideKey(override.getKey()).variableOverridePath(override.getKeyPath());
                }
            }
        }
        return new Evaluation(Evaluation.TYPE_VARIABLE, null, key)
            .reason(Evaluation.REASON_VARIABLE_DEFAULT).variable(variable).variableValue(variable.getDefaultValue());
    }

    /**
     * Evaluate required features
     * @param options The evaluation options
     * @param feature The feature to evaluate
     * @return The evaluation result if required features are not met, null otherwise
     */
    private static Evaluation evaluateRequired(EvaluateOptions options, Feature feature) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        DiagnosticReporter diagnostics = options.getDiagnostics();
        InstanceEvaluationDataProvider evaluationData = options.getInstanceEvaluationDataProvider();

        // Check if required features are enabled
        List<Object> requiredList = feature.getRequired();
        if (requiredList == null) {
            requiredList = new java.util.ArrayList<>();
        }
        if (requiredList.isEmpty()) {
            return null;
        }

        for (Object required : requiredList) {
            if (required instanceof String) {
                String requiredFeatureKey = (String) required;
                Feature requiredFeature = evaluationData.getFeature(requiredFeatureKey);

                if (requiredFeature == null) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    diagnostics.debug("required feature not found", details);

                    return evaluation;
                }

                // Check if the required feature is enabled
                Evaluation requiredEvaluation = evaluate(options.copy()
                    .type(Evaluation.TYPE_FLAG)
                    .featureKey(requiredFeatureKey));

                if (!Boolean.TRUE.equals(requiredEvaluation.getEnabled())) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    diagnostics.debug("required feature disabled", details);

                    return evaluation;
                }
            } else if (required instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> requiredMap = (Map<String, Object>) required;
                String requiredFeatureKey = (String) requiredMap.get("key");
                String requiredVariation = (String) requiredMap.get("variation");

                if (requiredFeatureKey == null) {
                    continue;
                }

                Feature requiredFeature = evaluationData.getFeature(requiredFeatureKey);

                if (requiredFeature == null) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    diagnostics.debug("required feature not found", details);

                    return evaluation;
                }

                // Check if the required feature has the required variation
                Evaluation requiredEvaluation = evaluate(options.copy()
                    .type(Evaluation.TYPE_VARIATION)
                    .featureKey(requiredFeatureKey));

                Object variationValue = requiredEvaluation.getVariationValue();
                if (variationValue == null && requiredEvaluation.getVariation() != null) {
                    variationValue = requiredEvaluation.getVariation().getValue();
                }

                if (requiredVariation != null && !requiredVariation.equals(variationValue)) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    details.put("requiredVariation", requiredVariation);
                    details.put("actualVariation", variationValue);
                    diagnostics.debug("required feature variation mismatch", details);

                    return evaluation;
                }
            }
        }

        return null;
    }
}
