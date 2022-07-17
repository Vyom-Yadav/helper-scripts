#!/bin/bash
set -e

######################
#Usage: ./modifyPomPitestProfile.sh <profile>
######################

profile=$1
lead='^      <id>'$profile'<\/id>$'
tail='^              <targetClasses>$'
sed -i -e "/$lead/,/$tail/{ /$lead/{p; r /home/vyom/addition.txt
        }; /$tail/p; d }"  pom.xml

/home/vyom/IdeaProjects/helper-scripts/updateCoverageAndMutationThreshold.sh "$profile" 0 0