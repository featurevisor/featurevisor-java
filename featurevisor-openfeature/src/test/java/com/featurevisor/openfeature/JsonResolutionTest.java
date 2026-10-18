package com.featurevisor.openfeature;

import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class JsonResolutionTest {
    @Test void trailingDatafileTokensReportTheOriginalParseErrorAndRecover() {
        String root = """
            {"schemaVersion":"4","revision":"accepted","segments":{},"features":{},
             "variables":{"value":{"type":"string","defaultValue":"ok"}}}
            """;
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(
            new Featurevisor.FeaturevisorOptions().datafileString(root + " {}")
                .logLevel(FeaturevisorLogLevel.FATAL));
        try {
            ProviderEvaluation<String> rejected = provider.getStringEvaluation("variable:value", "fallback", ImmutableContext.EMPTY);
            assertEquals("fallback", rejected.getValue());
            assertEquals(ErrorCode.PARSE_ERROR, rejected.getErrorCode());
            assertEquals("Could not parse datafile", rejected.getErrorMessage());
            assertEquals("unknown", provider.getFeaturevisor().getRevision());
            assertTrue(provider.getFeaturevisor().setDatafile(root));
            ProviderEvaluation<String> recovered = provider.getStringEvaluation("variable:value", "fallback", ImmutableContext.EMPTY);
            assertNull(recovered.getErrorCode());
            assertEquals("ok", recovered.getValue());
            assertEquals("accepted", provider.getFeaturevisor().getRevision());
        } finally { provider.shutdown(); }
    }

    @Test void rejectsTrailingJsonButNeverDecodesStringAliasesAgain() throws Exception {
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson("""
                {"schemaVersion":"4","revision":"json-resolution","features":{},"segments":{},"variables":{
                  "invalid":{"type":"json","defaultValue":"false true"},
                  "nullAlias":{"type":"json","compose":"invalid"},
                  "source":{"type":"json","defaultValue":"\\\"false\\\""},
                  "alias":{"type":"json","compose":"source"},
                  "chain":{"type":"json","compose":"alias"}
                }}
                """)).logLevel(FeaturevisorLogLevel.FATAL));
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(f);
        try {
            ProviderEvaluation<Boolean> invalid = provider.getBooleanEvaluation("variable:invalid", true, ImmutableContext.EMPTY);
            assertTrue(invalid.getValue());
            assertEquals(ErrorCode.TYPE_MISMATCH, invalid.getErrorCode());
            ProviderEvaluation<Boolean> fallback = provider.getBooleanEvaluation("variable:nullAlias", true, ImmutableContext.EMPTY);
            assertTrue(fallback.getValue());
            assertNull(fallback.getErrorCode());
            assertEquals(Reason.TARGETING_MATCH.name(), fallback.getReason());
            assertEquals("variable_composed", fallback.getFlagMetadata().getString("featurevisorReason"));
            ProviderEvaluation<String> alias = provider.getStringEvaluation("variable:chain", "fallback", ImmutableContext.EMPTY);
            assertEquals("false", alias.getValue());
            assertNull(alias.getErrorCode());
            assertEquals(Reason.TARGETING_MATCH.name(), alias.getReason());
        } finally { provider.shutdown(); f.close(); }
    }
}
