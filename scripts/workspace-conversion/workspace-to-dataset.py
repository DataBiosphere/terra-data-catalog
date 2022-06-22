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
#   `export GCLOUD_USER={your-email}; export WORKSPACE_NAMESPACE={workspace-namespace}; export WORKSPACE_NAME={workspace-name}; python3 workspace-to-dataset.py`
#
# Environment Variables:
#   GCLOUD_USER: user account email
#   WORKSPACE_NAMESPACE: workspace namespace for the rawls query
#   WORKSPACE_NAME: workspace name for the rawls query
#   RAWLS_URL: url for rawls. Default set to rawls-prod
#   TERRA_URL: url for terra ui. Default set to staging. Used to output demo link.
# ------------------------------------------------------------------------------
import json
import os, subprocess
import requests
import itertools
from datetime import datetime

urlRoot = os.environ.get("RAWLS_URL") or "https://rawls.dsde-prod.broadinstitute.org"
urlWorkspace = f"{urlRoot}/api/workspaces"
workspaceNamespace = os.environ.get("WORKSPACE_NAMESPACE")
workspaceName = os.environ.get("WORKSPACE_NAME")
user = os.environ.get("GCLOUD_USER") or "datacatalogadmin@test.firecloud.org"

policyMap = {
    "no restrictions": "TerraCore:NoRestriction",
    "no restriction": "TerraCore:NoRestriction",
    "general research use": "TerraCore:GeneralResearchUse",
    "no population origins or ancestry research": "TerraCore:NPOA",
    "no general methods research": "TerraCore:NMDS",
    "genetic studies only": "TerraCore:GSO",
    "clinical care use": "TerraCore:CC",
    "publication required": "TerraCore:PUB",
    "collaboration required": "TerraCore:COL",
    "ethics approval required": "TerraCore:IRB",
    "geographical restriction": "TerraCore:GS",
    "publication moratorium": "TerraCore:MOR",
    "return to database/resource": "TerraCore:RT",
    "non commercial use only": "TerraCore:NCU",
    "not-for-profit use only": "TerraCore:NPC",
    "not for profit use only": "TerraCore:NPC",
    "not-for-profit, non-commercial use ony": "TerraCore:NPC2",
}

dataModalityMap = {
    "Epigenomic": ["TerraCoreValueSets:Epigenomic"],
    "Epigenomic_3D Contact Maps": ["TerraCoreValueSets:Epigenomic_3dContactMaps"],
    "Epigenomic_DNABinding": ["TerraCoreValueSets:Epigenomic_DnaBinding"],
    "Epigenomic_DNABinding_HistoneModificationLocation": [
        "TerraCoreValueSets:Epigenomic_DnaBinding_HistoneModificationLocation"
    ],
    "Epigenomic_DNABinding_TranscriptionFactorLocation": [
        "TerraCoreValueSets:Epigenomic_DnaBinding_TranscriptionFactorLocation"
    ],
    "Epigenomic_DNAChromatinAccessibility": [
        "TerraCoreValueSets:Epigenomic_DnaChromatinAccessibility"
    ],
    "Epigenomic_DNAMethylation": ["TerraCoreValueSets:Epigenomic_DnaMethylation"],
    "Epigenomic_RNABinding": ["TerraCoreValueSets:Epigenomic_RnaBinding"],
    "Genomic": ["TerraCoreValueSets:Genomic"],
    "Genomic_Assembly": ["TerraCoreValueSets:Genomic_Assembly"],
    "Genomic_Exome": ["TerraCoreValueSets:Genomic_Exome"],
    "Genomic_Genotyping_Targeted": ["TerraCoreValueSets:Genomic_Genotyping_Targeted"],
    "Genomic_WholeGenome": ["TerraCoreValueSets:Genomic_WholeGenome"],
    "Imaging": ["TerraCoreValueSets:Imaging"],
    "Imaging_Electrophysiology": ["TerraCoreValueSets:Imaging_Electrophysiology"],
    "Imaging_Microscopy": ["TerraCoreValueSets:Imaging_Microscopy"],
    "Medical imaging _CTScan": ["TerraCoreValueSets:MedicalImaging_CTScan"],
    "Medical imaging _Echocardiogram": [
        "TerraCoreValueSets:MedicalImaging_Echocardiogram"
    ],
    "Medical imaging _MRI": ["TerraCoreValueSets:MedicalImaging_MRI"],
    "Medical imaging_PET": ["TerraCoreValueSets:MedicalImaging_PET"],
    "Medical imaging _Xray": ["TerraCoreValueSets:MedicalImaging_Xray"],
    "Metabolomic": ["TerraCoreValueSets:metabolomic"],
    "Microbiome": ["TerraCoreValueSets:Microbiome"],
    "Metagenomic": ["TerraCoreValueSets:Metagenomic"],
    "Proteomic": ["TerraCoreValueSets:Proteomic"],
    "Transcriptomic": ["TerraCoreValueSets:Transcriptomic"],
    "SpatialTranscriptomics": ["TerraCoreValueSets:SpatialTranscriptomics"],
    "Trascriptomic_Targeted": ["TerraCoreValueSets:Transcriptomic_Targeted"],
    "Trascriptomic_NonTargeted": ["TerraCoreValueSets:Transcriptomic_NonTargeted"],
    "Trascriptomic_NonTargeted_RnaSeq": [
        "TerraCoreValueSets:Transcriptomic_NoneTargeted_RnaSeq"
    ],
    "Trascriptomic_NonTargeted_MicroRnaCounts": [
        "TerraCoreValueSets:Transcriptomic_NonTargeted_MicroRnaCounts"
    ],
    "Electrocardiogram": ["TerraCoreValueSets:Electrocardiogram"],
    # From create-hca-collection.py
    "10x sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10X 3' v1 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x 3' v2": ["TerraCoreValueSets:Transcriptomic"],
    "10x 3' v2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10X 3' v2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10X 3' V2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x 3' V2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x 3' v3": ["TerraCoreValueSets:Transcriptomic"],
    "10x 3' v3 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10X 3' v3 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x 5' v1": ["TerraCoreValueSets:Transcriptomic"],
    "10X 5' v2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x v2 3'": ["TerraCoreValueSets:Transcriptomic"],
    "10x v2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10X v2 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "10x v3 sequencing": ["TerraCoreValueSets:Transcriptomic"],
    "CITE 10x 3' v2": [
        "TerraCoreValueSets:Transcriptomic",
        "TerraCoreValueSets:Proteomic",
    ],
    "CITE-seq": [
        "TerraCoreValueSets:Transcriptomic",
        "TerraCoreValueSets:Proteomic",
    ],
    "Smart-seq": ["TerraCoreValueSets:Transcriptomic"],
    "Smart-Seq": ["TerraCoreValueSets:Transcriptomic"],
    "Smart-seq2": ["TerraCoreValueSets:Transcriptomic"],
    "Smart-like": ["TerraCoreValueSets:Transcriptomic"],
    "Drop-seq": ["TerraCoreValueSets:Transcriptomic"],
    "Drop-Seq": ["TerraCoreValueSets:Transcriptomic"],
    "10X Feature Barcoding technology for cell surface proteins": [
        "TerraCoreValueSets:Transcriptomic",
        "TerraCoreValueSets:Proteomic",
    ],
    "10X Gene Expression Library": [
        "TerraCoreValueSets:Transcriptomic",
        "TerraCoreValueSets:Proteomic",
    ],
    "10x Ig enrichment": ["TerraCoreValueSets:Transcriptomic"],
    "10X Ig enrichment": ["TerraCoreValueSets:Transcriptomic"],
    "10x TCR enrichment": ["TerraCoreValueSets:Transcriptomic"],
    "10X TCR enrichment": ["TerraCoreValueSets:Transcriptomic"],
    "Fluidigm C1-based library preparation": ["TerraCoreValueSets:Transcriptomic"],
    "barcoded plate-based single cell RNA-seq": ["TerraCoreValueSets:Transcriptomic"],
    "cDNA library construction": ["TerraCoreValueSets:Transcriptomic"],
    "ATAC 10x v1": ["TerraCoreValueSets:Epigenomic"],
    "inDrop": ["TerraCoreValueSets:Transcriptomic"],
    "DNA library construction": ["TerraCoreValueSets:Genomic"],
    "sci-CAR": ["TerraCoreValueSets:Transcriptomic"],
    "sci-RNA-seq": ["TerraCoreValueSets:Transcriptomic"],
    "DroNc-Seq": ["TerraCoreValueSets:Transcriptomic"],
    "MARS-seq": ["TerraCoreValueSets:Transcriptomic"],
}


def log_response(response, message):
    if response.ok:
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


def get_access_token():
    proc = subprocess.Popen(
        [f"gcloud auth login {user} --brief"], stdout=subprocess.PIPE, shell=True
    )
    proc.communicate()

    printProc = subprocess.Popen(
        ["gcloud auth print-access-token"], stdout=subprocess.PIPE, shell=True
    )
    (out, err) = printProc.communicate()

    return out.decode("ASCII").strip()


def get_workspace(accessToken):
    print("Getting workspace information from rawls...")
    headers = {
        "Authorization": f"Bearer {accessToken}",
        "Content-Type": "application/json",
    }

    # Get dataset list once in if lowerPolicy == of collision later
    response = requests.get(
        f"{urlWorkspace}/{workspaceNamespace}/{workspaceName}", headers=headers
    )
    responseData = json.loads(response.text)
    print("loaded workspace information from rawls")
    return responseData


def map_dataset_release_policy(policyString):
    lowerPolicy = policyString.lower()
    return policyMap.get(lowerPolicy, policyString)


def map_data_modality(modalityArray):
    ret = []
    for modality in modalityArray:
        if modality in dataModalityMap:
            ret = list(itertools.chain(ret, dataModalityMap[modality]))
    return list(set(ret))


def get_workspace_contributors(wsAttributes):
    ret = []
    if (
        "library:datasetDepositor" in wsAttributes
        or "library:contactEmail" in wsAttributes
    ):
        contributor = {}
        if "library:datasetDepositor" in wsAttributes:
            contributor["contactName"] = wsAttributes["library:datasetDepositor"]
            contributor["correspondingContributor"] = True
        contributor["email"] = wsAttributes.pop("library:contactEmail", None)
        # We currently only get the first item in the institution list.
        # We may want to revisit this.
        contributor["institution"] = next(
            iter(wsAttributes.pop("library:institute", {}).get("items", [])), None
        )
        ret.append(contributor)
    return ret


def get_workspace_generated(wsAttributes):
    ret = []
    if "library:projectName" in wsAttributes:
        ret.append({"TerraCore:hasAssayType": [wsAttributes["library:projectName"]]})
    # Get the intersection of the datatype.items array and the data modality types
    ret.append(
        {
            "TerraCore:hasDataModality": map_data_modality(
                wsAttributes.pop("library:datatype", {}).get("items", [])
            )
        }
    )
    return ret


def get_workspace_files(wsAttributes):
    ret = []
    fileList = wsAttributes.pop("library:dataFileFormats", {}).get("items", [])
    for ext in fileList:
        fileObj = {"dcat:mediaType": ext, "count": 0, "byteSize": 0}
        ret.append(fileObj)
    return ret


def generate_catalog_metadata(workspace, bucket):
    print("Generating workspace metadata")

    wsAttributes = workspace["workspace"]["attributes"]

    # Set empty up the major objects
    metadata = {
        "samples": {
            # Use set first to dedup, then list because set is not json serializable
            "disease": list(
                set(
                    filter(
                        None,
                        [
                            wsAttributes.pop("library:diseaseOntologyLabel", None),
                            wsAttributes.pop("library:indication", None),
                            wsAttributes.pop("library:primaryDiseaseSite", None),
                            wsAttributes.pop("library:studyDesign", None),
                            wsAttributes.pop("library:cellType", None),
                        ],
                    )
                )
            )
        },
        "counts": {"donors": wsAttributes.pop("library:numSubjects", 0)},
        "dct:dataCategory": wsAttributes.pop("library:dataCategory", {}).get(
            "items", None
        ),
        "TerraDCAT_ap:hasDataUsePermission": list(
            filter(
                None,
                [
                    map_dataset_release_policy(
                        wsAttributes.pop(
                            "library:dataUseRestriction", "No restrictions"
                        )
                    )
                ],
            )
        ),
        "dct:title": wsAttributes.pop("library:datasetName", None),
        "dct:version": wsAttributes.pop("library:datasetVersion", None),
        "dct:description": wsAttributes.pop("library:datasetDescription", None),
        "dct:modified": workspace["workspace"]["lastModified"],
        "TerraDCAT_ap:hasDataCollection": list(
            filter(
                None,
                [
                    {"dct:identifier": wsAttributes["library:datasetOwner"]}
                    if "library:datasetOwner" in wsAttributes
                    else None
                ],
            )
        ),
        "TerraDCAT_ap:hasOwner": wsAttributes.pop("library:datasetOwner", None),
        "TerraDCAT_ap:hasCustodian": wsAttributes.pop("library:datasetCustodian", None),
        "contributors": get_workspace_contributors(wsAttributes),
        "prov:wasAssociatedWith": next(
            iter(wsAttributes.pop("library:institute", {}).get("items", [])), None
        ),
        "prov:wasGeneratedBy": get_workspace_generated(wsAttributes),
        "files": get_workspace_files(wsAttributes),
        "TerraDCAT_ap:hasConsentGroup": wsAttributes.pop("library:orsp", None),
        "workspaces": {"legacy": {"workspace": workspace["workspace"]}},
        "storage": [
            {
                bucket["locationType"]: bucket["location"],
                "cloudPlatform": "gcp",
                "cloudResource": "bucket",
            }
        ],
    }

    return wsAttributes, metadata


def get_bucket_information(bucketName, accessToken):
    print("Getting bucket information from google cloud storage...")
    headers = {
        "Authorization": f"Bearer {accessToken}",
        "Content-Type": "application/json",
        "authority": "storage.googleapis.com",
    }

    # Get dataset list once in if lowerPolicy == of collision later
    response = requests.get(
        f"https://storage.googleapis.com/storage/v1/b/{bucketName}", headers=headers
    )
    responseData = json.loads(response.text)
    print("loaded bucket information from google cloud storage")
    return responseData


def main():
    print("Adding Rawls Workspace Metadata")

    # Obtain google user credentials
    accessToken = get_access_token()

    # Get workspace information
    workspace = get_workspace(accessToken)
    bucket = get_bucket_information(workspace["workspace"]["bucketName"], accessToken)
    unusedWorkspaceAttributes, metadata = generate_catalog_metadata(workspace, bucket)
    print("------------------------------------------------------------")
    print("JSON Result:\n", json.dumps(metadata))
    print("------------------------------------------------------------")
    print("Unused Attributes \n", json.dumps(unusedWorkspaceAttributes))
    print("------------------------------------------------------------")


main()
