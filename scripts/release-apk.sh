#!/bin/bash
# Builds the signed release APK into dist/omarchy-remote-<version>.apk.
# The signing key stays outside the repository: ~/.config/omarchy-remote-release/signing.properties
# (or OMARCHY_REMOTE_SIGNING=<file>). Back that folder up: without the key, no update installs over this one.
set -euo pipefail
cd "$(dirname "$0")/.."
# The Android Gradle plugin here needs Java 17 (a newer default JDK fails with just "27").
[[ -d /usr/lib/jvm/java-17-openjdk ]] && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew -q :app:assembleRelease
version=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/omarchy-remote-$version.apk"
"$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | tail -1)/apksigner" verify "dist/omarchy-remote-$version.apk"
echo "dist/omarchy-remote-$version.apk ($(du -h "dist/omarchy-remote-$version.apk" | cut -f1)), signed"
