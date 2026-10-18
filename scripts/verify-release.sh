#!/usr/bin/env bash
set -euo pipefail

tag="${1:?Pass a release tag}"
if [[ ! "$tag" =~ ^v5\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "Expected a Featurevisor Java v5 semantic version tag" >&2
  exit 1
fi
version="${tag#v}"
if ! grep -Fq "<revision>$version</revision>" pom.xml; then
  echo "Tag $tag does not match the Maven revision" >&2
  exit 1
fi
if [[ "$(grep -Fc "<version>$version</version>" README.md)" != 2 ]]; then
  echo "SDK and provider README installation versions must match $tag" >&2
  exit 1
fi
if ! grep -Fq "version = \"$version\"" featurevisor-sdk/src/main/java/com/featurevisor/cli/CLI.java; then
  echo "CLI version does not match $tag" >&2
  exit 1
fi
echo "Validated Featurevisor Java $version"
