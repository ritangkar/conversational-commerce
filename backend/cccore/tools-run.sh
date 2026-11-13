#!/usr/bin/env bash
# Compiles the platform-independent engine against local stubs and runs its JUnit tests. No SAP Commerce needed.
# Libraries are downloaded once into backend/cccore/.local-libs (gitignored). Usage: backend/cccore/tools-run.sh
set -eu
cd "$(dirname "$0")"
LIB=.local-libs; mkdir -p "$LIB" build-local
M=https://repo1.maven.org/maven2
get() { [ -f "$LIB/$(basename "$1")" ] || curl -fsS -m 120 -o "$LIB/$(basename "$1")" "$M/$1"; }
get com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar
get com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar
get com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar
get org/apache/logging/log4j/log4j-api/2.23.1/log4j-api-2.23.1.jar
get org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar
get jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar
get org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar
CP=$(ls "$LIB"/*.jar | tr '\n' ':')
rm -rf build-local/classes && mkdir -p build-local/classes
javac -Xlint:-options -d build-local/classes -cp "$CP" $(find src teststubs testsrc -name '*.java')
cp -r resources/cccore build-local/classes/
java -jar "$LIB/junit-platform-console-standalone-1.10.2.jar" execute -cp "build-local/classes:$CP" --scan-classpath build-local/classes \
  --disable-banner --details=summary "$@"
