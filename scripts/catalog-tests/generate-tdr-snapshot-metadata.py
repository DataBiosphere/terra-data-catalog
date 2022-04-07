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
#   vault login -method=github token=$(cat ~/.github-token)
#   vault read secret/dsde/terra/kernel/dev/dev/catalog/tests/user
#   vault read secret/dsde/terra/kernel/dev/dev/catalog/tests/userAdmin
# Run: python3 generate-tdr-snapshot-metadata.py
#------------------------------------------------------------------------------
import json
import os, subprocess, sys
import requests

urlRoot = os.environ.get('catalogServiceUrl') or 'http://localhost:8080'
urlDatasets = f'{urlRoot}/api/v1/datasets'
urlDataRepoList = "https://jade.datarepo-dev.broadinstitute.org/api/repository/v1/snapshots"
user = os.environ.get('gcloudUser') or 'datacatalogadmin@test.firecloud.org'

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

  if response.status_code == 500:
    print('---------------------------------------------------')
    print(f'There was a problem updating dataset with metadata for ({datasetId}, {datasetTitle})')
    print(json.loads(response.text)['message'])
    print('\nRequest:')
    print(f'\t{response.request.url}')
    print(f'\t{response.request.headers}')
    print(f'\t{response.request.body}')
    print('---------------------------------------------------')
  else:
    print('success!', response.text)


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

  if response.status_code == 500:
    print('---------------------------------------------------')
    print(f'There was a problem creating dataset with metadata for ({datasetId}, {datasetTitle})')
    print(json.loads(response.text)['message'])
    print('\nRequest:')
    print(f'\t{response.request.url}')
    print(f'\t{response.request.headers}')
    print(f'\t{response.request.body}')
    print('---------------------------------------------------')
  else:
    print('success!', response.text)

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
