#!/usr/bin/env bash
# Build the debug APK in a container (no Android tooling needed on the host).
# Output:
#   dist/NopeBrowser-<versionName>.apk
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=chrisincode-render-build
KEYSTORE=app/render.keystore
APK=app/build/outputs/apk/debug/app-debug.apk

echo "==> Building Android SDK image (first run is slow; cached afterwards)…"
podman build -t "$IMAGE" -f Containerfile .

# Stable, checked-in signing key so every rebuild (on any machine) signs
# identically — otherwise `adb install -r` fails with a signature mismatch. A
# container has no persistent ~/.android/debug.keystore, so the default debug key
# would be different every run. This is a throwaway key for a sideloaded app, not
# a secret.
if [[ ! -f "$KEYSTORE" ]]; then
    echo "==> Generating signing key ($KEYSTORE)…"
    podman run --rm -v "$PWD":/work:Z -w /work "$IMAGE" \
        keytool -genkeypair -v \
            -keystore "$KEYSTORE" \
            -alias render \
            -keyalg RSA -keysize 4096 -validity 10000 \
            -storepass renderpass -keypass renderpass \
            -dname "CN=Minimal Web Renderer, O=chrisincode.com, C=US"
fi

# NOPE_WHITELIST replaces the sample whitelist in res/values/whitelist.xml — see
# the comment at the top of app/build.gradle.kts. A container inherits nothing,
# so it has to be handed over explicitly; empty means "use the sample".
echo "==> Assembling debug APK…"
podman run --rm -v "$PWD":/work:Z -w /work \
    -e NOPE_WHITELIST="${NOPE_WHITELIST:-}" "$IMAGE" \
    gradle --no-daemon assembleDebug

# Read the version out of the APK rather than parsing build.gradle.kts, so the
# filename cannot disagree with what is actually inside. aapt2 is not on the
# image's PATH, hence the glob.
BADGING=$(podman run --rm -v "$PWD":/work:Z -w /work "$IMAGE" \
    bash -lc "/opt/android-sdk/build-tools/*/aapt2 dump badging $APK | head -1")
VERSION=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")

if [[ -z "$VERSION" ]]; then
    echo "!! Could not read versionName from $APK" >&2
    echo "   aapt2 said: $BADGING" >&2
    exit 1
fi

OUT="dist/NopeBrowser-${VERSION}.apk"
mkdir -p dist
cp "$APK" "$OUT"

echo "==> Done: $OUT"
echo "    adb install -r $OUT"
