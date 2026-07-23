package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class PublicApiTest {
    @Test
    void exposesContractsButKeepsManagersInternal() {
        assertTrue(Modifier.isPublic(Featurevisor.class.getModifiers()));
        assertTrue(Modifier.isPublic(ChildInstance.class.getModifiers()));
        assertTrue(Modifier.isPublic(FeaturevisorModule.class.getModifiers()));
        assertTrue(Modifier.isPublic(FeaturevisorEventName.class.getModifiers()));
        assertTrue(Modifier.isPublic(FeaturevisorEventDetails.class.getModifiers()));
        assertTrue(Modifier.isPublic(FeaturevisorEventHandler.class.getModifiers()));
        assertTrue(Modifier.isPublic(FeaturevisorUnsubscribe.class.getModifiers()));
        assertTrue(Modifier.isPublic(ConfigureBucketKeyOptions.class.getModifiers()));
        assertTrue(Modifier.isPublic(ConfigureBucketValueOptions.class.getModifiers()));

        assertFalse(Modifier.isPublic(Emitter.class.getModifiers()));
        assertFalse(Modifier.isPublic(ModulesManager.class.getModifiers()));
        assertFalse(Modifier.isPublic(InstanceEvaluationDataProvider.class.getModifiers()));
        assertFalse(Modifier.isPublic(Evaluate.class.getModifiers()));
    }
}
