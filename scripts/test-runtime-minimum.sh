#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

# The default verifies the supported minimum. CI also runs the same consumer on
# its latest JDK, supplying that version explicitly rather than claiming Java 11.
expected_java="${1:-11}"
if [[ -n "${JAVA_HOME:-}" ]]; then
  java="$JAVA_HOME/bin/java"
  javac="$JAVA_HOME/bin/javac"
else
  java="java"
  javac="javac"
fi
actual_java="$("$java" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p')"
if [[ "$actual_java" != "$expected_java" ]]; then
  echo "Expected Java $expected_java execution runtime, found $actual_java. Set JAVA_HOME to the intended JDK." >&2
  exit 1
fi

version="$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)"
sdk_jar="$root/featurevisor-sdk/target/featurevisor-java-$version.jar"
if [[ -z "$version" || ! -f "$sdk_jar" ]]; then
  echo "Package the Maven reactor before running this consumer: mvn -Dmaven.test.skip=true package" >&2
  exit 1
fi

temporary="$(mktemp -d "${TMPDIR:-/tmp}/featurevisor-java-consumer.XXXXXX")"
trap 'rm -rf "$temporary"' EXIT

# Resolve only SDK runtime dependencies, never reactor classes, JUnit or the
# separate OpenFeature provider. This goal does not rebuild shared output.
mvn --batch-mode -q -pl featurevisor-sdk \
  org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath \
  -DincludeScope=runtime -Dmdep.outputFile="$temporary/classpath"
classpath="$sdk_jar:$(<"$temporary/classpath")"
"$javac" --release 11 -encoding UTF-8 -cp "$classpath" -d "$temporary" \
  scripts/fixtures/MinimumRuntimeConsumer.java
"$java" -cp "$temporary:$classpath" MinimumRuntimeConsumer \
  scripts/fixtures/minimum-runtime-datafile.json "$expected_java"
