import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A standalone consumer compiled against the packaged SDK, without JUnit. */
public final class MinimumRuntimeConsumer {
    private static int checks;

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", found " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        equal(args[1], System.getProperty("java.specification.version"));
        equal(true, Featurevisor.class.getProtectionDomain().getCodeSource()
            .getLocation().getPath().endsWith(".jar"));
        for (String name : List.of("dev.openfeature.sdk.OpenFeatureAPI",
                "com.featurevisor.openfeature.FeaturevisorOpenFeatureProvider",
                "org.junit.jupiter.api.Test")) {
            try {
                Class.forName(name);
                throw new AssertionError("Unexpected consumer dependency: " + name);
            } catch (ClassNotFoundException expected) {
                checks++;
            }
        }

        String datafile = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(datafile)
            .context(Map.of("userId", "123", "country", "nl"))
            .logLevel(FeaturevisorLogLevel.FATAL));
        try {
            equal(true, sdk.isEnabled("base"));
            equal(true, sdk.isEnabled("policy"));
            equal(false, sdk.isEnabled("missing"));
            equal(true, sdk.getVariable("gate"));
            equal(Map.of("grants", List.of("feature")), sdk.getVariable("policy", "grants"));
            Map<String, Object> grants = Map.of("grants", List.of("read"), "label", "café");
            equal(grants, sdk.getVariable("policy"));
            equal(grants, sdk.getVariable("policy"));
            equal(grants, sdk.getVariable("policyAlias"));
            equal(Map.of("enabled", true, "policy", grants), sdk.getVariable("screen"));
            equal("variable_composed", sdk.evaluateVariable("policyAlias").getReason());
            equal(Map.of("grants", List.of()), sdk.getVariable("policy", Map.of("country", "de")));
            sdk.setContext(Map.of("country", "de"));
            equal(Map.of("grants", List.of()), sdk.getVariable("policy"));
            sdk.setContext(Map.of("country", "nl"));
            equal(grants, sdk.getVariable("policy"));
            sdk.setStickyVariables(Map.of("gate", false));
            equal(false, sdk.isEnabled("policy"));
            equal(null, sdk.getVariable("policy", "grants"));
            equal(Map.of("grants", List.of("blocked")), sdk.getVariable("policy"));
            sdk.setStickyVariables(Map.of(), true);
            equal(true, sdk.isEnabled("policy"));
            equal(grants, sdk.getVariable("policy"));
            sdk.setStickyFeatures(Map.of("base", Map.of("enabled", false)));
            equal(false, sdk.isEnabled("policy"));
            sdk.setStickyFeatures(Map.of(), true);
            equal(true, sdk.isEnabled("policy"));
            sdk.setDatafile(datafile.replace("\"read\"", "\"write\""), true);
            equal(Map.of("grants", List.of("write"), "label", "café"), sdk.getVariable("policy"));
        } finally {
            sdk.close();
        }
        System.out.println("Packaged SDK consumer passed " + checks + " checks on Java "
            + System.getProperty("java.version") + " (consumer compiled with --release 11)");
    }
}
