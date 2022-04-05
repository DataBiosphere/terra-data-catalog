## Building the code

1. Create and activate the Python virtual environment and install dependencies:

```sh
python3 -m venv .env
source .env/bin/activate
pip install --upgrade pip
pip install black tqdm
4. generate and save a AUTH_TOKEN which is required to make API requests.
```sh
AUTH_TOKEN="Authorization: Bearer $(gcloud auth application-default print-access-token)"
```
5. run python code to extract the project and snapshot data as json files. ```create-hca-collection``` combines the two to create a collection file
```sh
python3 extract-hca-projects.py
python3 extract-hca-snapshots.py
python3 create-hca-collection
```

6. ```.env/bin/activate``` must be used to create an environment so  ```ingest-hca-collection``` script can run and to avoid a tpq error.
```sh
source .env/bin/activate
python3 ingest-hca-collection.py
```

Currrently Missing command to delete files generated after testing
