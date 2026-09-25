#!/bin/zsh

set -euo pipefail

project_dir="${0:A:h:h}"
configuration="${1:-release}"
app_dir="$project_dir/dist/Clickify.app"
contents_dir="$app_dir/Contents"
binary_dir="$(cd "$project_dir" && swift build -c "$configuration" --show-bin-path)"
# Ad-hoc signing ("-") produces a designated requirement of cdhash, and the cdhash changes with every build —
# so every granted permission is invalidated on each install. When a stable certificate exists, use it, so that
# granting the permission once is enough. Create one with ./scripts/create-local-signing-identity.sh
local_identity="Clickify Local Signing"
if [ -n "${CLICKIFY_SIGNING_IDENTITY:-}" ]; then
    signing_identity="$CLICKIFY_SIGNING_IDENTITY"
elif security find-identity -p codesigning | grep -q "$local_identity"; then
    signing_identity="$local_identity"
else
    signing_identity="-"
fi

cd "$project_dir"
swift build -c "$configuration"

mkdir -p "$contents_dir/MacOS" "$contents_dir/Resources"
cp "$binary_dir/Clickify" "$contents_dir/MacOS/Clickify"
cp "$project_dir/Resources/Info.plist" "$contents_dir/Info.plist"
# LC-3: the .lproj directories go straight into Contents/Resources, where `Bundle.main` finds them the
# ordinary way. Deliberately not a SwiftPM resource bundle: `Bundle.module` resolves through an absolute
# path into the build machine's .build/, so it works here and crashes on any other machine (LC-4).
# Rebuilt from scratch so a language removed from the repository also leaves the bundle.
rm -rf "$contents_dir/Resources"
mkdir -p "$contents_dir/Resources"
cp -R "$project_dir/Resources/"*.lproj "$contents_dir/Resources/"

codesign --force --deep --sign "$signing_identity" "$app_dir"

echo "$app_dir"
