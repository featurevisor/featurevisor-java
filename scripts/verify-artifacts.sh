#!/usr/bin/env bash

set -euo pipefail

find_main_jar() {
  find "$1/target" -maxdepth 1 -type f -name "$2-*.jar" \
    ! -name "*-sources.jar" \
    ! -name "*-javadoc.jar" \
    -print -quit
}

sdk_jar="$(find_main_jar featurevisor-sdk featurevisor-java)"
provider_jar="$(find_main_jar featurevisor-openfeature featurevisor-openfeature)"

if [[ -z "$sdk_jar" || -z "$provider_jar" ]]; then
  echo "Expected SDK and provider JARs were not found" >&2
  exit 1
fi

if jar tf "$sdk_jar" | grep '^com/featurevisor/openfeature/' >/dev/null; then
  echo "The Featurevisor SDK JAR contains OpenFeature provider classes" >&2
  exit 1
fi

if ! jar tf "$provider_jar" | grep '^com/featurevisor/openfeature/FeaturevisorOpenFeatureProvider.class$' >/dev/null; then
  echo "The OpenFeature provider JAR does not contain its provider class" >&2
  exit 1
fi

if grep -q 'dev\.openfeature' featurevisor-sdk/.flattened-pom.xml; then
  echo "The Featurevisor SDK published POM contains an OpenFeature dependency" >&2
  exit 1
fi

if ! grep -q '<artifactId>featurevisor-java</artifactId>' featurevisor-openfeature/.flattened-pom.xml; then
  echo "The provider published POM does not depend on Featurevisor Java" >&2
  exit 1
fi

if ! grep -q '<groupId>dev\.openfeature</groupId>' featurevisor-openfeature/.flattened-pom.xml; then
  echo "The provider published POM does not depend on the OpenFeature SDK" >&2
  exit 1
fi

for artifact in \
  featurevisor-sdk/target/featurevisor-java-*-sources.jar \
  featurevisor-sdk/target/featurevisor-java-*-javadoc.jar \
  featurevisor-openfeature/target/featurevisor-openfeature-*-sources.jar \
  featurevisor-openfeature/target/featurevisor-openfeature-*-javadoc.jar; do
  if ! compgen -G "$artifact" >/dev/null; then
    echo "Missing published artifact: $artifact" >&2
    exit 1
  fi
done

echo "SDK and OpenFeature provider artifacts are isolated and complete."
