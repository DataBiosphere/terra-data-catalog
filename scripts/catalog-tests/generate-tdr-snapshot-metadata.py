#------------------------------------------------------------------------------
# Generate TDR Snapshot Metadata
# - tickets: DC-221, DC-220
#
# This script adds snapshots to the catalog as the test user from DC-218,
# pulling the metadata from the resources JSON file
# 
# Before Running:
#   python3 -m pip install requests
#   
#   Authorization: Access the password in the vault using the appropriate command:
#   vault read secret/dsde/terra/kernel/dev/dev/catalog/tests/user
#   vault read secret/dsde/terra/kernel/dev/dev/catalog/tests/userAdmin
# Run: python3 generate-tdr-snapshot-metadata.py datasetId datasetId2
#------------------------------------------------------------------------------
import json
import os, subprocess, sys
import requests

urlRoot = 'http://localhost:8080'
user = 'datacataloguser@test.firecloud.org'

def getAccessToken():
  proc = subprocess.Popen([f'gcloud auth login {user} --brief'], stdout=subprocess.PIPE, shell=True)
  proc.communicate()
  
  printProc = subprocess.Popen(['gcloud auth print-access-token'], stdout=subprocess.PIPE, shell=True)
  (out, err) = printProc.communicate()

  return out.decode('ASCII').strip()

def updateMetadataInCatalog(datasetId, resourceJson, accessToken):
  if datasetId in resourceJson:
    print(datasetId, ': updating with metadata')
    url = f'{urlRoot}/api/v1/datasets/{datasetId}'
    headers = {
      'Authorization': f'Bearer {accessToken}',
      'Content-Type': 'application/json'
    }
    existsResponse = requests.get(url, headers=headers)
    if existsResponse.status_code == 200:
      print('Updating existing dataset... PUT /api/v1/datasets/${id}')
    elif existsResponse.status_code == 500:
      print('Adding new dataset... POST /api/v1/datasets')
      addResponse = requests.post(f'{urlRoot}/api/v1/datasets', headers=headers, data=json.dumps(resourceJson[datasetId]))
      print('errored?', addResponse.json())
    else:
      print('There was an unexpected error, ignored')
  else:
    print(datasetId, ': ignored')

def main():
  print('Generating TDR Snapshot Metadata')

  # Get dataset ids from cmd line
  datasetIds = sys.argv[1:]

  # Obtain google user credentials
  accessToken = getAccessToken()

  # Load resource file information
  resourceFile = open('resources.json')
  resourceJson = json.load(resourceFile)

  for datasetId in datasetIds:
    updateMetadataInCatalog(datasetId, resourceJson, accessToken)

  # Close file
  resourceFile.close()

main()
