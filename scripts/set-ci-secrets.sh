#!/usr/bin/env bash
# Adds the release signing key to this repo's GitHub Actions secrets, so every tagged build
# (v0.2.0, …) is signed with the same key as v0.1.0 and installs as an update.
# Usage: scripts/set-ci-secrets.sh path/to/depth-release.jks 'store-password'
set -euo pipefail
jks="${1:?path to depth-release.jks}"; pw="${2:?keystore password}"
repo="${REPO:-au12/Depth-Android}"
base64 < "$jks" | tr -d '\n' | gh secret set DEPTH_KEYSTORE_BASE64 --repo "$repo"
printf %s "$pw" | gh secret set DEPTH_KEYSTORE_PASSWORD --repo "$repo"
printf %s "$pw" | gh secret set DEPTH_KEY_PASSWORD --repo "$repo"
printf depth   | gh secret set DEPTH_KEY_ALIAS --repo "$repo"
echo "Done. Tag a release (git tag v0.2.0 && git push --tags) to publish a signed build."
