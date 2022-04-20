# How to create a catalog entry in the terra data catalog
The terra data catalog (catalog) is a service that supports creating searchable entries for shared collections of data called Datasets. Currently the catalog supports datasets that correspond to snapshots in the terra data repository (TDR).

### Catalog Dependencies

The catalog depends on data in two other services: SAM and TDR. So to create a catalog entry, you’ll need to create objects in these services too. In these examples the URLs for the dev environment are used, but any environment will work as long as it’s the same for all services.

### Authentication
All endpoints require authenticated access. You can generate a bearer token on the comment line by running:
```shell
$ TOKEN="Authorization: Bearer $(gcloud auth application-default print-access-token)"

```

## Steps to create a catalog entry

### Set up a test user

1. Set up the test user and bearer token
Create a google user account for testing, then log into that account using “gcloud auth login”.
Generate a bearer token auth header using this account by using “print-access-token”:
```shell
$ export TOKEN="Authorization: Bearer $(gcloud auth application-default print-access-token)"

```
2. Register this user in SAM.
```shell
curl -X 'POST' -H $”TOKEN”  'https://sam.dsde-dev.broadinstitute.org/register/user/v1'
```

### Create a snapshot in TDR

Create billing profile, or use an existing one.

See if you already have a billing profile ID:
```shell
PROFILE_ID=$(curl -X 'GET' -H "${TOKEN}" https://jade.datarepo-dev.broadinstitute.org/api/resources/v1/profiles | jq -r '.items[0].id')
```
If $PROFILE_ID is empty, create a new billing profile:
```shell
JOB_ID=$(curl -X POST -H 'Content-Type: application/json' -H 'Accept: application/json' \
    -H "${TOKEN}" \
    -d '{ "biller": "direct", "billingAccountId": "00708C-45D19D-27AAFA", "profileName": "testing" }' \
    https://jade.datarepo-dev.broadinstitute.org/api/resources/v1/profiles \
    | jq -r .id)
PROFILE_ID=$(curl -H "${TOKEN}" "https://jade.datarepo-dev.broadinstitute.org/api/repository/v1/jobs/${JOB_ID}/result" | jq -r '.id')
```

Create a Dataset

```shell
curl -X 'GET' -H "${TOKEN}" https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets | jq -r '.id'

curl -X 'POST' -H "${TOKEN}" \
-H 'Content-Type: application/json' \
-d '{ "storageSystem": "tdr", "storageSourceId": "32a4b90a-def0-4016-b561-c41242017846", "catalogEntry": "hello" }' \
https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets

curl -X 'GET' -H "${TOKEN}" https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets/32a4b90a-def0-4016-b561-c41242017846

curl -X 'PUT' -H "${TOKEN}" -H 'Content-Type: application/json' -d '{ "requestBody": "hello" }' https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets/32a4b90a-def0-4016-b561-c41242017846

curl -X 'DELETE' -H "${TOKEN}" https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets/32a4b90a-def0-4016-b561-c41242017846

```
