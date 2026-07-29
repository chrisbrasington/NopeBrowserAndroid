# Reproducible Android build image: Gradle + JDK 17 + the Android SDK pieces that
# AGP 8.7 / compileSdk 35 needs. Used only to assemble the APK — see build.sh.
#
# Deliberately a separate image from youtube-zero/android-screen: that one pins
# Gradle 8.7 + SDK 34, and AGP 8.7.2 requires Gradle 8.9 or newer.
FROM docker.io/library/gradle:8.10.2-jdk17

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends unzip wget ca-certificates; \
    rm -rf /var/lib/apt/lists/*; \
    mkdir -p "$ANDROID_HOME/cmdline-tools"; \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip; \
    unzip -q /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"; \
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"; \
    rm /tmp/cmdtools.zip; \
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null; \
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
        "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

ENV PATH="${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools"
