#!/bin/sh

set -eu

TOKEN=$(cat "$HOME/.github-token")
TEST_ENV="perf"

# ensure the script always runs in the parent directory terra-data-catalog
CWD=$(dirname "$(dirname "$(readlink -f "$0")")")
cd "$CWD"

# get the helm chart versions for the perf env
curl -H "Authorization: token ${TOKEN}" \
    -H 'Accept: application/vnd.github.v3.raw' \
    -L https://api.github.com/repos/broadinstitute/terra-helmfile/contents/versions/app/dev.yaml \
    --create-dirs -o "integration/src/main/resources/rendered/dev.yaml"
curl -H "Authorization: token ${TOKEN}" \
    -H 'Accept: application/vnd.github.v3.raw' \
    -L https://api.github.com/repos/broadinstitute/terra-helmfile/contents/environments/live/perf.yaml \
    --create-dirs -o "integration/src/main/resources/rendered/$TEST_ENV.yaml"

# render the service account secrets
vault read secret/dsde/firecloud/dev/common/firecloud-sa -format=json | \
    jq -r .data.key | base64 -d > "integration/src/main/resources/rendered/user-delegated-sa.json"

vault read secret/dsde/terra/kernel/perf/common/testrunner/testrunner-sa -format=json | \
    jq -r .data.key | base64 -d > "integration/src/main/resources/rendered/testrunner-perf.json"

# run the perf test suite
./gradlew --build-cache runTest --args="suites/$TEST_ENV/FullIntegration.json build/reports"
./gradlew --build-cache uploadResults --args="CompressDirectoryToTerraKernelK8S.json build/reports"
