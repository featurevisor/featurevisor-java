package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModulesManagerTest {

    private ModulesManager modulesManager;
    private List<FeaturevisorDiagnostic> diagnostics;

    @BeforeEach
    void setUp() {
        diagnostics = new ArrayList<>();
        modulesManager = new ModulesManager(new ModulesManager.ModulesManagerOptions()
            .diagnosticReporter((diagnostic, module) -> diagnostics.add(diagnostic))
            .moduleApiFactory(module -> new FeaturevisorModuleApi() {
                @Override
                public String getRevision() { return "unknown"; }

                @Override
                public Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler) { return () -> {}; }

                @Override
                public Runnable onDiagnostic(FeaturevisorDiagnosticHandler handler, FeaturevisorModuleDiagnosticOptions options) { return () -> {}; }

                @Override
                public void reportDiagnostic(FeaturevisorDiagnostic diagnostic) {}
            }));
    }

    @Test
    void testAddAndRemoveModule() {
        FeaturevisorModule module = new FeaturevisorModule("test-module");

        Runnable removeModule = modulesManager.add(module);
        assertNotNull(removeModule);

        List<FeaturevisorModule> modules = modulesManager.getAll();
        assertEquals(1, modules.size());
        assertEquals("test-module", modules.get(0).getName());

        removeModule.run();

        assertEquals(0, modulesManager.getAll().size());
    }

    @Test
    void testRemoveModuleByName() {
        modulesManager.add(new FeaturevisorModule("module1"));
        modulesManager.add(new FeaturevisorModule("module2"));

        assertEquals(2, modulesManager.getAll().size());

        modulesManager.remove("module1");

        List<FeaturevisorModule> modules = modulesManager.getAll();
        assertEquals(1, modules.size());
        assertEquals("module2", modules.get(0).getName());
    }

    @Test
    void testDuplicateModuleNameReportsDiagnostic() {
        Runnable removeModule1 = modulesManager.add(new FeaturevisorModule("duplicate"));
        assertNotNull(removeModule1);

        Runnable removeModule2 = modulesManager.add(new FeaturevisorModule("duplicate"));
        assertNull(removeModule2);

        assertEquals(1, modulesManager.getAll().size());
        assertEquals(1, diagnostics.size());
        assertEquals("duplicate_module", diagnostics.get(0).getCode());
        assertEquals("duplicate", diagnostics.get(0).getModuleName());
        assertEquals(Logger.LogLevel.ERROR, diagnostics.get(0).getLevel());
    }

    @Test
    void testSetupAndCloseModule() {
        AtomicBoolean setupCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        modulesManager.add(new FeaturevisorModule("lifecycle")
            .setup(api -> {
                setupCalled.set(true);
                assertEquals("unknown", api.getRevision());
            })
            .close(() -> closeCalled.set(true)));

        assertTrue(setupCalled.get());

        modulesManager.closeAll();

        assertTrue(closeCalled.get());
        assertEquals(0, modulesManager.getAll().size());
    }

    @Test
    void testBeforeModule() {
        FeaturevisorModule beforeModule = new FeaturevisorModule("before-test")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                if (context == null) {
                    context = new HashMap<>();
                }
                context.put("modified", true);
                return options.copy().context(context);
            });

        modulesManager.add(beforeModule);

        Map<String, Object> originalContext = new HashMap<>();
        originalContext.put("original", true);
        EvaluateOptions options = new EvaluateOptions("flag", "test-feature")
            .context(originalContext);

        EvaluateOptions modifiedOptions = modulesManager.executeBeforeModules(options);

        Map<String, Object> modifiedContext = modifiedOptions.getContext();
        assertTrue((Boolean) modifiedContext.get("original"));
        assertTrue((Boolean) modifiedContext.get("modified"));
    }

    @Test
    void testBucketKeyModule() {
        modulesManager.add(new FeaturevisorModule("bucket-key-test")
            .bucketKey(options -> "modified:" + options.getBucketKey()));

        Map<String, Object> context = new HashMap<>();
        Bucket bucketBy = new Bucket("userId");
        ModulesManager.ConfigureBucketKeyOptions options =
            new ModulesManager.ConfigureBucketKeyOptions("test-feature", context, bucketBy, "original-key");

        assertEquals("modified:original-key", modulesManager.executeBucketKeyModules(options));
    }

    @Test
    void testBucketValueModule() {
        modulesManager.add(new FeaturevisorModule("bucket-value-test")
            .bucketValue(options -> options.getBucketValue() + 10));

        Map<String, Object> context = new HashMap<>();
        ModulesManager.ConfigureBucketValueOptions options =
            new ModulesManager.ConfigureBucketValueOptions("test-feature", "bucket-key", context, 50);

        assertEquals(60, modulesManager.executeBucketValueModules(options));
    }

    @Test
    void testAfterModule() {
        modulesManager.add(new FeaturevisorModule("after-test")
            .after((evaluation, options) -> evaluation.copy().enabled(true)));

        Evaluation evaluation = new Evaluation("flag", "test-feature", "allocated")
            .enabled(false);
        EvaluateOptions options = new EvaluateOptions("flag", "test-feature");

        Evaluation modifiedEvaluation = modulesManager.executeAfterModules(evaluation, options);

        assertTrue(modifiedEvaluation.getEnabled());
    }

    @Test
    void testMultipleModules() {
        modulesManager.add(new FeaturevisorModule("before1")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                if (context == null) {
                    context = new HashMap<>();
                }
                context.put("module1", true);
                return options.copy().context(context);
            }));

        modulesManager.add(new FeaturevisorModule("before2")
            .before(options -> {
                Map<String, Object> context = options.getContext();
                context.put("module2", true);
                return options.copy().context(context);
            }));

        EvaluateOptions modifiedOptions = modulesManager.executeBeforeModules(new EvaluateOptions("flag", "test-feature"));

        Map<String, Object> context = modifiedOptions.getContext();
        assertTrue((Boolean) context.get("module1"));
        assertTrue((Boolean) context.get("module2"));
    }
}
