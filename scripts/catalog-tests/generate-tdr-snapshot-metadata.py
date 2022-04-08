#------------------------------------------------------------------------------
# Generate TDR Snapshot Metadata
#
# This script adds snapshots to the catalog as the test user from DC-218,
# pulling the metadata from the resources JSON file
# 
# Before Running:
#   python3 -m venv .env
#   source .env/bin/activate
#   pip install --upgrade pip
#   python3 -m pip install requests
#   
#   Authorization: Access the password in the vault using the appropriate command:
#      (you will need this for the auth dialog that the script will pull up)
#   vault login -method=github token=$(cat ~/.github-token)
#   vault read secret/dsde/terra/kernel/dev/dev/catalog/tests/userAdmin
#
# Run:
#   `python3 generate-tdr-snapshot-metadata.py`
#   `GCLOUD_USER={your-email} python3 generate-tdr-snapshot-metadata.py`
#
# Environment Variables:
#   GCLOUD_USER: user account email
#   CATALOG_SERVICE_URL: url for the catalog service to update the updata in
#   DATA_REPO_URL: url for the data repo to use to extract testing snapshot information
#------------------------------------------------------------------------------
import json
import os, subprocess, sys
import requests

urlRoot = os.environ.get('CATALOG_SERVICE_URL') or 'http://localhost:8080'
urlDatasets = f'{urlRoot}/api/v1/datasets'
urlDataRepoList = os.environ.get('DATA_REPO_URL') or 'https://jade.datarepo-dev.broadinstitute.org/api/repository/v1/snapshots'
user = os.environ.get('GCLOUD_USER') or 'datacatalogadmin@test.firecloud.org'

def logResponse(response, message):
  if 200 <= response.status_code and response.status_code < 300:
    print('success!', response.text)
  else:
    print('---------------------------------------------------')
    print(f'There was a problem running this request: ', message)
    print(json.loads(response.text)['message'])
    print('\nRequest:')
    print(f'\t{response.request.url}')
    print(f'\t{response.request.headers}')
    print(f'\t{response.request.body}')
    print('---------------------------------------------------')

def getAccessToken():
  proc = subprocess.Popen([f'gcloud auth login {user} --brief'], stdout=subprocess.PIPE, shell=True)
  proc.communicate()
  
  printProc = subprocess.Popen(['gcloud auth print-access-token'], stdout=subprocess.PIPE, shell=True)
  (out, err) = printProc.communicate()

  return out.decode('ASCII').strip()

def updateMetadataInCatalog(resourceJson, accessToken):
  print('Updating metadata for datasets in the catalog')
  headers = {
    'Authorization': f'Bearer {accessToken}',
    'Content-Type': 'application/json'
  }

  # Get dataset list once in case of collision later
  response = requests.get(urlDatasets, headers=headers)
  responseData = json.loads(response.text)
  datasetList = responseData['result'] if 'result' in responseData else []
  datasetMap = {}
  print('loaded dataset list from catalog service')

  for dataset in datasetList:
    datasetMap[dataset['dct:identifier']] = dataset['id']

  for resource in resourceJson:
    existsResponse = requests.get(f'{urlDataRepoList}?filter={resource}', headers=headers)
    print('retrieved snapshot from TDR')
    tdrDatasetId = json.loads(existsResponse.text)['items'][0]['id']
    catalogEntry = resourceJson[resource]
    catalogEntry['dct:identifier'] = tdrDatasetId

    if tdrDatasetId in datasetMap:
      updateDataset(datasetMap[tdrDatasetId], resource, catalogEntry, accessToken)
    else:
      createDataset(tdrDatasetId, resource, catalogEntry, accessToken)

def updateDataset(datasetId, datasetTitle, metadata, accessToken):
  print(f'\nUpdating existing dataset ({datasetId}, {datasetTitle})')
  headers = {
    'Authorization': f'Bearer {accessToken}',
    'Content-Type': 'application/json'
  }
  response = requests.put(f'{urlDatasets}/{datasetId}', headers=headers, data=json.dumps(metadata))
  logResponse(response, f'updating dataset with metadata for ({datasetId}, {datasetTitle})')

def createDataset(datasetId, datasetTitle, metadata, accessToken):
  print(f'\nCreating dataset ({datasetId}, {datasetTitle})')
  headers = {
    'Authorization': f'Bearer {accessToken}',
    'Content-Type': 'application/json'
  }
  metadata = {
      'storageSystem': 'tdr',
      'storageSourceId': datasetId,
      'catalogEntry': json.dumps(metadata)
    }
  response = requests.post(urlDatasets, headers=headers, data=json.dumps(metadata))
  logResponse(response, f'creating dataset with metadata for ({datasetId}, {datasetTitle})')

def main():
  print('Adding TDR Snapshot Metadata')

  # Obtain google user credentials
  accessToken = getAccessToken()

  # Load resource file information
  resourceFile = open('resources.json')
  resourceJson = json.load(resourceFile)

  updateMetadataInCatalog(resourceJson, accessToken)

  # Close file
  resourceFile.close()

main()
