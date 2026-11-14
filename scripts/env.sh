# Source this before running Gradle/adb from a terminal:  source scripts/env.sh
# AGP 8.13 does not support JDK 26 (the system default here), so pin JDK 21.
export JAVA_HOME="${PLEOS_JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
