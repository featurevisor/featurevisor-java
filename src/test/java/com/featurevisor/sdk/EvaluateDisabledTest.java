package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.VariableType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

public class EvaluateDisabledTest {

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

        // Create a test feature
        feature = new Feature("test-feature");
        feature.setDisabledVariationValue("disabled-variation");

        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        variableSchema.setDisabledValue("disabled-value");
        variableSchema.setUseDefaultWhenDisabled(true);
        variablesSchema.put("test-variable", variableSchema);
        feature.setVariablesSchema(variablesSchema);

        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", feature);
        datafile.setFeatures(features);
    }

    @Test
    public void testEvaluateDisabledWithFlagType() {
        // Should return null for flag type
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(false);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);
        assertNull(result);
    }

    @Test
    public void testEvaluateDisabledWithEnabledFlag() {
        // Should return null when flag is enabled
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(true);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);
        assertNull(result);
    }

    @Test
    public void testEvaluateDisabledWithVariationType() {
        // Create a fresh feature for this test
        Feature testFeature = new Feature("test-feature");
        testFeature.setDisabledVariationValue("disabled-variation");

        // Create a new datafile with the updated feature
        DatafileContent newDatafile = new DatafileContent();
        newDatafile.setSchemaVersion("2.0");
        newDatafile.setRevision("1");
        newDatafile.setSegments(new HashMap<>());
        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", testFeature);
        newDatafile.setFeatures(features);

        // Create a new DatafileReader with the updated datafile
        DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(newDatafile)
            .logger(logger));

        // Should return disabled variation evaluation
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(newDatafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(false);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIATION, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_VARIATION_DISABLED, result.getReason());
        assertEquals("disabled-variation", result.getVariationValue());
        assertFalse(Boolean.TRUE.equals(result.getEnabled()));
    }

    @Test
    public void testEvaluateDisabledWithVariableTypeAndDisabledValue() {
        // Create a fresh feature for this test
        Feature testFeature = new Feature("test-feature");
        testFeature.setDisabledVariationValue("disabled-variation");

        // Create a variable schema with disabledValue
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        variableSchema.setDisabledValue("disabled-value");
        variableSchema.setUseDefaultWhenDisabled(false);

        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        variablesSchema.put("test-variable", variableSchema);
        testFeature.setVariablesSchema(variablesSchema);

        // Create a new datafile with the updated feature
        DatafileContent newDatafile = new DatafileContent();
        newDatafile.setSchemaVersion("2.0");
        newDatafile.setRevision("1");
        newDatafile.setSegments(new HashMap<>());
        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", testFeature);
        newDatafile.setFeatures(features);

        // Create a new DatafileReader with the updated datafile
        DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(newDatafile)
            .logger(logger));

        // Should return variable disabled evaluation with disabledValue
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(newDatafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(false);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIABLE, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_VARIABLE_DISABLED, result.getReason());
        assertEquals("test-variable", result.getVariableKey());
        assertEquals("disabled-value", result.getVariableValue());
        assertNotNull(result.getVariableSchema());
        assertFalse(Boolean.TRUE.equals(result.getEnabled()));
    }

    @Test
    public void testEvaluateDisabledWithVariableTypeAndUseDefaultWhenDisabled() {
        // Create a fresh feature for this test
        Feature testFeature = new Feature("test-feature");
        testFeature.setDisabledVariationValue("disabled-variation");

        // Create a variable schema without disabledValue but with useDefaultWhenDisabled
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        variableSchema.setUseDefaultWhenDisabled(true);
        variableSchema.setDisabledValue(null); // Explicitly set to null to test useDefaultWhenDisabled

        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        variablesSchema.put("test-variable", variableSchema);
        testFeature.setVariablesSchema(variablesSchema);

        // Create a new datafile with the updated feature
        DatafileContent newDatafile = new DatafileContent();
        newDatafile.setSchemaVersion("2.0");
        newDatafile.setRevision("1");
        newDatafile.setSegments(new HashMap<>());
        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", testFeature);
        newDatafile.setFeatures(features);

        // Create a new DatafileReader with the updated datafile
        DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(newDatafile)
            .logger(logger));

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(newDatafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(false);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIABLE, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, result.getReason());
        assertEquals("test-variable", result.getVariableKey());
        assertEquals("default-value", result.getVariableValue());
        assertNotNull(result.getVariableSchema());
        assertFalse(Boolean.TRUE.equals(result.getEnabled()));
    }

    @Test
    public void testEvaluateDisabledWithVariableTypeAndNoSpecialHandling() {
        // Create a fresh feature for this test
        Feature testFeature = new Feature("test-feature");
        testFeature.setDisabledVariationValue("disabled-variation");

        // Create a variable schema without disabledValue or useDefaultWhenDisabled
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        // No disabledValue or useDefaultWhenDisabled set

        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        variablesSchema.put("test-variable", variableSchema);
        testFeature.setVariablesSchema(variablesSchema);

        // Create a new datafile with the updated feature
        DatafileContent newDatafile = new DatafileContent();
        newDatafile.setSchemaVersion("2.0");
        newDatafile.setRevision("1");
        newDatafile.setSegments(new HashMap<>());
        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", testFeature);
        newDatafile.setFeatures(features);

        // Create a new DatafileReader with the updated datafile
        DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(newDatafile)
            .logger(logger));

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(newDatafileReader)
            .logger(logger);

        Evaluation flag = new Evaluation()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .enabled(false);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, flag);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIABLE, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_DISABLED, result.getReason());
        assertFalse(Boolean.TRUE.equals(result.getEnabled()));
    }

    @Test
    public void testEvaluateDisabledWithNullFlag() {
        // Should return null when flag is null
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        Evaluation result = EvaluateDisabled.evaluateDisabled(options, null);
        assertNull(result);
    }
}
