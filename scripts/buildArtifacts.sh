#!/bin/bash

# Ensure script always executes from the repository root directory
cd $(dirname $0)/..

# Clean and clone dependencies
rm -rf ci.ant
git clone --depth=1 git@github.com:OpenLiberty/ci.ant.git ci.ant

# Install dependencies
./mvnw -V clean install -ntp -f ci.ant -DskipTests
./mvnw -V clean install -ntp -DskipTests
MVN_RC=$?

exit $MVN_RC
