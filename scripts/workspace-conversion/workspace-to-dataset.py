# ------------------------------------------------------------------------------
# Workspace to Dataset
#
# This script converts terra workspaces attributes into a potential catalog entry.
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
#   `python3 workspace-to-dataset.py`
#   `GCLOUD_USER={your-email} python3 generate-tdr-snapshot-metadata.py`
#
# Environment Variables:
#   GCLOUD_USER: user account email
#   CATALOG_SERVICE_URL: url for the catalog service to update the updata in
#   DATA_REPO_URL: url for the data repo to use to extract testing snapshot information
# ------------------------------------------------------------------------------
import json
import os, subprocess, sys
import requests

urlRoot = os.environ.get("RAWLS_URL") or "https://rawls.dsde-prod.broadinstitute.org"
urlWorkspace = f"{urlRoot}/api/workspaces"
workspaceNamespace = os.environ.get("WORKSPACE_NAMESPACE")
workspaceName = os.environ.get("WORKSPACE_NAME")


def logResponse(response, message):
    if 200 <= response.status_code and response.status_code < 300:
        print("success!", response.text)
    else:
        print("---------------------------------------------------")
        print(f"There was a problem running this request: ", message)
        print(json.loads(response.text)["message"])
        print("\nRequest:")
        print(f"\t{response.request.url}")
        print(f"\t{response.request.headers}")
        print(f"\t{response.request.body}")
        print("---------------------------------------------------")


def getAccessToken():
    proc = subprocess.Popen(
        [f"gcloud auth login --brief"], stdout=subprocess.PIPE, shell=True
    )
    proc.communicate()

    printProc = subprocess.Popen(
        ["gcloud auth print-access-token"], stdout=subprocess.PIPE, shell=True
    )
    (out, err) = printProc.communicate()

    return out.decode("ASCII").strip()


def getWorkspace(accessToken):
    print("Getting workspace information from rawls...")
    headers = {
        "Authorization": f"Bearer {accessToken}",
        "Content-Type": "application/json",
    }

    # Get dataset list once in case of collision later
    response = requests.get(
        f"{urlWorkspace}/{workspaceNamespace}/{workspaceName}", headers=headers
    )
    responseData = json.loads(response.text)
    print("loaded workspace information from rawls")
    print(responseData)


def main():
    print("Adding TDR Snapshot Metadata")

    # Obtain google user credentials
    accessToken = getAccessToken()

    # Get workspace information
    getWorkspace(accessToken)


main()
