#!/bin/bash

# run separately with env as dev, perf, staging, alpha and prod
# you can get your auth token by calling any SAM API and copy pasting the authorization header
# your email should be whatever you use to authenticate in that environment, such as your dev account for dev

set -e

if [[ $# -lt 3 ]]; then
    echo "usage $0 <env> <auth-token> <your-email>"
    exit 1
fi

RESOURCE_TYPE="catalog"

ENV=$1
SAM_URL="https://sam.dsde-${ENV}.broadinstitute.org"
RESOURCE_ID="catalog-${ENV}"
ADMIN_GROUP_NAME="CatalogAdmins-${ENV}"

AUTH_TOKEN=$2
AUTH="Authorization: Bearer ${AUTH_TOKEN}"

YOUR_EMAIL=$3

# Create the admin group for the env
curl -X POST -H 'Content-Length: 0' -H "${AUTH}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}"
# Add the user as an admin of the ops group
curl -X PUT -H 'Content-Length: 0' -H "${AUTH}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}/admin/${YOUR_EMAIL}"

# create the resource id
curl -X POST -H 'Content-Length: 0' -H "${AUTH}" "${SAM_URL}/api/resources/v2/${RESOURCE_TYPE}/${RESOURCE_ID}"

# Get the email address for the ops group
GROUP_EMAIL=$(curl -H 'Content-Type: application/json' -H 'Accept: application/json' -H "${AUTH}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}")

# add the admin policy on the resource with the admin group email
curl -X PUT -H 'Content-Type: application/json' -H 'Accept: application/json' -H "${AUTH}" \
  "${SAM_URL}/api/resources/v2/${RESOURCE_TYPE}/${RESOURCE_ID}/policies/admin" \
  -d '{"memberEmails": ['"${GROUP_EMAIL}"'],"actions": [], "roles": ["admin"], "descendantPermissions": []}'


