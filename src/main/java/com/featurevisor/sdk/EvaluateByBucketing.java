package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.Force;
import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableOverride;
import com.featurevisor.types.Traffic;
import com.featurevisor.types.Allocation;
import com.featurevisor.types.Range;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * EvaluateByBucketing for Featurevisor SDK
 * Handles bucketing evaluation logic for feature flags
 */
public class EvaluateByBucketing {

    /**
     * Result of bucketing evaluation
     */
    public static class EvaluateByBucketingResult {
        private Evaluation evaluation;
        private String bucketKey;
        private Integer bucketValue;
        private Traffic matchedTraffic;
        private Allocation matchedAllocation;

        public EvaluateByBucketingResult() {}

        public EvaluateByBucketingResult(Evaluation evaluation, String bucketKey, Integer bucketValue) {
            this.evaluation = evaluation;
            this.bucketKey = bucketKey;
            this.bucketValue = bucketValue;
        }

        // Getters and setters
        public Evaluation getEvaluation() { return evaluation; }
        public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }
        public String getBucketKey() { return bucketKey; }
        public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
        public Integer getBucketValue() { return bucketValue; }
        public void setBucketValue(Integer bucketValue) { this.bucketValue = bucketValue; }
        public Traffic getMatchedTraffic() { return matchedTraffic; }
        public void setMatchedTraffic(Traffic matchedTraffic) { this.matchedTraffic = matchedTraffic; }
        public Allocation getMatchedAllocation() { return matchedAllocation; }
        public void setMatchedAllocation(Allocation matchedAllocation) { this.matchedAllocation = matchedAllocation; }
    }

    /**
     * Evaluate by bucketing
     * @param options The evaluation options
     * @param feature The feature to evaluate
     * @param variableSchema The variable schema (can be null)
     * @param force The force object (can be null)
     * @return The bucketing evaluation result
     */
    public static EvaluateByBucketingResult evaluateByBucketing(
            EvaluateOptions options,
            Feature feature,
            VariableSchema variableSchema,
            Force force) {

        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        Map<String, Object> context = options.getContext();
        Logger logger = options.getLogger();
        HooksManager hooksManager = options.getHooksManager();
        DatafileReader datafileReader = options.getDatafileReader();

        // Get bucket key
        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(feature.getBucketBy())
            .context(context)
            .logger(logger));

        // Apply bucket key hooks
        if (hooksManager != null) {
            bucketKey = hooksManager.executeBucketKeyHooks(new HooksManager.ConfigureBucketKeyOptions(
                featureKey, context, feature.getBucketBy(), bucketKey));
        }

        // Get bucket value
        Integer bucketValue = Bucketer.getBucketedNumber(bucketKey);

        // Apply bucket value hooks
        if (hooksManager != null) {
            bucketValue = hooksManager.executeBucketValueHooks(new HooksManager.ConfigureBucketValueOptions(
                featureKey, bucketKey, context, bucketValue));
        }

        // Get matched traffic and allocation
        Traffic matchedTraffic = null;
        Allocation matchedAllocation = null;

        if (!Evaluation.TYPE_FLAG.equals(type)) {
            matchedTraffic = datafileReader.getMatchedTraffic(feature.getTraffic(), context);
            if (matchedTraffic != null) {
                matchedAllocation = datafileReader.getMatchedAllocation(matchedTraffic, bucketValue);
            }
        } else {
            matchedTraffic = datafileReader.getMatchedTraffic(feature.getTraffic(), context);
        }



        EvaluateByBucketingResult result = new EvaluateByBucketingResult();
        result.setBucketKey(bucketKey);
        result.setBucketValue(bucketValue);
        result.setMatchedTraffic(matchedTraffic);
        result.setMatchedAllocation(matchedAllocation);

        if (matchedTraffic != null) {
            // percentage: 0
            if (matchedTraffic.getPercentage() == 0) {
                Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                    .reason(Evaluation.REASON_RULE)
                    .bucketKey(bucketKey)
                    .bucketValue(bucketValue)
                    .ruleKey(matchedTraffic.getKey())
                    .traffic(convertTrafficToMap(matchedTraffic))
                    .enabled(false);

                Map<String, Object> details = new HashMap<>();
                details.put("featureKey", featureKey);
                details.put("bucketKey", bucketKey);
                details.put("bucketValue", bucketValue);
                logger.debug("matched rule with 0 percentage", details);

                result.setEvaluation(evaluation);
                return result;
            }

            // flag
            if (Evaluation.TYPE_FLAG.equals(type)) {
                // flag: check if mutually exclusive
                if (feature.getRanges() != null && !feature.getRanges().isEmpty()) {
                    // Find matched range
                    boolean matchedRange = false;
                    for (Range range : feature.getRanges()) {
                        if (bucketValue >= range.getStart() && bucketValue < range.getEnd()) {
                            matchedRange = true;
                            break;
                        }
                    }

                    // matched
                    if (matchedRange) {
                        Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                            .reason(Evaluation.REASON_ALLOCATED)
                            .bucketKey(bucketKey)
                            .bucketValue(bucketValue)
                            .ruleKey(matchedTraffic.getKey())
                            .traffic(convertTrafficToMap(matchedTraffic))
                            .enabled(matchedTraffic.getEnabled() == null ? true : matchedTraffic.getEnabled());

                        Map<String, Object> details = new HashMap<>();
                        details.put("featureKey", featureKey);
                        details.put("bucketKey", bucketKey);
                        details.put("bucketValue", bucketValue);
                        logger.debug("matched", details);

                        result.setEvaluation(evaluation);
                        return result;
                    }

                    // no match
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_OUT_OF_RANGE)
                        .bucketKey(bucketKey)
                        .bucketValue(bucketValue)
                        .enabled(false);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("bucketKey", bucketKey);
                    details.put("bucketValue", bucketValue);
                    logger.debug("not matched", details);

                    result.setEvaluation(evaluation);
                    return result;
                }

                // flag: override from rule
                if (matchedTraffic.getEnabled() != null) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_RULE)
                        .bucketKey(bucketKey)
                        .bucketValue(bucketValue)
                        .ruleKey(matchedTraffic.getKey())
                        .traffic(convertTrafficToMap(matchedTraffic))
                        .enabled(matchedTraffic.getEnabled());

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("bucketKey", bucketKey);
                    details.put("bucketValue", bucketValue);
                    logger.debug("override from rule", details);

                    result.setEvaluation(evaluation);
                    return result;
                }

                // treated as enabled because of matched traffic
                if (bucketValue <= matchedTraffic.getPercentage()) {
                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_RULE)
                        .bucketKey(bucketKey)
                        .bucketValue(bucketValue)
                        .ruleKey(matchedTraffic.getKey())
                        .traffic(convertTrafficToMap(matchedTraffic))
                        .enabled(true);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("bucketKey", bucketKey);
                    details.put("bucketValue", bucketValue);
                    logger.debug("matched traffic", details);

                    result.setEvaluation(evaluation);
                    return result;
                }
            }

            // variation
            if (Evaluation.TYPE_VARIATION.equals(type) && feature.getVariations() != null) {
                // override from rule
                if (matchedTraffic.getVariation() != null) {
                    Variation variation = null;
                    for (Variation v : feature.getVariations()) {
                        if (matchedTraffic.getVariation().equals(v.getValue())) {
                            variation = v;
                            break;
                        }
                    }

                    if (variation != null) {
                        Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                            .reason(Evaluation.REASON_RULE)
                            .bucketKey(bucketKey)
                            .bucketValue(bucketValue)
                            .ruleKey(matchedTraffic.getKey())
                            .traffic(convertTrafficToMap(matchedTraffic))
                            .variation(variation);

                        Map<String, Object> details = new HashMap<>();
                        details.put("featureKey", featureKey);
                        details.put("bucketKey", bucketKey);
                        details.put("bucketValue", bucketValue);
                        logger.debug("override from rule", details);

                        result.setEvaluation(evaluation);
                        return result;
                    }
                }

                // regular allocation
                if (matchedAllocation != null && matchedAllocation.getVariation() != null) {
                    Variation variation = null;
                    for (Variation v : feature.getVariations()) {
                        if (matchedAllocation.getVariation().equals(v.getValue())) {
                            variation = v;
                            break;
                        }
                    }

                    if (variation != null) {
                        Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                            .reason(Evaluation.REASON_ALLOCATED)
                            .bucketKey(bucketKey)
                            .bucketValue(bucketValue)
                            .ruleKey(matchedTraffic.getKey())
                            .traffic(convertTrafficToMap(matchedTraffic))
                            .variation(variation);

                        Map<String, Object> details = new HashMap<>();
                        details.put("featureKey", featureKey);
                        details.put("bucketKey", bucketKey);
                        details.put("bucketValue", bucketValue);
                        logger.debug("regular allocation", details);

                        result.setEvaluation(evaluation);
                        return result;
                    }
                }
            }

            // variable
            if (Evaluation.TYPE_VARIABLE.equals(type) && variableKey != null) {
                // override from rule
                if (matchedTraffic.getVariables() != null && matchedTraffic.getVariables().containsKey(variableKey)) {
                    Object variableValue = matchedTraffic.getVariables().get(variableKey);

                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_VARIABLE_OVERRIDE)
                        .bucketKey(bucketKey)
                        .bucketValue(bucketValue)
                        .ruleKey(matchedTraffic.getKey())
                        .traffic(convertTrafficToMap(matchedTraffic))
                        .variableValue(variableValue)
                        .variableSchema(variableSchema);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("variableKey", variableKey);
                    details.put("bucketKey", bucketKey);
                    details.put("bucketValue", bucketValue);
                    logger.debug("variable override from rule", details);

                    result.setEvaluation(evaluation);
                    return result;
                }

                // check variations (force, traffic, allocation)
                String variationValue = null;
                if (force != null && force.getVariation() != null) {
                    variationValue = force.getVariation();
                } else if (matchedTraffic != null && matchedTraffic.getVariation() != null) {
                    variationValue = matchedTraffic.getVariation();
                } else if (matchedAllocation != null && matchedAllocation.getVariation() != null) {
                    variationValue = matchedAllocation.getVariation();
                }

                if (variationValue != null && feature.getVariations() != null) {
                    Variation variation = null;
                    for (Variation v : feature.getVariations()) {
                        if (variationValue.equals(v.getValue())) {
                            variation = v;
                            break;
                        }
                    }



                    // Check for variable overrides first (highest precedence)
                    if (variation != null && variation.getVariableOverrides() != null &&
                        variation.getVariableOverrides().containsKey(variableKey)) {

                        List<VariableOverride> overrides = variation.getVariableOverrides().get(variableKey);
                        for (VariableOverride override : overrides) {
                            boolean matches = false;

                            // Check conditions
                            if (override.getConditions() != null) {
                                matches = datafileReader.allConditionsAreMatched(override.getConditions(), context);
                            }
                            // Check segments
                            else if (override.getSegments() != null) {
                                Object parsedSegments = datafileReader.parseSegmentsIfStringified(override.getSegments());
                                matches = datafileReader.allSegmentsAreMatched(parsedSegments, context);
                            }

                            if (matches) {
                                Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                                    .reason(Evaluation.REASON_VARIABLE_OVERRIDE)
                                    .bucketKey(bucketKey)
                                    .bucketValue(bucketValue)
                                    .ruleKey(matchedTraffic != null ? matchedTraffic.getKey() : null)
                                    .traffic(matchedTraffic != null ? convertTrafficToMap(matchedTraffic) : null)
                                    .variableValue(override.getValue())
                                    .variableSchema(variableSchema);

                                Map<String, Object> details = new HashMap<>();
                                details.put("featureKey", featureKey);
                                details.put("variableKey", variableKey);
                                details.put("bucketKey", bucketKey);
                                details.put("bucketValue", bucketValue);
                                logger.debug("variable override", details);

                                result.setEvaluation(evaluation);
                                return result;
                            }
                        }
                    }

                    // If the variation has the variable, return it
                    if (variation != null && variation.getVariables() != null && variation.getVariables().containsKey(variableKey)) {
                        Object variableValue = variation.getVariables().get(variableKey);

                        Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                            .reason(Evaluation.REASON_ALLOCATED)
                            .bucketKey(bucketKey)
                            .bucketValue(bucketValue)
                            .ruleKey(matchedTraffic != null ? matchedTraffic.getKey() : null)
                            .traffic(matchedTraffic != null ? convertTrafficToMap(matchedTraffic) : null)
                            .variableValue(variableValue)
                            .variableSchema(variableSchema);

                        Map<String, Object> details = new HashMap<>();
                        details.put("featureKey", featureKey);
                        details.put("variableKey", variableKey);
                        details.put("bucketKey", bucketKey);
                        details.put("bucketValue", bucketValue);
                        logger.debug("variable from variation", details);

                        result.setEvaluation(evaluation);
                        return result;
                    }
                }

                // default value
                if (variableSchema != null) {
                    Object variableValue = variableSchema.getDefaultValue();

                    Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                        .reason(Evaluation.REASON_VARIABLE_DEFAULT)
                        .bucketKey(bucketKey)
                        .bucketValue(bucketValue)
                        .ruleKey(matchedTraffic.getKey())
                        .traffic(convertTrafficToMap(matchedTraffic))
                        .variableValue(variableValue)
                        .variableSchema(variableSchema);

                    Map<String, Object> details = new HashMap<>();
                    details.put("featureKey", featureKey);
                    details.put("variableKey", variableKey);
                    details.put("bucketKey", bucketKey);
                    details.put("bucketValue", bucketValue);
                    logger.debug("variable default value", details);

                    result.setEvaluation(evaluation);
                    return result;
                }

                // variable not found
                Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
                    .reason(Evaluation.REASON_VARIABLE_NOT_FOUND)
                    .bucketKey(bucketKey)
                    .bucketValue(bucketValue)
                    .ruleKey(matchedTraffic.getKey())
                    .traffic(convertTrafficToMap(matchedTraffic))
                    .variableSchema(variableSchema);

                Map<String, Object> details = new HashMap<>();
                details.put("featureKey", featureKey);
                details.put("variableKey", variableKey);
                details.put("bucketKey", bucketKey);
                details.put("bucketValue", bucketValue);
                logger.debug("variable not found", details);

                result.setEvaluation(evaluation);
                return result;
            }
        }

        // Nothing matched
        Evaluation evaluation = new Evaluation(type, featureKey, variableKey)
            .reason(Evaluation.REASON_NO_MATCH)
            .bucketKey(bucketKey)
            .bucketValue(bucketValue)
            .enabled(false);

        Map<String, Object> details = new HashMap<>();
        details.put("featureKey", featureKey);
        details.put("bucketKey", bucketKey);
        details.put("bucketValue", bucketValue);
        logger.debug("no matched variation", details);

        result.setEvaluation(evaluation);
        return result;
    }

    /**
     * Convert Traffic object to Map for evaluation
     */
    private static Map<String, Object> convertTrafficToMap(Traffic traffic) {
        Map<String, Object> map = new HashMap<>();
        if (traffic != null) {
            map.put("key", traffic.getKey());
            map.put("segments", traffic.getSegments());
            map.put("percentage", traffic.getPercentage());
            map.put("enabled", traffic.getEnabled());
            map.put("variation", traffic.getVariation());
            map.put("variables", traffic.getVariables());
            map.put("variationWeights", traffic.getVariationWeights());
            map.put("allocation", traffic.getAllocation());
        }
        return map;
    }
}
