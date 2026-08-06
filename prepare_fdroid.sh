#!/bin/bash
set -euo pipefail

# Builds the file that goes into an fdroiddata merge request.
#
# That is one YAML file and nothing else. F-Droid reads the app's name, summary, description,
# changelogs and screenshots out of the fastlane/ directory in this repository, so putting any
# of those in fdroiddata makes its CI reject the merge request.

APP_ID="com.searchlauncher.app"
TEMPLATE="fdroid/$APP_ID.yml"
OUTPUT_DIR="fdroid_submission"
COMMIT_PLACEHOLDER="COMMIT_RESOLVED_FROM_TAG"

[ -f "$TEMPLATE" ] || { echo "Error: $TEMPLATE not found" >&2; exit 1; }

# The build block F-Droid should build. Only the newest one is ever hand-written: once the app is
# in fdroiddata, AutoUpdateMode adds the entries for later releases automatically.
version_name=$(grep -E '^  - versionName:' "$TEMPLATE" | tail -1 | sed 's/.*versionName: *//')
version_code=$(grep -E '^    versionCode:' "$TEMPLATE" | tail -1 | sed 's/.*versionCode: *//')
tag="v$version_name"

if ! commit=$(git rev-parse --verify --quiet "$tag^{commit}"); then
  echo "Error: $TEMPLATE builds $version_name, but tag $tag does not exist." >&2
  echo "Tag the release commit first: git tag $tag && git push origin $tag" >&2
  exit 1
fi

# F-Droid builds whatever the tag pointed at when it was fetched, so the versions baked into that
# commit have to be the ones the metadata promises. Catching this here beats finding out from a
# failed pipeline.
tagged_build_file=$(git show "$tag:app/build.gradle.kts")
tagged_name=$(echo "$tagged_build_file" | grep -E '^ *versionName = ' | head -1 | sed 's/.*= *"\(.*\)".*/\1/')
tagged_code=$(echo "$tagged_build_file" | grep -E '^ *versionCode = ' | head -1 | sed 's/.*= *//')

if [ "$tagged_name" != "$version_name" ] || [ "$tagged_code" != "$version_code" ]; then
  echo "Error: $tag builds $tagged_name ($tagged_code), but $TEMPLATE claims $version_name ($version_code)." >&2
  exit 1
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/metadata"
sed "s/$COMMIT_PLACEHOLDER/$commit/" "$TEMPLATE" > "$OUTPUT_DIR/metadata/$APP_ID.yml"

if grep -q "$COMMIT_PLACEHOLDER" "$OUTPUT_DIR/metadata/$APP_ID.yml"; then
  echo "Error: commit placeholder was not substituted." >&2
  exit 1
fi

cat <<EOF

Prepared $OUTPUT_DIR/metadata/$APP_ID.yml
  version $version_name ($version_code) at $tag -> $commit

Next steps:
  1. Fork https://gitlab.com/fdroid/fdroiddata and clone it.
  2. cp $OUTPUT_DIR/metadata/$APP_ID.yml /path/to/fdroiddata/metadata/
  3. cd /path/to/fdroiddata && fdroid readmeta \\
       && fdroid rewritemeta $APP_ID && git diff --exit-code \\
       && fdroid lint $APP_ID \\
       && fdroid checkupdates --allow-dirty $APP_ID \\
       && fdroid build $APP_ID:$version_code
  4. Open a merge request using the "App Inclusion" template and tick its boxes.
EOF
