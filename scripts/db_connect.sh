#!/bin/sh

# This script requires google's cloud_sql_proxy on your PATH. One way to set this up:
# $ brew cask install google-cloud-sdk
# $ gcloud components install cloud_sql_proxy
# $ PATH="$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin"
# OR
# $ ln -s /usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/cloud_sql_proxy /usr/local/bin/cloud_sql_proxy
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

INSTANCE=$(vault read -field=data -format=json "${VAULT_PATH}/instance" |
           jq -r '"\(.project):\(.region):\(.name)"')=tcp:$PORT

DB_CREDS_DATA=$(vault read -field=data -format=json "${VAULT_PATH}/db-creds")

JDBC_URL=jdbc:postgresql://localhost:$PORT/$(echo "${DB_CREDS_DATA}" |
          jq -r '"\(.db)?user=\(.username)&password=\(.password)"')

PSQL_COMMAND=$(echo "${DB_CREDS_DATA}" |
          jq -r '"psql postgresql://\(.username):\(.password)@localhost/\(.db)\\?port="')$PORT

echo "Starting a proxy for $ENV. Connect using: \"$JDBC_URL\" or run: \"$PSQL_COMMAND\""

cloud_sql_proxy -instances="${INSTANCE}" -dir=/tmp
