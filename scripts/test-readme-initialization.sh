#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
temporary="$(mktemp -d "${TMPDIR:-/tmp}/featurevisor-readme-classpath.XXXXXX")"
trap 'rm -rf "$temporary"' EXIT

version="$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)"
sdk_jar="$root/featurevisor-sdk/target/featurevisor-java-$version.jar"
if [[ ! -f "$sdk_jar" ]]; then
  echo "Package the SDK first: mvn -Dmaven.test.skip=true package" >&2
  exit 1
fi
mvn --batch-mode -q -pl featurevisor-sdk \
  org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath \
  -DincludeScope=runtime -Dmdep.outputFile="$temporary/classpath" "$@"
classpath="$sdk_jar:$(<"$temporary/classpath")"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$java" scripts/ReadmeInitializationCheck.java "$classpath"
