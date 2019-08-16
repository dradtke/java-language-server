#!/bin/bash
# Patch Gson with module-info.java
# When Gson releases a new version, we will no longer need to do this 
# https://github.com/google/gson/issues/1315

set -e

# Check JAVA_HOME points to java 11
./scripts/check_java_home.sh

# Download jar
cd modules/gradle-tooling-api
curl https://repo.gradle.org/gradle/libs-releases-local/org/gradle/gradle-tooling-api/5.5.1/gradle-tooling-api-5.5.1.jar > gradle-tooling-api.jar
curl https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.28/slf4j-api-1.7.28.jar > slf4j-api.jar

# Unpack jar into modules/classes
mkdir classes
cd classes
$JAVA_HOME/bin/jar xf ../gradle-tooling-api.jar
$JAVA_HOME/bin/jar xf ../slf4j-api.jar
cd ..

# Compile module-info.java to module-info.class
$JAVA_HOME/bin/javac -p org.gradle.tooling -d classes module-info.java

# Update jar with module-info.class
$JAVA_HOME/bin/jar uf gradle-tooling-api.jar -C classes org/slf4j
$JAVA_HOME/bin/jar uf gradle-tooling-api.jar -C classes module-info.class

# Clean up
rm -rf classes
cd ../..
