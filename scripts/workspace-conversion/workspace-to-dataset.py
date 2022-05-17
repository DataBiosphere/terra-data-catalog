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


def logResponse(response, message):
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


def getAccessToken():
    proc = subprocess.Popen(
        [f"gcloud auth login {user} --brief"], stdout=subprocess.PIPE, shell=True
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

    # Get dataset list once in if lowerPolicy == of collision later
    response = requests.get(
        f"{urlWorkspace}/{workspaceNamespace}/{workspaceName}", headers=headers
    )
    responseData = json.loads(response.text)
    print("loaded workspace information from rawls")
    return responseData


def mapDatasetReleasePolicy(policyString):
    lowerPolicy = policyString.lower()
    if lowerPolicy == "no restrictions" or lowerPolicy == "no restriction":
        return "TerraCore:NoRestriction"
    if lowerPolicy == "general research use":
        return "TerraCore:GeneralResearchUse"
    if lowerPolicy == "no population origins or ancestry research":
        return "TerraCore:NPOA"
    if lowerPolicy == "no general methods research":
        return "TerraCore:NMDS"
    if lowerPolicy == "genetic studies only":
        return "TerraCore:GSO"
    if lowerPolicy == "clinical care use":
        return "TerraCore:CC"
    if lowerPolicy == "publication required":
        return "TerraCore:PUB"
    if lowerPolicy == "collaboration required":
        return "TerraCore:COL"
    if lowerPolicy == "ethics approval required":
        return "TerraCore:IRB"
    if lowerPolicy == "geographical restriction":
        return "TerraCore:GS"
    if lowerPolicy == "publication moratorium":
        return "TerraCore:MOR"
    if lowerPolicy == "return to database/resource":
        return "TerraCore:RT"
    if lowerPolicy == "non commercial use only":
        return "TerraCore:NCU"
    if (
        lowerPolicy == "not-for-profit use only"
        or lowerPolicy == "not for profit use only"
    ):
        return "TerraCore:NPC"
    if lowerPolicy == "not-for-profit, non-commercial use ony":
        return "TerraCore:NPC2"
    return policyString


def mapDataModality(modalityArray):
    ret = []
    for modality in modalityArray:
        if modality == "Epigenomic":
            ret.append("TerraCoreValueSets:Epigenomic")
        if modality == "Epigenomic_3D Contact Maps":
            ret.append("TerraCoreValueSets:Epigenomic_3dContactMaps")
        if modality == "Epigenomic_DNABinding":
            ret.append("TerraCoreValueSets:Epigenomic_DnaBinding")
        if modality == "Epigenomic_DNABinding_HistoneModificationLocation":
            ret.append(
                "TerraCoreValueSets:Epigenomic_DnaBinding_HistoneModificationLocation"
            )
        if modality == "Epigenomic_DNABinding_TranscriptionFactorLocation":
            ret.append(
                "TerraCoreValueSets:Epigenomic_DnaBinding_TranscriptionFactorLocation"
            )
        if modality == "Epigenomic_DNAChromatinAccessibility":
            ret.append("TerraCoreValueSets:Epigenomic_DnaChromatinAccessibility")
        if modality == "Epigenomic_DNAMethylation":
            ret.append("TerraCoreValueSets:Epigenomic_DnaMethylation")
        if modality == "Epigenomic_RNABinding":
            ret.append("TerraCoreValueSets:Epigenomic_RnaBinding")
        if modality == "Genomic":
            ret.append("TerraCoreValueSets:Genomic")
        if modality == "Genomic_Assembly":
            ret.append("TerraCoreValueSets:Genomic_Assembly")
        if modality == "Genomic_Exome":
            ret.append("TerraCoreValueSets:Genomic_Exome")
        if modality == "Genomic_Genotyping_Targeted":
            ret.append("TerraCoreValueSets:Genomic_Genotyping_Targeted")
        if modality == "Genomic_WholeGenome":
            ret.append("TerraCoreValueSets:Genomic_WholeGenome")
        if modality == "Imaging":
            ret.append("TerraCoreValueSets:Imaging")
        if modality == "Imaging_Electrophysiology":
            ret.append("TerraCoreValueSets:Imaging_Electrophysiology")
        if modality == "Imaging_Microscopy":
            ret.append("TerraCoreValueSets:Imaging_Microscopy")
        if modality == "Medical imaging _CTScan":
            ret.append("TerraCoreValueSets:MedicalImaging_CTScan")
        if modality == "Medical imaging _Echocardiogram":
            ret.append("TerraCoreValueSets:MedicalImaging_Echocardiogram")
        if modality == "Medical imaging _MRI":
            ret.append("TerraCoreValueSets:MedicalImaging_MRI")
        if modality == "Medical imaging_PET":
            ret.append("TerraCoreValueSets:MedicalImaging_PET")
        if modality == "Medical imaging _Xray":
            ret.append("TerraCoreValueSets:MedicalImaging_Xray")
        if modality == "Metabolomic":
            ret.append("TerraCoreValueSets:metabolomic")
        if modality == "Microbiome":
            ret.append("TerraCoreValueSets:Microbiome")
        if modality == "Metagenomic":
            ret.append("TerraCoreValueSets:Metagenomic")
        if modality == "Proteomic":
            ret.append("TerraCoreValueSets:Proteomic")
        if modality == "Transcriptomic":
            ret.append("TerraCoreValueSets:Transcriptomic")
        if modality == "SpatialTranscriptomics":
            ret.append("TerraCoreValueSets:SpatialTranscriptomics")
        if modality == "Trascriptomic_Targeted":
            ret.append("TerraCoreValueSets:Transcriptomic_Targeted")
        if modality == "Trascriptomic_NonTargeted":
            ret.append("TerraCoreValueSets:Transcriptomic_NonTargeted")
        if modality == "Trascriptomic_NonTargeted_RnaSeq":
            ret.append("TerraCoreValueSets:Transcriptomic_NoneTargeted_RnaSeq")
        if modality == "Trascriptomic_NonTargeted_MicroRnaCounts":
            ret.append("TerraCoreValueSets:Transcriptomic_NonTargeted_MicroRnaCounts")
        if modality == "Electrocardiogram":
            ret.append("TerraCoreValueSets:Electrocardiogram")
    return ret


def generateCatalogMetadata(workspace):
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
                mapDatasetReleasePolicy(
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
        metadata["TerraDCAT_ap:hasDataCollection"].append({})
        metadata["TerraDCAT_ap:hasDataCollection"][0]["dct:identifier"] = wsAttributes[
            "library:datasetOwner"
        ]
        metadata["TerraDCAT_ap:hasOwner"] = wsAttributes["library:datasetOwner"]
    metadata["TerraDCAT_ap:hasCustodian"] = wsAttributes["library:datasetCustodian"]
    metadata["contributors"] = []
    if (
        "library:datasetDepositor" in wsAttributes
        or "library:contactEmail" in wsAttributes
    ):
        metadata["contributors"].append({})
        if "library:datasetDepositor" in wsAttributes:
            metadata["contributors"][0]["contactName"] = wsAttributes[
                "library:datasetDepositor"
            ]
            metadata["contributors"][0]["correspondingContributor"] = True
        metadata["contributors"][0]["email"] = wsAttributes.get(
            "library:contactEmail", None
        )
        metadata["contributors"][0]["intstitution"] = next(
            iter(wsAttributes.get("library:institute", {}).get("items", [])), None
        )
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
            "TerraCore:hasDataModality": mapDataModality(
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
    accessToken = getAccessToken()

    # Get workspace information
    workspace = getWorkspace(accessToken)
    metadata = generateCatalogMetadata(workspace)
    print(metadata)


main()
