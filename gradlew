#!/bin/sh
# Gradle wrapper script
APP_HOME=$(cd "$(dirname "$0")" && pwd)
if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    cd "$APP_HOME"
    gradle wrapper --gradle-version=8.5
fi
exec "$JAVACMD" \
    -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    "$@"
