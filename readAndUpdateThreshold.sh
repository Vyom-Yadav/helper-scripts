#!/bin/bash
set -e

profile=$1
threshold=$(grep -m 2 '<div class="coverage_bar"><div class="coverage_complete' /home/vyom/Desktop/pitest-all-latest/"$profile"/index.html | sed -e 's/[^0-9]*\([0-9]*\).*/\1/')
# shellcheck disable=SC2206
threshold=(${threshold//\s/ })

/home/vyom/IdeaProjects/helper-scripts/updateCoverageAndMutationThreshold.sh "$profile" "${threshold[0]}" "${threshold[1]}"