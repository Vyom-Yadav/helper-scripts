#!/bin/bash
set -e

######################
#Usage: pushReportToGithub.sh <profile>
######################

PROFILE=$1
cd /home/vyom/Desktop/pitest-all-latest/
git add .
git commit -m "All report for $PROFILE"
git push origin master
