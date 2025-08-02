package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.Force;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Main evaluation logic for Featurevisor SDK
 * Handles the evaluation of features, variations, and variables
 */
public class Evaluate {

    /**
     * Evaluate with hooks
     * @param opts The evaluation options
     * @return The evaluation result
     */
    public static Evaluation evaluateWithHooks(EvaluateOptions opts) {
        try {
            HooksManager hooksManager = opts.getHooksManager();
            java.util.List<HooksManager.Hook> hooks = hooksManager.getAll();

            // run before hooks
            EvaluateOptions options = opts;
            for (HooksManager.Hook hook : hooksManager.getAll()) {
                if (hook.getBefore() != null) {
                    options = hook.getBefore().apply(options);
                }
            }

            // evaluate
            Evaluation evaluation = evaluate(options);

            // default: variation
            if (options.getDefaultVariationValue() != null &&
                Evaluation.TYPE_VARIATION.equals(evaluation.getType()) &&
                evaluation.getVariationValue() == null) {
                evaluation.variationValue(options.getDefaultVariationValue());
            }

            // default: variable
            if (options.getDefaultVariableValue() != null &&
                Evaluation.TYPE_VARIABLE.equals(evaluation.getType()) &&
                evaluation.getVariableValue() == null) {
                evaluation.variableValue(options.getDefaultVariableValue());
            }

            // run after hooks
            for (HooksManager.Hook hook : hooks) {
                if (hook.getAfter() != null) {
                    evaluation = hook.getAfter().apply(evaluation, options);
                }
            }

            return evaluation;
        } catch (Exception e) {
            String type = opts.getType();
            String featureKey = opts.getFeatureKey();
            String variableKey = opts.getVariableKey();
            Logger logger = opts.getLogger();

            Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_ERROR)
                .error(e);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("variableKey", variableKey);
            details.put("error", e.getMessage());
            logger.error("error during evaluation", details);

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
        Logger logger = options.getLogger();

        Evaluation evaluation;

        try {
            // root
            Evaluation flag;

            if (!Evaluation.TYPE_FLAG.equals(type)) {
                // needed by variation and variable evaluations
                flag = options.getFlagEvaluation() != null ?
                    options.getFlagEvaluation() :
                    evaluate(options.copy().type(Evaluation.TYPE_FLAG));

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
                Evaluation requiredEvaluation = evaluateRequired(options, feature);
                if (requiredEvaluation != null) {
                    return requiredEvaluation;
                }
            }

            // bucket
            EvaluateByBucketing.EvaluateByBucketingResult bucketingResult = EvaluateByBucketing.evaluateByBucketing(options, feature, variableSchema, force);
            String bucketKey = bucketingResult.getBucketKey();
            Integer bucketValue = bucketingResult.getBucketValue();

            if (bucketingResult.getEvaluation() != null) {
                return bucketingResult.getEvaluation();
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
            logger.debug("nothing matched", details);

            return evaluation;
        } catch (Exception e) {
            evaluation = new Evaluation(type, featureKey, variableKey)
                .reason(Evaluation.REASON_ERROR)
                .error(e);

            Map<String, Object> details = new HashMap<>();
            details.put("featureKey", featureKey);
            details.put("variableKey", variableKey);
            details.put("error", e.getMessage());
            logger.error("error during evaluation", details);

            return evaluation;
        }
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
        Logger logger = options.getLogger();
        DatafileReader datafileReader = options.getDatafileReader();

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
                Feature requiredFeature = datafileReader.getFeature(requiredFeatureKey);

                if (requiredFeature == null) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    logger.debug("required feature not found", details);

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
                    logger.debug("required feature disabled", details);

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

                Feature requiredFeature = datafileReader.getFeature(requiredFeatureKey);

                if (requiredFeature == null) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_REQUIRED)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("requiredFeatureKey", requiredFeatureKey);
                    logger.debug("required feature not found", details);

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
                    logger.debug("required feature variation mismatch", details);

                    return evaluation;
                }
            }
        }

        return null;
    }
}
