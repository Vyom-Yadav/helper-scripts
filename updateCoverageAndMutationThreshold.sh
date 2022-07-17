#!/bin/bash
set -e

######################
#Usage: ./updateCoverageAndMutationThreshold.sh <profile> <coverage-threshold> <mutation-threshold>
######################

profile=$1
coverageThreshold=$2
mutationThreshold=$3

perl -i -wlpE'if ($on) { $on = 0 if s/              <coverageThreshold>.*<\/coverageThreshold>/              <coverageThreshold>'"$coverageThreshold"'<\/coverageThreshold>/ }; $on = 1 if /^      <id>'"$profile"'<\/id>$/' pom.xml;
perl -i -wlpE'if ($on) { $on = 0 if s/              <mutationThreshold>.*<\/mutationThreshold>/              <mutationThreshold>'"$mutationThreshold"'<\/mutationThreshold>/ }; $on = 1 if /^      <id>'"$profile"'<\/id>$/' pom.xml;
