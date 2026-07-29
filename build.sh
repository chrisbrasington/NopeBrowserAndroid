#!/usr/bin/env bash
# Build the debug APK in a container (no Android tooling needed on the host).
# Output:
#   dist/chrisincode-render.apk
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=chrisincode-render-build
KEYSTORE=app/render.keystore

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

echo "==> Assembling debug APK…"
podman run --rm -v "$PWD":/work:Z -w /work "$IMAGE" \
    gradle --no-daemon assembleDebug

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/chrisincode-render.apk
echo "==> Done: dist/chrisincode-render.apk"
echo "    adb install -r dist/chrisincode-render.apk"
