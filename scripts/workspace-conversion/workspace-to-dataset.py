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
# ------------------------------------------------------------------------------
import json
import os, subprocess, sys
import requests

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
    "Epigenomic": "TerraCoreValueSets:Epigenomic",
    "Epigenomic_3D Contact Maps": "TerraCoreValueSets:Epigenomic_3dContactMaps",
    "Epigenomic_DNABinding": "TerraCoreValueSets:Epigenomic_DnaBinding",
    "Epigenomic_DNABinding_HistoneModificationLocation": "TerraCoreValueSets:Epigenomic_DnaBinding_HistoneModificationLocation",
    "Epigenomic_DNABinding_TranscriptionFactorLocation": "TerraCoreValueSets:Epigenomic_DnaBinding_TranscriptionFactorLocation",
    "Epigenomic_DNAChromatinAccessibility": "TerraCoreValueSets:Epigenomic_DnaChromatinAccessibility",
    "Epigenomic_DNAMethylation": "TerraCoreValueSets:Epigenomic_DnaMethylation",
    "Epigenomic_RNABinding": "TerraCoreValueSets:Epigenomic_RnaBinding",
    "Genomic": "TerraCoreValueSets:Genomic",
    "Genomic_Assembly": "TerraCoreValueSets:Genomic_Assembly",
    "Genomic_Exome": "TerraCoreValueSets:Genomic_Exome",
    "Genomic_Genotyping_Targeted": "TerraCoreValueSets:Genomic_Genotyping_Targeted",
    "Genomic_WholeGenome": "TerraCoreValueSets:Genomic_WholeGenome",
    "Imaging": "TerraCoreValueSets:Imaging",
    "Imaging_Electrophysiology": "TerraCoreValueSets:Imaging_Electrophysiology",
    "Imaging_Microscopy": "TerraCoreValueSets:Imaging_Microscopy",
    "Medical imaging _CTScan": "TerraCoreValueSets:MedicalImaging_CTScan",
    "Medical imaging _Echocardiogram": "TerraCoreValueSets:MedicalImaging_Echocardiogram",
    "Medical imaging _MRI": "TerraCoreValueSets:MedicalImaging_MRI",
    "Medical imaging_PET": "TerraCoreValueSets:MedicalImaging_PET",
    "Medical imaging _Xray": "TerraCoreValueSets:MedicalImaging_Xray",
    "Metabolomic": "TerraCoreValueSets:metabolomic",
    "Microbiome": "TerraCoreValueSets:Microbiome",
    "Metagenomic": "TerraCoreValueSets:Metagenomic",
    "Proteomic": "TerraCoreValueSets:Proteomic",
    "Transcriptomic": "TerraCoreValueSets:Transcriptomic",
    "SpatialTranscriptomics": "TerraCoreValueSets:SpatialTranscriptomics",
    "Trascriptomic_Targeted": "TerraCoreValueSets:Transcriptomic_Targeted",
    "Trascriptomic_NonTargeted": "TerraCoreValueSets:Transcriptomic_NonTargeted",
    "Trascriptomic_NonTargeted_RnaSeq": "TerraCoreValueSets:Transcriptomic_NoneTargeted_RnaSeq",
    "Trascriptomic_NonTargeted_MicroRnaCounts": "TerraCoreValueSets:Transcriptomic_NonTargeted_MicroRnaCounts",
    "Electrocardiogram": "TerraCoreValueSets:Electrocardiogram",
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
        dataModalityMap.get(modality, None)
    return list(filter(None, ret))


def generate_catalog_metadata(workspace):
    print("Generating workspace metadata")

    wsAttributes = workspace["workspace"]["attributes"]

    # Set empty up the major objects
    metadata = {}
    metadata["samples"] = {}
    metadata["samples"]["disease"] = list(
        filter(
            None,
            [
                wsAttributes.get("library:diseaseOntologyLabel", None),
                wsAttributes.get("library:indication", None),
                wsAttributes.get("library:primaryDiseaseSite", None),
                wsAttributes.get("library:studyDesign", None),
                wsAttributes.get("library:cellType", None),
            ],
        )
    )
    metadata["counts"] = {}
    metadata["counts"]["donors"] = wsAttributes.get("library:numSubjects", 0)
    metadata["dct:dataCategory"] = wsAttributes.get("library:dataCategory", {}).get(
        "items", None
    )
    metadata["TerraDCAT_ap:hasDataUsePermission"] = list(
        filter(
            None,
            [
                map_dataset_release_policy(
                    wsAttributes.get("library:dataUseRestriction", "No restrictions")
                )
            ],
        )
    )
    metadata["dct:title"] = wsAttributes.get("library:datasetName", None)
    metadata["dct:version"] = wsAttributes.get("library:datasetVersion", None)
    metadata["dct:description"] = wsAttributes.get("library:datasetDescription", None)
    metadata["TerraDCAT_ap:hasOwner"] = wsAttributes.get("library:datasetOwner", None)
    metadata["TerraDCAT_ap:hasDataCollection"] = []
    if "library:datasetOwner" in wsAttributes:
        metadata["TerraDCAT_ap:hasDataCollection"].append(
            {"dct:identifier": wsAttributes["library:datasetOwner"]}
        )
        metadata["TerraDCAT_ap:hasOwner"] = wsAttributes["library:datasetOwner"]
    metadata["TerraDCAT_ap:hasCustodian"] = wsAttributes["library:datasetCustodian"]
    metadata["contributors"] = []
    if (
        "library:datasetDepositor" in wsAttributes
        or "library:contactEmail" in wsAttributes
    ):
        contributor = {}
        if "library:datasetDepositor" in wsAttributes:
            contributor["contactName"] = wsAttributes["library:datasetDepositor"]
            contributor["correspondingContributor"] = True
        contributor["email"] = wsAttributes.get("library:contactEmail", None)
        contributor["intstitution"] = next(
            iter(wsAttributes.get("library:institute", {}).get("items", [])), None
        )
        metadata["contributors"].append(contributor)
    metadata["prov:wasAssociatedWith"] = next(
        iter(wsAttributes.get("library:institute", {}).get("items", [])), None
    )
    metadata["prov:wasGeneratedBy"] = []
    if "library:projectName" in wsAttributes:
        metadata["prov:wasGeneratedBy"].append(
            {"TerraCore:hasAssayType": [wsAttributes["library:projectName"]]}
        )
    # Get the intersection of the datatype.items array and the data modality types
    metadata["prov:wasGeneratedBy"].append(
        {
            "TerraCore:hasDataModality": map_data_modality(
                wsAttributes.get("library:datatype", {}).get("items", [])
            )
        }
    )
    metadata["files"] = []
    fileList = wsAttributes.get("library:dataFileFormats", {}).get("items", [])
    for x in fileList:
        fileObj = {"dcat:mediaType": x, "count": 0, "byteSize": 0}
        metadata["files"].append(fileObj)

    metadata["TerraDCAT_ap:hasConsentGroup"] = wsAttributes.get("library:orsp")

    return json.dumps(metadata)


def main():
    print("Adding TDR Snapshot Metadata")

    # Obtain google user credentials
    accessToken = get_access_token()

    # Get workspace information
    workspace = get_workspace(accessToken)
    metadata = generate_catalog_metadata(workspace)
    print(metadata)


main()
