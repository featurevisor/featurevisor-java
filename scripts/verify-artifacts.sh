#!/usr/bin/env bash

set -euo pipefail

version="$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)"
sdk_jar="featurevisor-sdk/target/featurevisor-java-$version.jar"
provider_jar="featurevisor-openfeature/target/featurevisor-openfeature-$version.jar"

if [[ -z "$version" || ! -f "$sdk_jar" || ! -f "$provider_jar" ]]; then
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

for pom in .flattened-pom.xml featurevisor-sdk/.flattened-pom.xml featurevisor-openfeature/.flattened-pom.xml; do
  if ! grep -Fq "<version>$version</version>" "$pom"; then
    echo "Published POM does not match revision $version: $pom" >&2
    exit 1
  fi
done

if ! grep -A1 '<artifactId>featurevisor-java</artifactId>' featurevisor-openfeature/.flattened-pom.xml | grep -Fq "<version>$version</version>"; then
  echo "Provider dependency does not match SDK revision $version" >&2
  exit 1
fi

for artifact in \
  "featurevisor-sdk/target/featurevisor-java-$version-sources.jar" \
  "featurevisor-sdk/target/featurevisor-java-$version-javadoc.jar" \
  "featurevisor-openfeature/target/featurevisor-openfeature-$version-sources.jar" \
  "featurevisor-openfeature/target/featurevisor-openfeature-$version-javadoc.jar"; do
  if [[ ! -f "$artifact" ]]; then
    echo "Missing published artifact: $artifact" >&2
    exit 1
  fi
done

echo "SDK and OpenFeature provider artifacts are isolated and complete."
