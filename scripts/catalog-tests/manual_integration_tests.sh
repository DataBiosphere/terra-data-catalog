#!/bin/sh

# script for manually triggering integration-tests.yml github workflow
# you should modify the ~/.gh_token to the location and name of your github token
# change "environment": "alpha" to the env you want to run

set -eu

CUR_BRANCH=$(git branch --show-current)
curl -H "Authorization: token $(cat ~/.gh_token)" \
  --request POST --data '{"ref": "'"${CUR_BRANCH}"'", "inputs": {"environment": "alpha"}}' \
  https://api.github.com/repos/DataBiosphere/terra-data-catalog/actions/workflows/integration-tests.yml/dispatches
