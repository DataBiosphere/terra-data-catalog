#!/bin/sh

usage() {
  echo "usage: $0 <env> <create/delete>"
  exit 1
}

create_billing_project() {
  curl -X 'POST' \
    -H 'Content-Type: application/json' \
    -H "${AUTH_TOKEN}" \
    -d "{ \"projectName\": \"${PROJECT_NAME}\", \"billingAccount\": \"${BILLING_ACCT}\" }" \
    "${RAWLS_ENV}/billing/v2"
}

create_workspace() {
  curl -s -X 'POST' \
    -H 'Content-Type: application/json' \
    -H "${AUTH_TOKEN}" \
    -d "{ \"namespace\": \"${PROJECT_NAME}\", \"name\": \"${WORKSPACE_NAME}\", \"attributes\": {} }" \
    "${RAWLS_ENV}/workspaces" | jq -r .workspaceId
}

create_action() {
  create_billing_project
  create_workspace
}

delete_billing_project() {
  curl -X 'DELETE' -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/billing/v2/${PROJECT_NAME}"
}

delete_workspace() {
  curl -X 'DELETE' -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/api/workspaces/${PROJECT_NAME}/${WORKSPACE_NAME}"
}

delete_action() {
  delete_workspace
  delete_billing_project
}

AUTH_TOKEN="Authorization: Bearer $(gcloud auth print-access-token)"
BILLING_ACCT="billingAccounts/00708C-45D19D-27AAFA"
PROJECT_NAME="catalog_test_project"
WORKSPACE_NAME="catalog_test_workspace_discoverable"

if [ $# -lt 2 ]; then
  usage
fi
case $1 in
  dev | perf | alpha | staging) ;;
  *) usage ;;
esac
RAWLS_ENV="https://rawls.dsde-${1}.broadinstitute.org/api"
case $2 in
  create) create_action ;;
  delete) delete_action ;;
  *) usage ;;
esac
