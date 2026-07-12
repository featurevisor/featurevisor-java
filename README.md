# Featurevisor Java SDK <!-- omit in toc -->

This is a port of Featurevisor [Javascript SDK](https://featurevisor.com/docs/sdks/javascript/) v3.x to Java, providing a way to evaluate feature flags, variations, and variables in your Java applications.

This SDK supports Featurevisor v3 behavior and v2 datafiles. Generated datafiles continue to carry `schemaVersion: "2"`.

## Table of contents <!-- omit in toc -->

- [Installation](#installation)
- [Public API](#public-api)
  - [Repository](#repository)
  - [Dependency](#dependency)
  - [Authentication](#authentication)
- [Initialization](#initialization)
- [Evaluation types](#evaluation-types)
- [Context](#context)
  - [Setting initial context](#setting-initial-context)
  - [Setting after initialization](#setting-after-initialization)
  - [Replacing existing context](#replacing-existing-context)
  - [Manually passing context](#manually-passing-context)
- [Check if enabled](#check-if-enabled)
- [Getting variation](#getting-variation)
- [Getting variables](#getting-variables)
  - [Type specific methods](#type-specific-methods)
- [Getting all evaluations](#getting-all-evaluations)
- [Sticky](#sticky)
  - [Initialize with sticky](#initialize-with-sticky)
  - [Set sticky afterwards](#set-sticky-afterwards)
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
  - [`sticky_set`](#sticky_set)
  - [`error`](#error)
- [Evaluation details](#evaluation-details)
- [Modules](#modules)
  - [Defining a module](#defining-a-module)
  - [Registering modules](#registering-modules)
- [Child instance](#child-instance)
- [Close](#close)
- [CLI usage](#cli-usage)
  - [Test](#test)
  - [Benchmark](#benchmark)
  - [Assess distribution](#assess-distribution)
- [Development of this package](#development-of-this-package)
  - [Setting up](#setting-up)
  - [Running tests](#running-tests)
  - [Releasing](#releasing)
- [License](#license)

<!-- FEATUREVISOR_DOCS_BEGIN -->

## Installation

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

Add Featurevisor Java SDK as a dependency with your desired version:

```xml
<dependencies>
    <dependency>
        <groupId>com.featurevisor</groupId>
        <artifactId>featurevisor-java</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

Find latest version here: [https://github.com/featurevisor/featurevisor-java/packages](https://github.com/featurevisor/featurevisor-java/packages)

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

You can generate a new GitHub token with `read:packages` scope here: [https://github.com/settings/tokens](https://github.com/settings/tokens)

See example application here: [https://github.com/featurevisor/featurevisor-example-java](https://github.com/featurevisor/featurevisor-example-java)

## Public API

The main runtime API is `Featurevisor.createFeaturevisor()`:

```java
import com.featurevisor.sdk.FeaturevisorLogLevel;

Featurevisor f = Featurevisor.createFeaturevisor(
    new Featurevisor.FeaturevisorOptions().datafile(datafileContent)
);
```

Most applications only need `Featurevisor.createFeaturevisor`, the `Featurevisor` instance type, and `Featurevisor.FeaturevisorOptions`. Public extension and observability types include `FeaturevisorModule`, `FeaturevisorDiagnostic`, and the datafile model types.

## Initialization

The SDK can be initialized by passing [datafile](https://featurevisor.com/docs/building-datafiles/) content directly:

```java
import com.featurevisor.sdk.Featurevisor;

// Load datafile content
String datafileUrl = "https://cdn.yoursite.com/datafile.json";
String datafileContent = "..." // load your datafile content

// Create SDK instance
Featurevisor f = Featurevisor.createFeaturevisor(
    new Featurevisor.FeaturevisorOptions().datafile(datafileContent)
);
```

or by constructing a `Featurevisor.FeaturevisorOptions` object:

```java
Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .datafile(datafileContent)
);
```

We will learn about several different options in the next sections.

## Evaluation types

We can evaluate 3 types of values against a particular [feature](https://featurevisor.com/docs/features/):

- [**Flag**](#check-if-enabled) (`boolean`): whether the feature is enabled or not
- [**Variation**](#getting-variation) (`Object`): the variation of the feature (if any)
- [**Variables**](#getting-variables): variable values of the feature (if any)

These evaluations are run against the provided context.

## Context

Contexts are [attribute](https://featurevisor.com/docs/attributes) values that we pass to SDK for evaluating [features](https://featurevisor.com/docs/features) against.

Think of the conditions that you define in your [segments](https://featurevisor.com/docs/segments/), which are used in your feature's [rules](https://featurevisor.com/docs/features/#rules).

They are plain maps:

```java
Map<String, Object> context = new HashMap<>();
context.put("userId", "123");
context.put("country", "nl");
// ...other attributes
```

Context can be passed to SDK instance in various different ways, depending on your needs:

### Setting initial context

You can set context at the time of initialization:

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

You can also set more context after the SDK has been initialized:

```java
Map<String, Object> additionalContext = new HashMap<>();
additionalContext.put("userId", "123");
additionalContext.put("country", "nl");

f.setContext(additionalContext);
```

This will merge the new context with the existing one (if already set).

### Replacing existing context

If you wish to fully replace the existing context, you can pass `true` in second argument:

```java
Map<String, Object> newContext = new HashMap<>();
newContext.put("deviceId", "123");
newContext.put("userId", "234");
newContext.put("country", "nl");
newContext.put("browser", "chrome");

f.setContext(newContext, true); // replace existing context
```

### Manually passing context

You can optionally pass additional context manually for each and every evaluation separately, without needing to set it to the SDK instance affecting all evaluations:

```java
Map<String, Object> context = new HashMap<>();
context.put("userId", "123");
context.put("country", "nl");

boolean isEnabled = f.isEnabled("my_feature", context);
String variation = f.getVariation("my_feature", context);
String variableValue = f.getVariableString("my_feature", "my_variable", context);
```

When manually passing context, it will merge with existing context set to the SDK instance before evaluating the specific value.

Further details for each evaluation types are described below.

## Check if enabled

Once the SDK is initialized, you can check if a feature is enabled or not:

```java
String featureKey = "my_feature";

boolean isEnabled = f.isEnabled(featureKey);

if (isEnabled) {
    // do something
}
```

You can also pass additional context per evaluation:

```java
Map<String, Object> additionalContext = new HashMap<>();
// ...additional context

boolean isEnabled = f.isEnabled(featureKey, additionalContext);
```

## Getting variation

If your feature has any [variations](https://featurevisor.com/docs/features/#variations) defined, you can evaluate them as follows:

```java
String featureKey = "my_feature";

String variation = f.getVariation(featureKey);

if ("treatment".equals(variation)) {
    // do something for treatment variation
} else {
    // handle default/control variation
}
```

Additional context per evaluation can also be passed:

```java
String variation = f.getVariation(featureKey, additionalContext);
```

## Getting variables

Your features may also include [variables](https://featurevisor.com/docs/features/#variables), which can be evaluated as follows:

```java
String variableKey = "bgColor";

String bgColorValue = f.getVariableString(featureKey, variableKey);
```

Additional context per evaluation can also be passed:

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

Typed overloads are additive and non-breaking. If decoding fails for the requested target type, these methods return `null`.

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

## Getting all evaluations

You can get evaluations of all features available in the SDK instance:

```java
import com.featurevisor.sdk.EvaluatedFeatures;
import com.featurevisor.sdk.EvaluatedFeature;

EvaluatedFeatures allEvaluations = f.getAllEvaluations(context);

// Access the evaluations map
Map<String, EvaluatedFeature> evaluations = allEvaluations.getValue();

System.out.println(evaluations);
// {
//   "myFeature": {
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

## Sticky

For the lifecycle of the SDK instance in your application, you can set some features with sticky values, meaning that they will not be evaluated against the fetched [datafile](https://featurevisor.com/docs/building-datafiles/):

Sticky values belong to an SDK or child instance. Evaluation options do not accept sticky overrides; use `new Featurevisor.SpawnOptions().sticky(...)` when a child needs its own sticky state.

### Initialize with sticky

```java
Map<String, Object> stickyFeatures = new HashMap<>();

Map<String, Object> myFeatureSticky = new HashMap<>();
myFeatureSticky.put("enabled", true);
myFeatureSticky.put("variation", "treatment");

Map<String, Object> myVariables = new HashMap<>();
myVariables.put("myVariableKey", "myVariableValue");
myFeatureSticky.put("variables", myVariables);

stickyFeatures.put("myFeatureKey", myFeatureSticky);

Map<String, Object> anotherFeatureSticky = new HashMap<>();
anotherFeatureSticky.put("enabled", false);
stickyFeatures.put("anotherFeatureKey", anotherFeatureSticky);

Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
    .datafile(datafile)
    .sticky(stickyFeatures));
```

Once initialized with sticky features, the SDK will look for values there first before evaluating the targeting conditions and going through the bucketing process.

### Set sticky afterwards

You can also set sticky features after the SDK is initialized:

```java
Map<String, Object> stickyFeatures = new HashMap<>();
// ... build sticky features map

f.setSticky(stickyFeatures, true); // replace existing sticky features
```

## Setting datafile

You may also initialize the SDK without passing `datafile`, and set it later on:

```java
f.setDatafile(datafileContent);
```

### Merging by default

By default, `setDatafile(datafile)` merges the incoming datafile with the SDK's stored datafile. Incoming top-level metadata is used, and incoming segments/features override existing segments/features with the same keys.

This means you can call `setDatafile` more than once with different datafiles, and the SDK instance accumulates their features and segments together. This is what makes [loading datafiles on demand](#loading-datafiles-on-demand) possible.

### Replacing

To replace the stored datafile entirely, pass `true`:

```java
f.setDatafile(datafileContent, true);
```

### Loading datafiles on demand

Because merging is the default, a single SDK instance can start with a small datafile and load more datafiles later as your application needs them, instead of downloading every feature upfront.

This pairs well with [targets](https://featurevisor.com/docs/targets/), where each target produces a smaller datafile for a specific part of your application:

```java
Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions());

void loadDatafile(String target) throws Exception {
    String url = "https://cdn.yoursite.com/production/featurevisor-" + target + ".json";
    String json = fetchJson(url); // use your HTTP client of choice
    DatafileContent datafile = DatafileContent.fromJson(json);

    // merges into whatever was loaded before
    f.setDatafile(datafile);
}

loadDatafile("products");

// later, when the user reaches checkout
loadDatafile("checkout");
```

### Updating datafile

You can set the datafile as many times as you want in your application, which will result in emitting a [`datafile_set`](#datafile_set) event that you can listen and react to accordingly.

The triggers for setting the datafile again can be:

- periodic updates based on an interval (like every 5 minutes), or
- reacting to:
  - a specific event in your application (like a user action), or
  - an event served via websocket or server-sent events (SSE)

### Interval-based update

Here's an example of using interval-based update:

```java
// Using ScheduledExecutorService for periodic updates
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    // Fetch new datafile content
    String newDatafileContent = // ... fetch from your CDN
    DatafileContent newDatafile = DatafileContent.fromJson(newDatafileContent);

    // Merge into the SDK's existing datafile
    f.setDatafile(newDatafile);
}, 0, 5, TimeUnit.MINUTES);
```

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

Every diagnostic has `level`, `code`, `message`, and an object-shaped `details` map. Optional `module`, `moduleName`, and `originalError` fields describe provenance. Evaluation metadata belongs in `details`.

Diagnostic handlers are isolated from SDK behavior. An exception in a handler does not stop other handlers or evaluations.


## Events

Featurevisor SDK implements a simple event emitter that allows you to listen to events that happen in the runtime.

You can listen to these events that can occur at various stages in your application:

### `datafile_set`

```java
Runnable unsubscribe = f.on("datafile_set", (event) -> {
    String revision = (String) event.get("revision"); // new revision
    String previousRevision = (String) event.get("previousRevision");
    Boolean revisionChanged = (Boolean) event.get("revisionChanged"); // true if revision has changed

    // list of feature keys that have new updates,
    // and you should re-evaluate them
    @SuppressWarnings("unchecked")
    List<String> features = (List<String>) event.get("features");

    // handle here
});

// stop listening to the event
unsubscribe.run();
```

The `features` array will contain keys of features that have either been:

- added, or
- updated, or
- removed

compared to the previous datafile content that existed in the SDK instance.

### `context_set`

```java
Runnable unsubscribe = f.on("context_set", (event) -> {
    Boolean replaced = (Boolean) event.get("replaced"); // true if context was replaced
    @SuppressWarnings("unchecked")
    Map<String, Object> context = (Map<String, Object>) event.get("context"); // the new context

    System.out.println("Context set");
});
```

### `sticky_set`

```java
Runnable unsubscribe = f.on("sticky_set", (event) -> {
    Boolean replaced = (Boolean) event.get("replaced"); // true if sticky features got replaced
    @SuppressWarnings("unchecked")
    List<String> features = (List<String>) event.get("features"); // list of all affected feature keys

    System.out.println("Sticky features set");
});
```

### `error`

```java
Emitter.UnsubscribeFunction unsubscribe = f.on(Emitter.EventName.ERROR, (event) -> {
    System.err.println(event.get("message"));
});
```

## Evaluation details

Besides logging with debug level enabled, you can also get more details about how the feature variations and variables are evaluated in the runtime against given context:

```java
// flag
Map<String, Object> evaluation = f.evaluateFlag(featureKey, context);

// variation
Map<String, Object> evaluation = f.evaluateVariation(featureKey, context);

// variable
Map<String, Object> evaluation = f.evaluateVariable(featureKey, variableKey, context);
```

The returned object will always contain the following properties:

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
- `variableSchema`: the variable schema

## Modules

Modules allow you to intercept the evaluation process and customize it further as per your needs.

### Defining a module

A module is a `FeaturevisorModule` with a unique `name` and optional lifecycle functions:

If `setup` throws, the module is not registered. Featurevisor removes subscriptions created during setup, reports `module_setup_error`, and calls `close` when present.

```java
FeaturevisorModule myCustomModule = new FeaturevisorModule("my-custom-module")
    .setup(api -> {
        System.out.println("Current revision: " + api.getRevision());
    })

    // before evaluation
    .before(options -> {
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
    .after((evaluation, options) -> evaluation)

    // called by f.close()
    .close(() -> {
        // clean up resources
    });
```

### Registering modules

You can register modules at the time of SDK initialization:

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

When dealing with purely client-side applications, it is understandable that there is only one user involved, like in browser or mobile applications.

But when using Featurevisor SDK in server-side applications, where a single server instance can handle multiple user requests simultaneously, it is important to isolate the context for each request.

That's where child instances come in handy:

```java
Map<String, Object> childContext = new HashMap<>();
childContext.put("userId", "123");

ChildInstance childF = f.spawn(childContext);
```

Now you can pass the child instance where your individual request is being handled, and you can continue to evaluate features targeting that specific user alone:

```java
boolean isEnabled = childF.isEnabled("my_feature");
String variation = childF.getVariation("my_feature");
String variableValue = childF.getVariableString("my_feature", "my_variable");
```

Similar to parent SDK, child instances also support several additional methods:

- `setContext`
- `setSticky`
- `isEnabled`
- `getVariation`
- `getVariable`
- `getVariableBoolean`
- `getVariableString`
- `getVariableInteger`
- `getVariableDouble`
- `getVariableArray`
- `getVariableObject`
- `getVariableJSON`
- `getVariableJSONNode`
- `getAllEvaluations`
- `on`
- `close`

## Close

Both primary and child instances support a `.close()` method, that removes forgotten event listeners (via `on` method) and cleans up any potential memory leaks.

```java
f.close();
```

## CLI usage

This package also provides a CLI tool for running your Featurevisor project's test specs and benchmarking against this Java SDK:

All three commands accept repeatable `--target=<target>` options. `test` builds only the selected Target datafiles and runs untargeted assertions plus assertions for those targets. `benchmark` and `assess-distribution` run independently against every selected Target datafile. Without `--target`, existing project-wide behavior is preserved. Project definitions, test specs, Target discovery, and datafile generation continue to come from the Node.js CLI.

### Test

Learn more about testing [here](https://featurevisor.com/docs/testing/).

```bash
$ mvn exec:java -Dexec.mainClass="com.featurevisor.cli.CLI" -Dexec.args="test --projectDirectoryPath=/absolute/path/to/your/featurevisor/project"
```

Additional options that are available:

```bash
$ mvn exec:java -Dexec.mainClass="com.featurevisor.cli.CLI" -Dexec.args="test --projectDirectoryPath=/absolute/path/to/your/featurevisor/project --quiet --onlyFailures --keyPattern=myFeatureKey --assertionPattern=#1 --showDatafile --inflate=1"
```

The test runner builds base datafiles and Target datafiles, then uses a Target datafile when an assertion contains `target`.

### Benchmark

Learn more about benchmarking [here](https://featurevisor.com/docs/cli/#benchmarking).

```bash
$ mvn exec:java -Dexec.mainClass="com.featurevisor.cli.CLI" -Dexec.args="benchmark --projectDirectoryPath=/absolute/path/to/your/featurevisor/project --environment=production --feature=myFeatureKey --context='{\"country\": \"nl\"}' --n=1000"
```

### Assess distribution

Learn more about assessing distribution [here](https://featurevisor.com/docs/cli/#assess-distribution).

```bash
$ mvn exec:java -Dexec.mainClass="com.featurevisor.cli.CLI" -Dexec.args="assess-distribution --projectDirectoryPath=/absolute/path/to/your/featurevisor/project --environment=production --feature=foo --variation --context='{\"country\": \"nl\"}' --populateUuid=userId --populateUuid=deviceId --n=1000"
```

<!-- FEATUREVISOR_DOCS_END -->

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

### Releasing

- Manually create a new release on [GitHub](https://github.com/featurevisor/featurevisor-java/releases)
- Tag it with a prefix of `v`, like `v1.0.0`
- GitHub Actions is set up to automatically publish the package to [GitHub Packages](https://github.com/orgs/featurevisor/packages?repo_name=featurevisor-java)

## License

MIT © [Fahad Heylaal](https://fahad19.com)
