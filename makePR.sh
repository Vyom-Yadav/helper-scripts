#!/bin/bash
set -e

######################
#Usage: ./makePR.sh <profile>
######################

cd /home/vyom/IdeaProjects/checkstyle/

if [ -z "$1" ]; then
  echo "No Branch name provided"
  exit 1
fi

PROFILE=$1

BRANCH_NAME="activate-all-group-$PROFILE"

CURRENT_STATUS=$(git status)

if [[ ! ($CURRENT_STATUS =~ "On branch master") ]]; then
  echo "Not on master branch, exiting....."
  exit 1
fi

git checkout -b "$BRANCH_NAME"

git add .
git commit -m "Issue #11719: Activate all group for $PROFILE"
git push --set-upstream origin "$BRANCH_NAME"
TITLE="Issue #11719: Activate all group for $PROFILE"
LINK="https://vyom-yadav.github.io/pitest-all-latest/$PROFILE/index.html"

gh pr create -t "$TITLE" -b "
#11719
Activating \`ALL\` group for \`$PROFILE\`

Every mutator has been activated except \`INLINE_CONSTS\`

- Report with \`ALL\` group except \`INLINE_CONSTS\`: $LINK
" -B "master"

echo "Checking out back to the master branch..."

git checkout master

echo "Done"
