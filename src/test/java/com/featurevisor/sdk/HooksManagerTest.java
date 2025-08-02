package com.featurevisor.sdk;

import com.featurevisor.types.Bucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Test class for HooksManager functionality
 */
public class HooksManagerTest {

    private HooksManager hooksManager;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = new Logger();
        HooksManager.HooksManagerOptions options = new HooksManager.HooksManagerOptions(logger);
        hooksManager = new HooksManager(options);
    }

    @Test
    void testAddAndRemoveHook() {
        // Create a simple hook
        HooksManager.Hook hook = new HooksManager.Hook("test-hook");

        // Add hook
        Runnable removeHook = hooksManager.add(hook);
        assertNotNull(removeHook);

        // Verify hook was added
        List<HooksManager.Hook> hooks = hooksManager.getAll();
        assertEquals(1, hooks.size());
        assertEquals("test-hook", hooks.get(0).getName());

        // Remove hook using returned function
        removeHook.run();

        // Verify hook was removed
        hooks = hooksManager.getAll();
        assertEquals(0, hooks.size());
    }

    @Test
    void testRemoveHookByName() {
        // Create hooks
        HooksManager.Hook hook1 = new HooksManager.Hook("hook1");
        HooksManager.Hook hook2 = new HooksManager.Hook("hook2");

        // Add hooks
        hooksManager.add(hook1);
        hooksManager.add(hook2);

        // Verify both hooks are present
        assertEquals(2, hooksManager.getAll().size());

        // Remove one hook by name
        hooksManager.remove("hook1");

        // Verify only one hook remains
        List<HooksManager.Hook> hooks = hooksManager.getAll();
        assertEquals(1, hooks.size());
        assertEquals("hook2", hooks.get(0).getName());
    }

    @Test
    void testDuplicateHookName() {
        // Create hooks with same name
        HooksManager.Hook hook1 = new HooksManager.Hook("duplicate");
        HooksManager.Hook hook2 = new HooksManager.Hook("duplicate");

        // Add first hook
        Runnable removeHook1 = hooksManager.add(hook1);
        assertNotNull(removeHook1);

        // Try to add second hook with same name
        Runnable removeHook2 = hooksManager.add(hook2);
        assertNull(removeHook2); // Should return null for duplicate

        // Verify only one hook exists
        assertEquals(1, hooksManager.getAll().size());
    }

    @Test
    void testBeforeHook() {
        // Create before hook that modifies context
        HooksManager.Hook beforeHook = new HooksManager.Hook("before-test")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                if (context == null) {
                    context = new HashMap<>();
                }
                context.put("modified", true);
                return options.copy().context(context);
            });

        hooksManager.add(beforeHook);

        // Create test options
        Map<String, Object> originalContext = new HashMap<>();
        originalContext.put("original", true);
        EvaluateOptions options = new EvaluateOptions("flag", "test-feature")
            .context(originalContext);

        // Execute before hooks
        EvaluateOptions modifiedOptions = hooksManager.executeBeforeHooks(options);

        // Verify context was modified
        Map<String, Object> modifiedContext = modifiedOptions.getContext();
        assertTrue((Boolean) modifiedContext.get("original"));
        assertTrue((Boolean) modifiedContext.get("modified"));
    }

    @Test
    void testBucketKeyHook() {
        // Create bucket key hook
        HooksManager.Hook bucketKeyHook = new HooksManager.Hook("bucket-key-test")
            .bucketKey(options -> {
                String originalKey = options.getBucketKey();
                return "modified:" + originalKey;
            });

        hooksManager.add(bucketKeyHook);

        // Create test options
        Map<String, Object> context = new HashMap<>();
        Bucket bucketBy = new Bucket("userId");
        HooksManager.ConfigureBucketKeyOptions options =
            new HooksManager.ConfigureBucketKeyOptions("test-feature", context, bucketBy, "original-key");

        // Execute bucket key hooks
        String modifiedKey = hooksManager.executeBucketKeyHooks(options);

        // Verify key was modified
        assertEquals("modified:original-key", modifiedKey);
    }

    @Test
    void testBucketValueHook() {
        // Create bucket value hook
        HooksManager.Hook bucketValueHook = new HooksManager.Hook("bucket-value-test")
            .bucketValue(options -> {
                int originalValue = options.getBucketValue();
                return originalValue + 10;
            });

        hooksManager.add(bucketValueHook);

        // Create test options
        Map<String, Object> context = new HashMap<>();
        HooksManager.ConfigureBucketValueOptions options =
            new HooksManager.ConfigureBucketValueOptions("test-feature", "bucket-key", context, 50);

        // Execute bucket value hooks
        int modifiedValue = hooksManager.executeBucketValueHooks(options);

        // Verify value was modified
        assertEquals(60, modifiedValue);
    }

    @Test
    void testAfterHook() {
        // Create after hook that modifies evaluation
        HooksManager.Hook afterHook = new HooksManager.Hook("after-test")
            .after((evaluation, options) -> {
                return evaluation.copy().enabled(true);
            });

        hooksManager.add(afterHook);

        // Create test evaluation and options
        Evaluation evaluation = new Evaluation("flag", "test-feature", "allocated")
            .enabled(false);
        EvaluateOptions options = new EvaluateOptions("flag", "test-feature");

        // Execute after hooks
        Evaluation modifiedEvaluation = hooksManager.executeAfterHooks(evaluation, options);

        // Verify evaluation was modified
        assertTrue(modifiedEvaluation.getEnabled());
    }

    @Test
    void testMultipleHooks() {
        // Create multiple hooks
        HooksManager.Hook beforeHook = new HooksManager.Hook("before1")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                if (context == null) {
                    context = new HashMap<>();
                }
                context.put("hook1", true);
                return options.copy().context(context);
            });

        HooksManager.Hook beforeHook2 = new HooksManager.Hook("before2")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                context.put("hook2", true);
                return options.copy().context(context);
            });

        hooksManager.add(beforeHook);
        hooksManager.add(beforeHook2);

        // Create test options
        EvaluateOptions options = new EvaluateOptions("flag", "test-feature");

        // Execute before hooks
        EvaluateOptions modifiedOptions = hooksManager.executeBeforeHooks(options);

        // Verify both hooks were executed
        Map<String, Object> context = modifiedOptions.getContext();
        assertTrue((Boolean) context.get("hook1"));
        assertTrue((Boolean) context.get("hook2"));
    }
}
