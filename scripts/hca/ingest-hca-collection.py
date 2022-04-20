#!/usr/bin/env python

import json
import os

import requests
from tqdm import tqdm

upsert_url = "https://catalog.dsde-dev.broadinstitute.org/api/v1/datasets"
policy_url = (
    "https://jade.datarepo-dev.broadinstitute.org/"
    "api/repository/v1/snapshots/{id}/policies/steward/members"
)


def auth_token():
    return os.environ["AUTH_TOKEN"].split(": ", 1)


def user_email():
    return os.environ["USER_EMAIL"]


def api_request(snapshot_id, url, obj, method):
    auth, token = auth_token()

    url = url.format(id=snapshot_id)

    data = None
    if obj:
        data = json.dumps(obj).encode("utf-8")

    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        auth: token,
    }

    req = requests.request(method, url, headers=headers, data=data)

    if not req.ok and method != "GET":
        raise Exception(f"Error: {req}")

    return req.ok


def add_policy(snapshot):
    email = {"email": user_email()}
    api_request(snapshot["dct:identifier"], policy_url, email, "POST")


def remove_policy(snapshot):
    snapshot_id = snapshot["dct:identifier"]
    api_request(snapshot_id, policy_url + "/" + user_email(), None, "DELETE")


def upsert(snapshot):
    snapshot_id = snapshot["dct:identifier"]
    body = {
        "storageSystem": "tdr",
        "storageSourceId": snapshot_id,
        "catalogEntry": json.dumps(snapshot),
    }

    # If it doesn't exist, add it.
    if not api_request(snapshot_id, upsert_url + "/{id}", None, "GET"):
        api_request(snapshot_id, upsert_url, body, "POST")


def main():
    with open("hca-collection.json", "r") as f:
        collection = json.load(f)

    for snapshot in tqdm(collection["data"]):
        add_policy(snapshot)
        upsert(snapshot)
        remove_policy(snapshot)


main()
