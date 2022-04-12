#!/bin/bash

set -e

if [[ $# -lt 5 ]]; then
    echo "usage $0 <sam-url> <auth-token> <resource-id> <admin-group-name> <your-email>"
    exit 1
fi

RESOURCE_TYPE="catalog"
SAM_URL=$1
AUTH_TOKEN=$2
# resource_id such as broad-tdc-dev, broad-tdc-perf etc.
RESOURCE_ID=$3
ADMIN_GROUP_NAME=$4
YOUR_EMAIL=$5

# Create the admin group for the env
curl -X POST -H 'Content-Length: 0' -H "Authorization: Bearer ${AUTH_TOKEN}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}"
# Add the user as an admin of the ops group
curl -X PUT -H 'Content-Length: 0' -H "Authorization: Bearer ${AUTH_TOKEN}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}/admin/${YOUR_EMAIL}"

# create the resource id
curl -X POST -H 'Content-Length: 0' -H "Authorization: Bearer ${AUTH_TOKEN}" "${SAM_URL}/api/resources/v2/${RESOURCE_TYPE}/${RESOURCE_ID}"

# Get the email address for the ops group
GROUP_EMAIL=$(curl --header 'Content-Type: application/json' --header 'Accept: application/json' --header "Authorization: Bearer ${AUTH_TOKEN}" "${SAM_URL}/api/groups/v1/${ADMIN_GROUP_NAME}")

# add the admin policy on the resource with the admin group email
curl -X PUT -H 'Content-Type: application/json' -H 'Accept: application/json' -H "Authorization: Bearer ${AUTH_TOKEN}" \
"${SAM_URL}/api/resources/v2/${RESOURCE_TYPE}/${RESOURCE_ID}/policies/admin" \
-d '{"memberEmails": ['"${GROUP_EMAIL}"'],"actions": [], "roles": ["admin"], "descendantPermissions": []}'


