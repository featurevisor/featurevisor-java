package com.featurevisor.sdk;

import com.featurevisor.types.Bucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

public class BucketerTest {

    private Logger logger;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));
    }

    @Test
    public void testGetBucketedNumberIsFunction() throws Exception {
        // This test verifies that getBucketedNumber is a static method
        assertNotNull(Bucketer.class.getDeclaredMethod("getBucketedNumber", String.class));
    }

    @Test
    public void testGetBucketedNumberReturnsNumberBetween0And100000() {
        String[] keys = {"foo", "bar", "baz", "123adshlk348-93asdlk"};

        for (String key : keys) {
            int n = Bucketer.getBucketedNumber(key);

            assertTrue(n >= 0, "Bucket number should be >= 0 for key: " + key);
            assertTrue(n <= Bucketer.MAX_BUCKETED_NUMBER, "Bucket number should be <= " + Bucketer.MAX_BUCKETED_NUMBER + " for key: " + key);
        }
    }

    @Test
    public void testGetBucketedNumberReturnsExpectedNumberForKnownKeys() {
        // These assertions will be copied to unit tests of SDKs ported to other languages,
        // so we can keep consistent bucketing across all SDKs
        Map<String, Integer> expectedResults = new HashMap<>();
        expectedResults.put("foo", 20602);
        expectedResults.put("bar", 89144);
        expectedResults.put("123.foo", 3151);
        expectedResults.put("123.bar", 9710);
        expectedResults.put("123.456.foo", 14432);
        expectedResults.put("123.456.bar", 1982);

        for (Map.Entry<String, Integer> entry : expectedResults.entrySet()) {
            String key = entry.getKey();
            Integer expected = entry.getValue();
            int n = Bucketer.getBucketedNumber(key);

            assertEquals(expected, n, "Expected bucket number for key: " + key);
        }
    }

    @Test
    public void testGetBucketKeyIsFunction() throws Exception {
        // This test verifies that getBucketKey is a static method
        assertNotNull(Bucketer.class.getDeclaredMethod("getBucketKey", Bucketer.GetBucketKeyOptions.class));
    }

    @Test
    public void testPlainBucketByReturnsBucketKey() {
        String featureKey = "test-feature";
        Bucket bucketBy = new Bucket("userId");
        Map<String, Object> context = new HashMap<>();
        context.put("userId", "123");
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("123.test-feature", bucketKey);
    }

    @Test
    public void testPlainBucketByReturnsFeatureKeyOnlyIfValueMissingInContext() {
        String featureKey = "test-feature";
        Bucket bucketBy = new Bucket("userId");
        Map<String, Object> context = new HashMap<>();
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("test-feature", bucketKey);
    }

    @Test
    public void testAndBucketByCombinesMultipleFieldValuesTogetherIfPresent() {
        String featureKey = "test-feature";
        List<String> bucketByList = Arrays.asList("organizationId", "userId");
        Bucket bucketBy = new Bucket(bucketByList, true);
        Map<String, Object> context = new HashMap<>();
        context.put("organizationId", "123");
        context.put("userId", "234");
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("123.234.test-feature", bucketKey);
    }

    @Test
    public void testAndBucketByCombinesOnlyAvailableFieldValuesTogetherIfPresent() {
        String featureKey = "test-feature";
        List<String> bucketByList = Arrays.asList("organizationId", "userId");
        Bucket bucketBy = new Bucket(bucketByList, true);
        Map<String, Object> context = new HashMap<>();
        context.put("organizationId", "123");
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("123.test-feature", bucketKey);
    }

    @Test
    public void testAndBucketByCombinesAllAvailableFieldsWithDotSeparatedPaths() {
        String featureKey = "test-feature";
        List<String> bucketByList = Arrays.asList("organizationId", "user.id");
        Bucket bucketBy = new Bucket(bucketByList, true);
        Map<String, Object> context = new HashMap<>();
        context.put("organizationId", "123");
        context.put("browser", "chrome");

        Map<String, Object> user = new HashMap<>();
        user.put("id", "234");
        context.put("user", user);

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("123.234.test-feature", bucketKey);
    }

    @Test
    public void testOrBucketByTakesFirstAvailableFieldValue() {
        String featureKey = "test-feature";
        List<String> orList = Arrays.asList("userId", "deviceId");
        Bucket bucketBy = new Bucket();
        bucketBy.setOr(orList);

        Map<String, Object> context = new HashMap<>();
        context.put("deviceId", "deviceIdHere");
        context.put("userId", "234");
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("234.test-feature", bucketKey);
    }

    @Test
    public void testOrBucketByTakesFirstAvailableFieldValueWhenFirstIsMissing() {
        String featureKey = "test-feature";
        List<String> orList = Arrays.asList("userId", "deviceId");
        Bucket bucketBy = new Bucket();
        bucketBy.setOr(orList);

        Map<String, Object> context = new HashMap<>();
        context.put("deviceId", "deviceIdHere");
        context.put("browser", "chrome");

        String bucketKey = Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
            .featureKey(featureKey)
            .bucketBy(bucketBy)
            .context(context)
            .logger(logger));

        assertEquals("deviceIdHere.test-feature", bucketKey);
    }

    @Test
    public void testInvalidBucketByThrowsException() {
        String featureKey = "test-feature";
        Bucket bucketBy = new Bucket(); // Empty bucket with no type set
        Map<String, Object> context = new HashMap<>();

        assertThrows(RuntimeException.class, () -> {
            Bucketer.getBucketKey(new Bucketer.GetBucketKeyOptions()
                .featureKey(featureKey)
                .bucketBy(bucketBy)
                .context(context)
                .logger(logger));
        });
    }
}
