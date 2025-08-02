package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Force;
import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.VariableType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class EvaluateForcedTest {

    private Logger logger;
    private DatafileReader datafileReader;
    private Feature feature;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));

        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2.0");
        datafile.setRevision("1");
        datafile.setSegments(new HashMap<>());
        datafile.setFeatures(new HashMap<>());

        datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafile)
            .logger(logger));

        // Create a test feature with variations
        feature = new Feature("test-feature");

        List<Variation> variations = new ArrayList<>();
        Variation variation1 = new Variation("control");
        variation1.setDescription("Control variation");
        variation1.setWeight(50);
        variations.add(variation1);

        Variation variation2 = new Variation("treatment");
        variation2.setDescription("Treatment variation");
        variation2.setWeight(50);
        variations.add(variation2);

        feature.setVariations(variations);

        // Create variable schema
        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        variablesSchema.put("test-variable", variableSchema);
        feature.setVariablesSchema(variablesSchema);

        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", feature);
        datafile.setFeatures(features);
    }

    @Test
    public void testEvaluateForcedWithNoForce() {
        // Should return result with no evaluation when no force is matched
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNull(result.getForce());
        assertNull(result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithFlagType() {
        // Create a force with enabled flag
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setEnabled(true);
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_FLAG, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_FORCED, result.getEvaluation().getReason());
        assertEquals(0, result.getEvaluation().getForceIndex());
        assertNotNull(result.getEvaluation().getForce());
        assertTrue(result.getEvaluation().getEnabled());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariationType() {
        // Create a force with variation
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setVariation("treatment");
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIATION, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_FORCED, result.getEvaluation().getReason());
        assertEquals(0, result.getEvaluation().getForceIndex());
        assertNotNull(result.getEvaluation().getForce());
        assertNotNull(result.getEvaluation().getVariation());
        assertEquals("treatment", result.getEvaluation().getVariation().getValue());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariationTypeNotFound() {
        // Create a force with non-existent variation
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setVariation("non-existent");
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariableType() {
        // Create a force with variables
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        Map<String, Object> variables = new HashMap<>();
        variables.put("test-variable", "forced-value");
        force.setVariables(variables);
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        VariableSchema variableSchema = feature.getVariablesSchema().get("test-variable");

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, variableSchema);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIABLE, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_FORCED, result.getEvaluation().getReason());
        assertEquals(0, result.getEvaluation().getForceIndex());
        assertNotNull(result.getEvaluation().getForce());
        assertEquals("test-variable", result.getEvaluation().getVariableKey());
        assertEquals("forced-value", result.getEvaluation().getVariableValue());
        assertNotNull(result.getEvaluation().getVariableSchema());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariableTypeNotFound() {
        // Create a force with variables but request different variable
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        Map<String, Object> variables = new HashMap<>();
        variables.put("other-variable", "forced-value");
        force.setVariables(variables);
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        VariableSchema variableSchema = feature.getVariablesSchema().get("test-variable");

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, variableSchema);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithFlagTypeNoEnabled() {
        // Create a force without enabled flag
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setConditions("*"); // Match all
        // No enabled set
        forces.add(force);
        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariationTypeNoVariations() {
        // Create a force with variation but feature has no variations
        feature.setVariations(null);

        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setVariation("treatment");
        force.setConditions("*"); // Match all
        forces.add(force);
        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithVariableTypeNoVariables() {
        // Create a force without variables
        List<Force> forces = new ArrayList<>();
        Force force = new Force();
        force.setConditions("*"); // Match all
        // No variables set
        forces.add(force);
        feature.setForce(forces);

        VariableSchema variableSchema = feature.getVariablesSchema().get("test-variable");

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, variableSchema);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getForce());
        assertEquals(0, result.getForceIndex());
    }

    @Test
    public void testEvaluateForcedWithMultipleForces() {
        // Create multiple forces, second one should match
        List<Force> forces = new ArrayList<>();

        Force force1 = new Force();
        force1.setEnabled(false);
        force1.setConditions("browser_type:equals:firefox"); // Won't match
        forces.add(force1);

        Force force2 = new Force();
        force2.setEnabled(true);
        force2.setConditions("*"); // Will match
        forces.add(force2);

        feature.setForce(forces);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .context(Map.of("browser_type", "chrome"))
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateForced.EvaluateForcedResult result = EvaluateForced.evaluate(options, feature, null);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_FLAG, result.getEvaluation().getType());
        assertEquals(Evaluation.REASON_FORCED, result.getEvaluation().getReason());
        assertEquals(1, result.getEvaluation().getForceIndex());
        assertTrue(result.getEvaluation().getEnabled());
        assertNotNull(result.getForce());
        assertEquals(1, result.getForceIndex());
    }
}
