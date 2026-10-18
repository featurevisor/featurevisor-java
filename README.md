# Featurevisor Java SDK <!-- omit in toc -->

Use the Featurevisor Java SDK to evaluate flags, variations and variables in Java applications.

## Table of contents <!-- omit in toc -->

- [Installation](#installation)
  - [Repository](#repository)
  - [Dependency](#dependency)
  - [Authentication](#authentication)
- [Initialization](#initialization)
  - [Public API](#public-api)
  - [Evaluation types](#evaluation-types)
- [Context](#context)
  - [Setting initial context](#setting-initial-context)
  - [Setting after initialization](#setting-after-initialization)
  - [Replacing existing context](#replacing-existing-context)
  - [Manually passing context](#manually-passing-context)
- [Check if enabled](#check-if-enabled)
- [Getting variation](#getting-variation)
- [Getting variables](#getting-variables)
  - [Getting feature variables](#getting-feature-variables)
  - [Type specific methods](#type-specific-methods)
  - [Getting global variables](#getting-global-variables)
- [Getting evaluations](#getting-evaluations)
- [Sticky](#sticky)
- [Setting datafile](#setting-datafile)
  - [Merging by default](#merging-by-default)
  - [Replacing](#replacing)
  - [Loading datafiles on demand](#loading-datafiles-on-demand)
  - [Updating datafile](#updating-datafile)
  - [Interval-based update](#interval-based-update)
- [Diagnostics](#diagnostics)
  - [Levels](#levels)
  - [Handler](#handler)
- [Events](#events)
  - [`datafile_set`](#datafile_set)
  - [`context_set`](#context_set)
  - [`sticky_features_set`](#sticky_features_set)
  - [`sticky_variables_set`](#sticky_variables_set)
  - [`error`](#error)
- [Evaluation details](#evaluation-details)
- [Modules](#modules)
  - [Defining a module](#defining-a-module)
  - [Registering modules](#registering-modules)
- [Child instance](#child-instance)
- [Close](#close)
- [OpenFeature](#openfeature)
- [CLI usage](#cli-usage)
  - [Test](#test)
  - [Benchmark](#benchmark)
  - [Assess distribution](#assess-distribution)
- [GitHub repositories](#github-repositories)
- [Development of this package](#development-of-this-package)
  - [Setting up](#setting-up)
  - [Running tests](#running-tests)
  - [Releasing](#releasing)
- [License](#license)

<!-- FEATUREVISOR_DOCS_BEGIN -->

## Installation

Upgrading an existing application? Follow the [Java migration guide](https://featurevisor.com/docs/migrations/v4/java/).

In your Java application, update `pom.xml` to add the following:

### Repository

For finding GitHub Package (public package):

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/featurevisor/featurevisor-java</url>
    </repository>
</repositories>
```

### Dependency

Add the Featurevisor Java SDK as a dependency with your desired version:

```xml
<dependencies>
    <dependency>
        <groupId>com.featurevisor</groupId>
        <artifactId>featurevisor-java</artifactId>
        <version>5.0.0</version>
    </dependency>
</dependencies>
```

Browse [Java SDK packages](https://github.com/featurevisor/featurevisor-java/packages)

### Authentication

To authenticate with GitHub Packages, in your `~/.m2/settings.xml` file, add the following:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>

</settings>
```

Generate a token with `read:packages` scope in [GitHub token settings](https://github.com/settings/tokens)

Example applications:

- [Java application](https://github.com/featurevisor/featurevisor-example-java)
- [Kotlin Android application](https://github.com/featurevisor/featurevisor-example-android)

## Initialization

Read a built [datafile](https://featurevisor.com/docs/building-datafiles/) from a local file and pass its content to the SDK. Save this Java 11 example as `Main.java` and place `featurevisor-all.json` in your working directory:

```java
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.DatafileContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("featurevisor-all.json"));
        DatafileContent datafileContent = DatafileContent.fromJson(json);
        Featurevisor f = Featurevisor.createFeaturevisor(
            new Featurevisor.FeaturevisorOptions().datafile(datafileContent)
        );
        try {
            System.out.println(f.isEnabled("checkout", Map.of("userId", "123")));
        } finally {
            f.close();
        }
    }
}
```

File read and JSON parsing failures propagate to the caller. You can also load the content with Java's HTTP client. Check for a successful HTTP status before passing the response body to the SDK. The following sections cover the available options.

### Public API

The main runtime API is `Featurevisor.createFeaturevisor()`:

```java
import com.featurevisor.sdk.FeaturevisorLogLevel;

Featurevisor f = Featurevisor.createFeaturevisor(
    new Featurevisor.FeaturevisorOptions().datafile(datafileContent)
);
```

Most applications only need `Featurevisor.createFeaturevisor`, the `Featurevisor` instance type, and `Featurevisor.FeaturevisorOptions`. Public extension and observability types include `FeaturevisorModule`, `FeaturevisorDiagnostic`, and the datafile model types.

Concurrent evaluations are safe after an instance is configured. Do not call state changing methods such as `setDatafile`, `setContext`, `setStickyFeatures`, `setStickyVariables`, `addModule`, `removeModule`, or `close` concurrently with evaluations or with each other. Apply those changes from a serialized update path. Module, event, and diagnostic callbacks must synchronize mutable state that they capture.

### Evaluation types

Featurevisor evaluates four kinds of values:

- [**Flag**](#check-if-enabled) (`boolean`): whether the feature is enabled or not
- [**Variation**](#getting-variation) (`String`): the variation of the feature (if any)
- [**Feature variable**](#getting-feature-variables): a configuration value owned by a feature
- [**Global variable**](#getting-global-variables): a configuration value evaluated independently of a feature

These evaluations are run against the provided context.

## Context

Contexts are [attribute](https://featurevisor.com/docs/attributes) values you pass to the SDK for evaluating [features](https://featurevisor.com/docs/features) against.

Conditions compare these attributes using the [operators](https://featurevisor.com/docs/operators/), including `empty` and `notEmpty`.

Date comparisons with `before` and `after` accept portable timestamp strings, `Instant`, `java.util.Date` (including `java.sql.Timestamp`), `OffsetDateTime` and `ZonedDateTime`. Native values are compared by their instant and truncated to milliseconds without rounding. Both the native written year, where present, and the UTC year must be within 1 to 9999. Explicit native offsets must have a magnitude of at most 14 hours, including any seconds. Offsets within that limit retain their full precision. `Instant` and `java.util.Date` have only an instant, so no offset check applies. `LocalDate` and `LocalDateTime` are not accepted because they do not identify an instant. Invalid values make both operators return `false`.

They are plain maps:

```java
Map<String, Object> context = new HashMap<>();
context.put("userId", "123");
context.put("country", "nl");
// ...other attributes
```

### Setting initial context

Set context when you create the instance:

```java
Map<String, Object> initialContext = new HashMap<>();
initialContext.put("deviceId", "123");
initialContext.put("country", "nl");

Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .datafile(datafileContent)
    .context(initialContext));
```

This is useful for values that don't change too frequently and available at the time of application startup.

### Setting after initialization

Add context later:

```java
Map<String, Object> additionalContext = new HashMap<>();
additionalContext.put("userId", "123");
additionalContext.put("country", "nl");

f.setContext(additionalContext);
```

This will merge the new context with the existing one (if already set).

### Replacing existing context

Replace the context by passing `true` as the second argument:

```java
Map<String, Object> newContext = new HashMap<>();
newContext.put("deviceId", "123");
newContext.put("userId", "234");
newContext.put("country", "nl");
newContext.put("browser", "chrome");

f.setContext(newContext, true); // replace existing context
```

### Manually passing context

Pass context to one evaluation without changing the instance context:

```java
Map<String, Object> context = new HashMap<>();
context.put("userId", "123");
context.put("country", "nl");

boolean isEnabled = f.isEnabled("checkout", context);
String variation = f.getVariation("checkout", context);
String variableValue = f.getVariableString("checkout", "bgColor", context);
```

When manually passing context, it will merge with existing context set to the SDK instance before evaluating the specific value.

## Check if enabled

Check whether a feature is enabled:

```java
String featureKey = "checkout";

boolean isEnabled = f.isEnabled(featureKey);

if (isEnabled) {
    // do something
}
```

Pass additional context to one evaluation:

```java
Map<String, Object> additionalContext = new HashMap<>();
// ...additional context

boolean isEnabled = f.isEnabled(featureKey, additionalContext);
```

## Getting variation

Evaluate a feature's [variations](https://featurevisor.com/docs/features/#variations):

```java
String featureKey = "checkout";

String variation = f.getVariation(featureKey);

if ("treatment".equals(variation)) {
    // do something for treatment variation
} else {
    // handle default/control variation
}
```

Pass additional context to one evaluation:

```java
String variation = f.getVariation(featureKey, additionalContext);
```

## Getting variables

### Getting feature variables

Evaluate a feature's [variables](https://featurevisor.com/docs/features/#variables):

```java
String variableKey = "bgColor";

String bgColorValue = f.getVariableString(featureKey, variableKey);
```

Pass additional context to one evaluation:

```java
String bgColorValue = f.getVariableString(featureKey, variableKey, additionalContext);
```

### Type specific methods

Next to generic `getVariable()` methods, there are also type specific methods available for convenience:

```java
f.getVariableBoolean(featureKey, variableKey, context);
f.getVariableString(featureKey, variableKey, context);
f.getVariableInteger(featureKey, variableKey, context);
f.getVariableDouble(featureKey, variableKey, context);
f.getVariableArray(featureKey, variableKey, context);

f.<Map<String, Object>>getVariableObject(featureKey, variableKey, context);
f.<MyCustomClass>getVariableObject(featureKey, variableKey, context);

f.<Map<String, Object>>getVariableJSON(featureKey, variableKey, context);
f.<MyCustomClass>getVariableJSON(featureKey, variableKey, context);
f.getVariableJSONNode(featureKey, variableKey, context);
```

Type specific methods do not coerce values. `getVariableInteger()` returns `null` for the string `"1"`, and boolean getters return `null` for non-boolean values.

For strongly typed decoding, additional overloads are available:

```java
import com.fasterxml.jackson.core.type.TypeReference;

// Array decoding using Class<T>
List<MyItem> items = f.getVariableArray(featureKey, variableKey, context, MyItem.class);

// Array decoding using TypeReference
List<Map<String, Object>> rows = f.getVariableArray(
    featureKey,
    variableKey,
    context,
    new TypeReference<List<Map<String, Object>>>() {}
);

// Object decoding using Class<T>
MyConfig config = f.getVariableObject(featureKey, variableKey, context, MyConfig.class);

// Object decoding using TypeReference
Map<String, List<MyConfig>> nested = f.getVariableObject(
    featureKey,
    variableKey,
    context,
    new TypeReference<Map<String, List<MyConfig>>>() {}
);
```

If decoding fails for the requested target type, these methods return `null`.

For dynamic JSON values with unknown shape, use `getVariableJSONNode`:

```java
import com.fasterxml.jackson.databind.JsonNode;

JsonNode node = f.getVariableJSONNode(featureKey, variableKey, context);

if (node != null && node.isObject()) {
    String nested = node.path("key").path("nested").asText(null);
}
```

If a variable schema type is `json` and the resolved value is a malformed stringified JSON, JSON parsing fails safely and these methods return `null`:

- `getVariable(...)`
- `getVariableJSONNode(...)`
- `getVariableJSON(...)`

### Getting global variables

Global variables use the same overloaded methods as variables inside features. A call with one key evaluates a global variable, while a call with a feature key and variable key evaluates a variable owned by that feature:

```java
String message = f.getVariableString("welcomeMessage", context, null);
Object value = f.getVariable("checkoutSettings", context);
Evaluation evaluation = f.evaluateVariable("checkoutSettings", context);
```

The type specific methods are `getVariableBoolean`, `getVariableString`, `getVariableInteger`, `getVariableDouble`, `getVariableArray`, `getVariableObject`, and `getVariableJSON`.

Root conditions and overrides are applied for you. See [Variables](https://featurevisor.com/docs/variables/#global-variables) and [Conditions](https://featurevisor.com/docs/conditions/) for authoring.

### Conditions and composed variables

Conditions combine attribute comparisons, literal segment keys, feature getters and variable comparisons. Arrays imply AND. Logical groups use `and`, `or` and `not`, with NOT negating the AND of its children. Empty expressions are false. Variable paths in generated datafiles are compiled arrays, not authored dotted paths.

Feature evaluation checks sticky values, force rules, root conditions and traffic rules in that order. Global variables check sticky values and root conditions before sources and overrides. An unmet root returns `conditions_unmet` and exposes `Evaluation.getConditions()`. Disabled variations and feature variables retain their existing disabled reasons.

Composed global variables use the ordinary getters:

```java
Object screen = f.getVariable("checkoutScreen", context);
Object policy = f.getVariable("policyAlias", context);
Evaluation evaluation = f.evaluateVariable("checkoutScreen", context);
```

A map composition assembles a fresh outer object with literal properties from feature flags, variations, feature variables and global variables. A string composition aliases one literal global variable key and returns its value directly. Sources use effective context, child sticky state and modules. False, zero, empty strings, arrays and explicit null remain values. JSON sources decode exactly once, even through alias chains. An outer caller default is never forwarded to sources.

Compositions have no defaultValue or useDefaultWhenDisabled. Root conditions can return disabledValue or null without reading sources or overrides. Whole variable sticky state bypasses composition. Sources are read before runtime overrides, whose mutation operations copy changed paths without modifying sources, siblings or sticky values. Successful composition reports `variable_composed`, or `variable_override_rule` when an override matches.

Treat supplied datafiles and returned objects as read only. Copy values before changing them. Composed members and string aliases retain ordinary getter ownership. Only the map's outer object is freshly assembled.

Conditions, segments and composition share cycle detection and a combined dependency depth bound of 256. A cycle or exhausted depth fails the complete evaluation through its ordinary error result. A thread stack too small for the chain fails the same way, and getters never throw `StackOverflowError`. A subsequent public call starts a fresh session.

To see which overrides changed a value, read `variableOverridePaths` from `evaluateVariable`. The singular override fields describe the final match.

## Getting evaluations

Evaluate all features available in the instance:

```java
import com.featurevisor.sdk.EvaluatedFeatures;
import com.featurevisor.sdk.EvaluatedFeature;

EvaluatedFeatures allEvaluations = f.getFeatureEvaluations(context);

// Access the evaluations map
Map<String, EvaluatedFeature> evaluations = allEvaluations.getValue();

System.out.println(evaluations);
// {
//   "checkout": {
//     "enabled": true,
//     "variation": "control",
//     "variables": {
//       "myVariableKey": "myVariableValue"
//     }
//   },
//
//   "anotherFeature": {
//     "enabled": true,
//     "variation": "treatment"
//   }
// }
```

This is handy especially when you want to pass all evaluations from a backend application to the frontend.

Global variables can be evaluated together as well:

```java
Map<String, Object> variables = f.getVariableEvaluations(context, null, null);
```

## Sticky

Set sticky values to take precedence over feature evaluation against the [datafile](https://featurevisor.com/docs/building-datafiles/):

Sticky values belong to an SDK or child instance. Feature sticky values and global variable sticky values are independent.

```java
Map<String, Object> stickyFeatures = new HashMap<>();

Map<String, Object> checkoutSticky = new HashMap<>();
checkoutSticky.put("enabled", true);
checkoutSticky.put("variation", "treatment");

Map<String, Object> checkoutVariables = new HashMap<>();
checkoutVariables.put("myVariableKey", "myVariableValue");
checkoutSticky.put("variables", checkoutVariables);

stickyFeatures.put("checkout", checkoutSticky);

Map<String, Object> anotherFeatureSticky = new HashMap<>();
anotherFeatureSticky.put("enabled", false);
stickyFeatures.put("anotherFeatureKey", anotherFeatureSticky);

Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .datafile(datafile)
    .stickyFeatures(stickyFeatures)
    .stickyVariables(Map.of("welcomeMessage", "Hello")));
```

Once initialized with sticky features, the SDK will look for values there first before evaluating the targeting conditions and going through the bucketing process.

Set sticky features after initialization:

```java
Map<String, Object> stickyFeatures = new HashMap<>();
// ... build sticky features map

f.setStickyFeatures(stickyFeatures, true); // replace existing sticky features
f.setStickyVariables(Map.of("welcomeMessage", "Welcome back"), true); // replace
```

## Setting datafile

You may also initialize the SDK without passing `datafile`, and set it later on:

```java
f.setDatafile(datafileContent);
```

`setDatafile` accepts either a parsed `DatafileContent` or a raw JSON string. All overloads return `true` when the datafile is accepted, including unchanged content, and `false` when the input is rejected or the instance is closed. Rejected input leaves the last accepted datafile in place and reports the existing diagnostic and error event, without a `datafile_set` event. Calls after closing do nothing. Accepted calls still emit `datafile_set`, even when nothing changed. The return value reports acceptance, not whether any features changed.

```java
boolean accepted = f.setDatafile(datafileContent);
```

### Merging by default

By default, `setDatafile(datafile)` merges the incoming datafile with the SDK's stored datafile. Incoming top level metadata is used, and incoming segments, features, and global variables override existing entities with the same keys.

Call `setDatafile` with different datafiles to merge their features, segments, and global variables. Use this to [load datafiles on demand](#loading-datafiles-on-demand).

### Replacing

To replace the stored datafile entirely, pass `true`:

```java
f.setDatafile(datafileContent, true); // replace
```

### Loading datafiles on demand

Merge datafiles for different [targets](https://featurevisor.com/docs/targets/) into the same instance. Here, the two datafile values have already been loaded and validated by your application from the same published release:

```java
f.setDatafile(productsDatafile);
f.setDatafile(checkoutDatafile);
```

Use immutable release URLs, check HTTP responses and keep shared dependencies consistent. See [Loading datafiles on demand](https://featurevisor.com/docs/use-cases/on-demand-datafiles/) for the complete loading and refresh flow.

### Updating datafile

Each accepted datafile update emits a [`datafile_set`](#datafile-set) event. Subscribe to react to updates.

The triggers for setting the datafile again can be:

- periodic updates based on an interval (like every 5 minutes), or
- reacting to:
  - a specific event in your application (like a user action), or
  - an event served via websocket or server-sent events (SSE)

### Interval-based update

Replace the complete datafile on each refresh so definitions removed upstream are removed from the SDK too. Use merging only when loading partial datafiles intentionally.

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.featurevisor.sdk.DatafileContent;

// f is the existing Featurevisor instance
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(URI.create(datafileUrl))
    .timeout(Duration.ofSeconds(30))
    .build();
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            DatafileContent newDatafile = DatafileContent.fromJson(response.body());
            f.setDatafile(newDatafile, true); // replace
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } catch (Exception e) {
        System.err.println("Failed to update datafile: " + e.getMessage());
    }
}, 0, 5, TimeUnit.MINUTES);
```

Call `scheduler.shutdownNow()` when your application shuts down.

## Diagnostics

By default, Featurevisor reports diagnostics to the console for `info` level and above with a `[Featurevisor]` prefix.

### Levels

Available diagnostic levels are `FATAL`, `ERROR`, `WARN`, `INFO`, and `DEBUG`.

Set the level during initialization or update it afterwards:

```java
Featurevisor f = Featurevisor.createFeaturevisor(
    new Featurevisor.FeaturevisorOptions().logLevel(FeaturevisorLogLevel.DEBUG)
);

f.setLogLevel(FeaturevisorLogLevel.INFO);
```

### Handler

Use `onDiagnostic` to send structured diagnostics to your observability system:

```java
Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .logLevel(FeaturevisorLogLevel.INFO)
    .onDiagnostic(diagnostic -> {
        System.out.println(diagnostic.getLevel() + ": " + diagnostic.getCode());
    }));
```

Generated datafiles use schemaVersion `"4"`. Schema `"2"` selectors normalize eagerly when accepted, while schema `"4"` entities normalize on first read. Partial updates invalidate only incoming entities. Replacement clears all entity caches. Legacy compatibility conversion does not change caller objects or the informational version metadata.

Malformed stringified conditions fail only their complete selector, including through NOT or segment aliases, without rejecting the datafile. `conditions_parse_error` is reported at error level once per selector per incoming entity load. Its details are exactly `conditions`, `entityType` and `entityKey`. Eager diagnostics run after storage, and lazy diagnostics run after caching, so callbacks may safely reenter getters.

Every diagnostic has `level`, `code`, `message`, and an object shaped `details` map. Optional `module`, `moduleName`, and `originalError` fields describe provenance. Evaluation metadata belongs in `details`.

Diagnostic handlers are isolated from SDK behaviour. An exception in a handler does not stop other handlers or evaluations.

## Events

Subscribe to SDK events to respond to context, sticky state and datafile changes.

Subscribe to instance lifecycle events:

### `datafile_set`

```java
FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.DATAFILE_SET, (event) -> {
    String revision = (String) event.get("revision"); // new revision
    String previousRevision = (String) event.get("previousRevision");
    Boolean revisionChanged = (Boolean) event.get("revisionChanged"); // true if revision has changed

    // list of feature keys that have new updates,
    // and you should re-evaluate them
    @SuppressWarnings("unchecked")
    List<String> features = (List<String>) event.get("features");

    @SuppressWarnings("unchecked")
    List<String> variables = (List<String>) event.get("variables");

    // handle here
});

// stop listening to the event
unsubscribe.unsubscribe();
```

The `features` and `variables` arrays contain directly changed entities and their transitive dependants through conditions, segments and composition, including removed dependencies.

- added, or
- updated, or
- removed

compared to the previous datafile content that existed in the SDK instance.

### `context_set`

```java
FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.CONTEXT_SET, (event) -> {
    Boolean replaced = (Boolean) event.get("replaced"); // true if context was replaced
    @SuppressWarnings("unchecked")
    Map<String, Object> context = (Map<String, Object>) event.get("context"); // the new context

    System.out.println("Context set");
});
```

### `sticky_features_set`

```java
FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.STICKY_FEATURES_SET, (event) -> {
    Boolean replaced = (Boolean) event.get("replaced"); // true if sticky features got replaced
    @SuppressWarnings("unchecked")
    List<String> features = (List<String>) event.get("features"); // list of all affected feature keys

    System.out.println("Sticky features set");
});
```

### `sticky_variables_set`

```java
FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.STICKY_VARIABLES_SET, (event) -> {
    @SuppressWarnings("unchecked")
    List<String> variables = (List<String>) event.get("variables");
});
```

### `error`

```java
FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.ERROR, (event) -> {
    FeaturevisorDiagnostic diagnostic = (FeaturevisorDiagnostic) event.get("diagnostic");
    System.err.println(diagnostic.getMessage());
});
```

The `error` event is emitted for diagnostics whose level is `ERROR`.

## Evaluation details

Inspect how variations and variables are evaluated against a context:

```java
// flag
Evaluation evaluation = f.evaluateFlag(featureKey, context);

// variation
Evaluation evaluation = f.evaluateVariation(featureKey, context);

// variable
Evaluation evaluation = f.evaluateVariable(featureKey, variableKey, context);

// global variable
Evaluation evaluation = f.evaluateVariable(variableKey, context);
```

The returned `Evaluation` exposes the following properties:

- `featureKey`: the feature key
- `reason`: the reason how the value was evaluated

And optionally these properties depending on whether you are evaluating a feature variation or a variable:

- `bucketValue`: the bucket value between 0 and 100,000
- `ruleKey`: the rule key
- `error`: the error object
- `enabled`: if feature itself is enabled or not
- `variation`: the variation object
- `variationValue`: the variation value
- `variableKey`: the variable key
- `variableValue`: the variable value
- `variableSchema`: the feature variable schema
- `variable`: the global variable definition
- `conditions`: the unmet root expression
- `variableOverrideIndex`, `variableOverrideKey`, `variableOverridePath`, `variableOverridePaths`: matched override provenance

## Modules

The before evaluation and after evaluation phases each use the module membership at the start of that phase. Where an evaluation uses bucketing, the bucket key and bucket value phases each use their own starting membership. Adding or removing a module during a phase does not change the iteration already in progress. The next phase uses the membership then installed. A module added before the after phase can receive after callbacks for evaluations already in progress, even if it did not receive their before callbacks. A module removed before the after phase receives no after callback.

Modules allow you to intercept the evaluation process and customize it further as per your needs.

`beforeEvaluation` and `afterEvaluation` run in registration order for feature and global variable evaluations. Condition and composition reads use the same complete module pipeline, and transformed defaults are preserved. One pair surrounds a complete accumulating evaluation, not each mutation operation.

Module options expose `getDependencyOf()` for an internal getter read. It identifies the immediate requester by type and optional feature and variable keys. `getDependencyPurpose()` is `condition` for eligibility checks or `composition` for consumed values. Application calls, including an independent public getter invoked from a module, have neither marker. Repeated equivalent internal reads can be memoized within one public call, separately for each purpose and requester.

Treat sticky maps and their nested values as read only after passing them to an instance or module. Use sticky setters or return replacement maps from a module to change them. Mutating an existing map in place is not supported.

Modules should return the same result for the same input during one evaluation. Pass values that change through context.

### Defining a module

A module is a `FeaturevisorModule` with a unique `name` and optional lifecycle functions:

If `setup` throws, the module is not registered. Featurevisor removes subscriptions created during setup, reports `module_setup_error`, and calls `close` when present.

```java
FeaturevisorModule myCustomModule = new FeaturevisorModule("my-custom-module")
    .setup(api -> {
        System.out.println("Current revision: " + api.getRevision());
    })

    // before evaluation
    .beforeEvaluation(options -> {
        Map<String, Object> context = new HashMap<>(options.getContext());
        context.put("someAdditionalAttribute", "value");
        return options.copy().context(context);
    })

    // configure bucket key
    .bucketKey(options -> {
        String bucketKey = options.getBucketKey();
        return bucketKey;
    })

    // configure bucket value (between 0 and 100,000)
    .bucketValue(options -> {
        int bucketValue = options.getBucketValue();
        return bucketValue;
    })

    // after evaluation
    .afterEvaluation((evaluation, options) -> evaluation)

    // called by f.close()
    .close(() -> {
        // clean up resources
    });
```

### Registering modules

Register modules when you create the instance:

```java
List<FeaturevisorModule> modules = new ArrayList<>();
modules.add(myCustomModule);

Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .datafile(datafile)
    .modules(modules));
```

Or after initialization:

```java
Runnable removeModule = f.addModule(myCustomModule);

// removeModule.run();
// or:
f.removeModule("my-custom-module");
```

Modules receive an API during `setup` and can subscribe to diagnostics or report their own:

```java
FeaturevisorModule module = new FeaturevisorModule("diagnostic-module")
    .setup(api -> {
        Runnable unsubscribe = api.onDiagnostic(diagnostic -> {
            // observe diagnostics from the SDK and other modules
        });

        api.reportDiagnostic(new FeaturevisorDiagnostic()
            .level(FeaturevisorLogLevel.WARN)
            .code("custom_module_warning")
            .message("Something notable happened"));
    });
```

## Child instance

A child snapshots the parent keys that exist when it is spawned. Child values win for those keys. Parent keys introduced later are still inherited. Calling `close()` removes both child-owned listeners and subscriptions delegated to the parent.

When dealing with purely client-side applications, it is understandable that there is only one user involved, like in browser or mobile applications.

But when using Featurevisor SDK in server-side applications, where a single server instance can handle multiple user requests simultaneously, it is important to isolate the context for each request.

That's where child instances come in handy:

```java
Map<String, Object> childContext = new HashMap<>();
childContext.put("userId", "123");

ChildInstance childF = f.spawn(childContext);
```

Pass the child instance to the request handler to evaluate features for that user:

```java
boolean isEnabled = childF.isEnabled("checkout");
String variation = childF.getVariation("checkout");
String variableValue = childF.getVariableString("checkout", "bgColor");
```

Similar to parent SDK, child instances also support several additional methods:

- `setContext`
- `setStickyFeatures`
- `setStickyVariables`
- `evaluateFlag`
- `isEnabled`
- `evaluateVariation`
- `getVariation`
- `evaluateVariable`
- `getVariable`
- `getVariableBoolean`
- `getVariableString`
- `getVariableInteger`
- `getVariableDouble`
- `getVariableArray`
- `getVariableObject`
- `getVariableJSON`
- `getVariableJSONNode`
- `getFeatureEvaluations`
- `getVariableEvaluations`
- `on`
- `close`

## Close

Both primary and child instances support a `.close()` method, that removes forgotten event listeners (via `on` method) and cleans up any potential memory leaks.

```java
f.close();
```

## OpenFeature

The OpenFeature provider is published as a separate artifact. Applications that use only `com.featurevisor:featurevisor-java` receive no provider classes or OpenFeature dependency.

Add the provider with the same version as the Featurevisor Java SDK:

```xml
<dependency>
  <groupId>com.featurevisor</groupId>
  <artifactId>featurevisor-openfeature</artifactId>
  <version>5.0.0</version>
</dependency>
```

The provider artifact depends on the matching Featurevisor Java SDK and the official OpenFeature Java SDK 1.23.0, so no additional dependency is required. Both retain Java 11 as the minimum runtime.

```java
import com.featurevisor.openfeature.FeaturevisorOpenFeatureProvider;
import com.featurevisor.sdk.Featurevisor;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;

var provider = new FeaturevisorOpenFeatureProvider(
    new Featurevisor.FeaturevisorOptions().datafile(datafileContent)
);

var api = OpenFeatureAPI.getInstance();
api.setProviderAndWait(provider);

var client = api.getClient();
boolean enabled = client.getBooleanValue("checkout", false, new ImmutableContext("user-123"));
```

Use `checkout` for a flag, `checkout:variation` for its variation, `checkout:title` for its `title` variable, and `variable:welcomeMessage` for a global variable. Boolean variables use the boolean resolver. Lists, structures, and JSON variables use the object resolver.

OpenFeature's targeting key maps to `userId` by default. `targetingKeyField`, `keySeparator`, `variationKey`, and `globalVariablePrefix` on `FeaturevisorOpenFeatureProvider.Options` can customize the mapping. The global variable prefix defaults to `variable` and cannot contain the configured separator.

Reuse an existing Featurevisor instance:

```java
Featurevisor featurevisor = Featurevisor.createFeaturevisor(featurevisorOptions);
var provider = new FeaturevisorOpenFeatureProvider(featurevisor);
```

The caller owns an instance passed this way. Provider shutdown does not close it. Call `featurevisor.close()` when every consumer is finished with it. When the provider creates the instance from options, the provider owns and closes it. If both are configured, the existing instance takes precedence.

An invalid initial datafile in an owned instance fails `setProviderAndWait` with a parse error, independently of the diagnostic log level. Valid updates clear the error and emit readiness. Supplied diagnostic callbacks and module setup remain unchanged, and shutdown closes only an owned instance.

Composed values map to `TARGETING_MATCH` without additional JSON decoding. Unmet root conditions map to `DISABLED`. Ordinary null values retain the provider default policy.

See the [OpenFeature provider guide](https://featurevisor.com/docs/sdks/openfeature/) for resolution reasons, errors, metadata, tracking, lifecycle, and providers for other languages.

## CLI usage

Compile the CLI module from this SDK repository:

```bash
mvn -pl featurevisor-sdk compile
```

The native test runner requires `@featurevisor/cli` 4.0.0 or later and Node.js 24 or later. Install the Node.js CLI in your Featurevisor project:

```bash
npm install --save-dev @featurevisor/cli@^4.0.0
```

Project parsing and datafile generation use `npx featurevisor`. Evaluations run through this Java SDK. The SDK runtime has no Node.js dependency. All three commands accept repeated `--target=<target>` options.

Use the Node.js CLI directly for authoring commands such as `init`, `lint`, `build` and `list`. Native commands delegate project parsing, test expansion and datafile generation to Node.js, then evaluate the results with this SDK.

Options use kebab case, such as `--project-directory-path`. The camelCase forms from earlier releases still work, and `--root-directory-path` is an alias for `--project-directory-path`. Pass values as `--name=value` or `--name value`. Repeat `--target` to select several targets in order. Run `<command> --help` to list every option.

`--n` and `-n` accept both value forms. Repeat `--populate-uuid` for multiple context attributes. Quote JSON and paths containing spaces.

### Test

```bash
mvn -q -pl featurevisor-sdk exec:java -Dexec.mainClass=com.featurevisor.cli.CLI \
  -Dexec.args="test --project-directory-path=/path/to/featurevisor-project --only-failures"
```

See [Testing](https://featurevisor.com/docs/testing/) for authoring specs and [Checks](https://featurevisor.com/docs/testing/#checks) for check syntax and operators.

Segment specs and checks evaluate ordinary SDK gates with the selected datafile, context, fixed bucket and isolated sticky state. Compiled execution definitions restore missing segments. Existing Target segment specializations remain authoritative. Attribute only segment chains can omit the environment, while segments depending on features or variables require the selected datafile. All transitive root and child check references are preflighted before getters. Synthetic segment gates do not count towards coverage.

Useful test options:

- `--key-pattern=<pattern>` and `--assertion-pattern=<pattern>` filter tests.
- `--entity-type=scenario` selects scenarios. `--set=<set>` selects one project set. Omitting `--set` runs all sets.
- `--case=<id>` reruns one expanded case. Copy the opaque ID or rerun command from a failure report.
- `--json --pretty` produces an indented JSON report.
- `--coverage` reports observed rule and override selections. `--uncovered` lists features and global variables without associated specs.
- `--only-failures`, `--quiet`, `--verbose` and `--show-datafile` control diagnostic output.

JSON rerun arguments use `argumentEncoding: maven-exec`. Pass the shell quoted native argument list as one `-Dexec.args` value, or copy the printed rerun command.

### Benchmark

The examples below use a project without environments. Add `--environment=<name>` if your project configures environments. In a sets project, omit `--set` to run every set or pass `--set=<name>`. Repeat `--target=<key>` to evaluate specific target datafiles. The Node.js CLI validates these options and builds the datafiles. Java evaluates them. Replace `checkout` with a feature defined in your project.

```bash
mvn -q -pl featurevisor-sdk exec:java -Dexec.mainClass=com.featurevisor.cli.CLI \
  -Dexec.args="benchmark --project-directory-path=/path/to/featurevisor-project --feature=checkout --context='{\"country\":\"nl\"}' --n=1000"
```

Use `--variation` for a variation or `--variable=<key>` for a feature variable. Omit `--feature` to benchmark a global variable. Do not combine `--variation` with `--variable`. See [Benchmarking](https://featurevisor.com/docs/cli/#benchmarking).

### Assess distribution

```bash
mvn -q -pl featurevisor-sdk exec:java -Dexec.mainClass=com.featurevisor.cli.CLI \
  -Dexec.args="assess-distribution --project-directory-path=/path/to/featurevisor-project --feature=checkout --populate-uuid=userId --n=1000"
```

The Java CLI also accepts `--variation` or `--variable=<key>` to assess a variation or feature variable instead of the enabled state. These selectors are specific to this runner and are not supported by the Node.js `assess-distribution` command. Repeat `--populate-uuid=<key>` to generate UUID values for multiple context attributes. See [Assess distribution](https://featurevisor.com/docs/cli/#assess-distribution).

## GitHub repositories

- SDK: [featurevisor/featurevisor-java](https://github.com/featurevisor/featurevisor-java)
- Example application: [featurevisor/featurevisor-example-java](https://github.com/featurevisor/featurevisor-example-java)
- Kotlin Android example application: [featurevisor/featurevisor-example-android](https://github.com/featurevisor/featurevisor-example-android)

<!-- FEATUREVISOR_DOCS_END -->

<a id="getting-aggregate-evaluations"></a>

The aggregate evaluation reference has moved to [Getting evaluations](#getting-evaluations).

<a id="sticky-features-and-variables"></a>

The sticky state reference has moved to [Sticky](#sticky).

## Development of this package

### Setting up

Clone the repository, and install the dependencies using [Maven](https://maven.apache.org/):

```bash
$ mvn install
```

### Running tests

```bash
$ mvn test
```

Compile the actual initialization snippet for Java 11 and execute it with an isolated local datafile:

```bash
mvn -Dmaven.test.skip=true package
bash scripts/test-readme-initialization.sh
```

The README check also covers missing files and invalid JSON. CI runs it on both configured JDKs. Pass `--offline` to reuse cached Maven dependencies locally.

To check runtime command selection against an already built Node.js monorepo:

```bash
mvn -pl featurevisor-sdk test-compile dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
node scripts/test-runtime-commands.mjs ../featurevisor java \
  -cp "featurevisor-sdk/target/classes:$(cat featurevisor-sdk/target/runtime-classpath.txt)" \
  com.featurevisor.cli.CLI
bash scripts/test-release.sh 5.0.0
```

These checks create temporary flat, environment and sets projects without changing the monorepo or downloading packages. The classpath command above uses the POSIX `:` separator.

The repository is a Maven reactor with two published libraries:

- `com.featurevisor:featurevisor-java`
- `com.featurevisor:featurevisor-openfeature`

To verify their published JAR and POM boundaries locally:

```bash
$ make verify-artifacts
```

### Releasing

The SDK, provider and parent POM share version `5.0.0`. Before tagging, run `bash scripts/verify-release.sh v5.0.0` to check the Maven revision, CLI and both README installation examples.

1. Merge the release changes into `main`.
2. Tag the release with a `v` prefix, such as `vX.Y.Z`, and push the tag.
3. GitHub Actions verifies and publishes the parent POM, Java SDK, and OpenFeature provider to [GitHub Packages](https://github.com/orgs/featurevisor/packages?repo_name=featurevisor-java).
4. Create the corresponding [GitHub release](https://github.com/featurevisor/featurevisor-java/releases).

## License

MIT © [Fahad Heylaal](https://fahad19.com)
