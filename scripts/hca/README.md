## Running the code

1. Create and activate the Python virtual environment and install dependencies:

```sh
python3 -m venv .env
source .env/bin/activate
pip install --upgrade pip
pip install black tqdm
```

3. Generate and save an `AUTH_TOKEN` which is required to make API requests:

```sh
export AUTH_TOKEN="Authorization: Bearer $(gcloud auth application-default print-access-token)"
```

4. Extract the HCA project and snapshot data as JSON files. `create-hca-collection.py` combines the two to create a collection file.

```sh
python3 extract-hca-projects.py
python3 extract-hca-snapshots.py
python3 create-hca-collection.py
```

5. `.env/bin/activate` must be used to activate the virtual env to avoid a `tqdm` error in `ingest-hca-collection.py`.

```sh
source .env/bin/activate
export USER_EMAIL=datacatalogadmin@test.firecloud.org
# OR export your personal dev admin email address
python3 ingest-hca-collection.py
```

