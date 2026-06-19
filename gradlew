#!/bin/sh

APP_HOME="$(cd "$(dirname "$0")" && pwd)"

# Run gradle wrapper
exec java -Xmx4096m -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

