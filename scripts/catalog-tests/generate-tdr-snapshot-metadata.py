#------------------------------------------------------------------------------
# Generate TDR Snapshot Metadata
#
# This script adds snapshots to the catalog as the test user from DC-218,
# pulling the metadata from the resources JSON file
# 
# Before Running:
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
user = os.environ.get('gcloudUser') or 'datacataloguser@test.firecloud.org'

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
  existsResponse = requests.get(urlDatasets, headers=headers)
  datasetList = json.loads(existsResponse.text)['result']
  print('datasetList', datasetList)

  for dataset in datasetList:
    datasetId = dataset['id']
    datasetTitle = dataset['dct:title']
    if datasetTitle in resourceJson:
      metadata = json.dumps(resourceJson[datasetTitle])
      updateMetadataForDataset(datasetId, datasetTitle, metadata, accessToken)
    else:
      print(f'No testing metadata found for this dataset name/type: {datasetTitle}')

def updateMetadataForDataset(datasetId, datasetTitle, metadata, accessToken):
  print(f'\nUpdating dataset ({datasetId}, {datasetTitle})')
  urlDatasetsWithId = f'{urlDatasets}/{datasetId}'
  headers = {
    'Authorization': f'Bearer {accessToken}',
    'Content-Type': 'application/json'
  }

  addResponse = requests.put(urlDatasetsWithId, headers=headers, data=metadata)
  if addResponse.status_code == 500:
    print('---------------------------------------------------')
    print(f'There was a problem updating metadata for ({datasetId}, {datasetTitle})')
    print(json.loads(addResponse.text)['message'])
    print('\nRequest:')
    print(f'\t{addResponse.request.url}')
    print(f'\t{addResponse.request.headers}')
    print(f'\t{addResponse.request.body}')
    print('---------------------------------------------------')

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
