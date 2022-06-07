#!/bin/sh
# ------------------------------------------------------------------------------
# Manage test workspaces
#
# A script to manually create, list, and delete test workspaces for data catalog.
#
# Usage:
#   ./manage-test-workspaces.sh <env> <create/delete_wks/delete_prj/list>
#
#   env           should be one of dev, perf, alpha, or staging
#
#   create        creates a new workspace
#   delete_wks    deletes all test workspaces
#   delete_prj    deletes the workspace billing project (not recommended)
#   list          lists all test workspaces
# ------------------------------------------------------------------------------
set -eu

usage() {
  echo "usage: $0 <env> <create/delete_wks/delete_prj/list>"
  exit 1
}

exists_billing_project() {
  curl -X 'GET' \
    -o /dev/null \
    -s -w '%{http_code}' \
    -H 'accept: application/json' \
    -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/billing/v2/${PROJECT_NAME}"
}

create_billing_project() {
  curl -X 'POST' \
    -H 'Content-Type: application/json' \
    -H "${AUTH_TOKEN}" \
    -d "{ \"projectName\": \"${PROJECT_NAME}\", \"billingAccount\": \"${BILLING_ACCT}\" }" \
    "${RAWLS_ENV}/billing/v2"
}

create_workspace() {
  WORKSPACE_UUID=$(uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]')
  WORKSPACE_NAME="catalog_workspace_test_${WORKSPACE_UUID}"
  curl -s -X 'POST' \
    -H 'Content-Type: application/json' \
    -H "${AUTH_TOKEN}" \
    -d "{ \"namespace\": \"${PROJECT_NAME}\", \"name\": \"${WORKSPACE_NAME}\", \"attributes\": {} }" \
    "${RAWLS_ENV}/workspaces"
}

create_action() {
  if [ "$(exists_billing_project)" = "404" ]; then
    create_billing_project
  fi
  create_workspace
}

delete_billing_project() {
  curl -X 'DELETE' -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/billing/v2/${PROJECT_NAME}"
}

list_workspaces() {
  curl -s -X 'GET' \
    -H 'accept: application/json' \
    -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/workspaces" | jq -r '.[].workspace | select(.namespace == "'"${PROJECT_NAME}"'") | [ .namespace, .name ] | join("/")'
}

delete_workspace() {
  curl -X 'DELETE' -H "${AUTH_TOKEN}" \
    "${RAWLS_ENV}/workspaces/$1"
}

delete_all_workspaces() {
  for WORKSPACE_WITH_NAMESPACE in $(list_workspaces); do
    delete_workspace "$WORKSPACE_WITH_NAMESPACE"
    printf "\n"
  done
}

AUTH_TOKEN="Authorization: Bearer $(gcloud auth print-access-token)"
BILLING_ACCT="billingAccounts/00708C-45D19D-27AAFA"
PROJECT_NAME="catalog_test_project"

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
  delete_wks) delete_all_workspaces ;;
  delete_prj) delete_billing_project ;;
  list) list_workspaces ;;
  *) usage ;;
esac
