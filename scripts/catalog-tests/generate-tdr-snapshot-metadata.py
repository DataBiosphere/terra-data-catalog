#------------------------------------------------------------------------------
# Generate TDR Snapshot Metadata
# - tickets: DC-221, DC-220
#
# This script adds snapshots to the catalog as the test user from DC-218,
# pulling the metadata from the resources JSON file
# 
# Before Running: python3 -m pip install requests
# Run: python3 generate-tdr-snapshot-metadata.py datasetId datasetId2
#------------------------------------------------------------------------------
import json, os
import sys
import requests

urlRoot = 'http://localhost:8080'

def updateMetadataInCatalog(datasetId, resourceJson):
  if datasetId in resourceJson:
    print(datasetId, ': updating with metadata')
    ses = requests.session()
    # SOMEHOW attach the auth token for test user
    # ses.headers.update({ [auth]: token })
    r = ses.get(f'{urlRoot}/api/v1/datasets/{datasetId}', timeout=60)
    if r.status_code == 200:
      print('PUT /api/v1/datasets/${id}')
    else:
      print('POST /api/v1/datasets')
  else:
    print(datasetId, ': ignored')

def main():
  print('Generating TDR Snapshot Metadata')

  # Get dataset ids from cmd line
  datasetIds = sys.argv[1:]

  # Load resource file information
  resourceFile = open('resources.json')
  resourceJson = json.load(resourceFile)

  for datasetId in datasetIds:
    updateMetadataInCatalog(datasetId, resourceJson)

  # Close file
  resourceFile.close()

main()