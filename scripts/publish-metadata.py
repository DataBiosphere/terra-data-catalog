import argparse
import sys
import requests
import json
import subprocess

DESCRIPTION = """
Uploads a given block of metadata to the Terra Data Catalog

You must have the following installed to run this script:
# The Python library `requests`
# The Google Cloud SDK

# To set up a venv with requests installed, follow the instructions below:
#   python3 -m venv .env
#   source .env/bin/activate
#   pip install --upgrade pip
#   python3 -m pip install requests

To install the google cloud sdk, follow instructions here:
 https://github.com/DataBiosphere/terra-data-catalog/blob/main/docs/getting-started.md#5-install-homebrew

To run this script you must authed as an "admin" on the
 specified environment's catalog resource

"""


def generate_access_token(user):
    auth_as_user(user)

    print_proc = subprocess.Popen(
        ["gcloud auth print-access-token"], stdout=subprocess.PIPE, shell=True
    )
    (out, err) = print_proc.communicate()

    return out.decode("ASCII").strip()


def auth_as_user(user):
    proc = subprocess.Popen(
        [f"gcloud auth login {user} --brief"], stdout=subprocess.PIPE, shell=True
    )
    proc.communicate()


def create_dataset(env, access_token, storage_system, storage_source_id, entry):
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
    }

    if "PLACEHOLDER" in json.dumps(entry):
        print("!!!!!")
        print(
            "PLACEHOLDER exists in json file, replace all"
            " placeholder values before continuing"
        )
        print("!!!!!")
        return None

    request = {
        "storageSystem": str(storage_system),
        "storageSourceId": str(storage_source_id),
        "catalogEntry": json.dumps(entry),
    }
    response = requests.post(
        f"https://catalog.dsde-{env}.broadinstitute.org/api/v1/datasets",
        headers=headers,
        data=json.dumps(request),
    )
    print(response.text)
    return None


def main(args, current_user):
    access_token = generate_access_token(args.user)
    # Opening JSON file
    with open(args.metadata_file, "r") as f:
        data = json.load(f)
    create_dataset(
        args.environment,
        access_token,
        args.storage_system,
        args.storage_source_id,
        data,
    )

    auth_as_user(current_user)


if __name__ == "__main__":
    current_user = (
        subprocess.run(
            ["gcloud", "config", "get-value", "account"],
            check=True,
            capture_output=True,
        )
        .stdout.strip()
        .decode("utf-8")
    )
    try:
        parser = argparse.ArgumentParser(
            description=DESCRIPTION, formatter_class=argparse.RawTextHelpFormatter
        )
        parser.add_argument(
            "--environment",
            type=str,
            choices=["prod", "dev", "alpha", "staging"],
            default="dev",
            help="terra data catalog environment to create dataset in",
        )
        parser.add_argument("--user", type=str, default=current_user)
        parser.add_argument(
            "--storage-system",
            type=str,
            choices=["wks", "tdr"],
            required=True,
            help="storage system for the dataset to be created",
        )
        parser.add_argument(
            "--storage-source-id",
            type=str,
            required=True,
            help="id for the underlying resource in the storage system",
        )
        parser.add_argument(
            "--metadata-file",
            dest="metadata_file",
            required=True,
            help="json file containing the metadata for the dataset",
        )
        args = parser.parse_args()
        main(args, current_user)
        sys.exit(0)

    except Exception as e:
        print(e)
        sys.exit(1)
