#!/bin/bash
set -e

######################
#Usage: generatePitestReport.sh <profile>
######################

cd /home/vyom/IdeaProjects/checkstyle/

export MAVEN_OPTS="-Xmx4000m -XX:MaxPermSize=512m"
mvn --no-transfer-progress -e -P"$1" clean test-compile org.pitest:pitest-maven:mutationCoverage
