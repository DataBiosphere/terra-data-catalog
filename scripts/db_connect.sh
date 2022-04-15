#!/bin/sh

# This script requires google's cloud_sql_proxy on your PATH. One way to set this up:
# $ brew cask install google-cloud-sdk
# $ gcloud components install cloud_sql_proxy
# $ PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/
#
# Use this script to connect to cloud Postgres for a specific Catalog instance.
# For example, to connect to the dev catalog database, run:
# $ ENV=dev ./db-connect.sh
#
# The proxy will continue to run until you quit it using ^C.
#
# The default port used is 5431, to avoid conflicting with a locally running postgres which
# defaults to port 5432. Use the environment variable PORT to override this setting.

: "${ENV:?}"
PORT=${PORT:-5431}

VAULT_PATH="secret/dsde/terra/kernel/${ENV}/${ENV}/catalog/postgres"

INSTANCE_DATA=$(vault read -field=data -format=json "${VAULT_PATH}/instance")
NAME=$(echo "${INSTANCE_DATA}" | jq -r '.name')
PROJECT=$(echo "${INSTANCE_DATA}" | jq -r '.project')
REGION=$(echo "${INSTANCE_DATA}" | jq -r '.region')

DB_DATA=$(vault read -field=data -format=json "${VAULT_PATH}/db-creds")
DB=$(echo "${DB_DATA}" | jq -r '.db')
USER=$(echo "${DB_DATA}" | jq -r '.username')
PASSWORD=$(echo "${DB_DATA}" | jq -r '.password')

echo "Starting a proxy for $ENV. Connection: jdbc:postgresql://localhost:$PORT/$DB?user=$USER&password=$PASSWORD"

cloud_sql_proxy -instances="${PROJECT}:${REGION}:${NAME}=tcp:${PORT}"
