## Converting Workspace Dataset Attributes to Catalog Entry Data

# Description
This script takes the workspace dataset attributes and attempts to map them to similar fields in the data catalog service as catalog entries.

# Dataset Attributes
An example workspace with attributes can be found [here](https://github.com/DataBiosphere/terra-ui/blob/dev/src/data/workspace-attributes.js#2), under the variable `displayLibraryAttributes`.

The request made to retrieve the workspace details is an XHR request to rawls to retrieve the workspace information. The basic structure of the return of the call looks like:

```
{
  owners: [],
  workspaces: {
    attributes: {
      description: string,
      `library:cellType: string,
      `library: ...
    }
  }
}
```

What is shown to the user in that table is the contents in the attributes that terra-ui has determined is important to display. These values are hardcoded at 
`{terra-ui}/src/data/workspace-cleanAttributes.js.displayLibraryAttributes`

# Value mappings
To run the conversion from workspace to dataset, here is a current mapping scheme:


| i | Attribute | Rawls Workspace Path | Data Catalog Entry Path |
| - | --------- | -------------------- | -------------------------- |
| 1 | Cohort Phenotype/Indication (Disease Ontology)  | `library:diseaseOntologyLabel` | `samples.disease.0`<br>kathy: lists predefined terms, but forces them to stick with a specific vocabulary. The other phenotype field is the diseases |
| 2 | No. of Subjects | `library:numSubjects` | `counts.donors`<br>Kathy: suggests leaving out this field because it is volatile and can easily become inaccurate |
| 3 | Data Category | `library:dataCategory`<br>ex: "Simple Nucleotide Variation, Copy Number Variation, Expression Quantification, DNA-Methylation, Clinical phenotypes, Biosample metadata" | `dct:dataCategory`*<br>This field does not currently exist |
| 4 | Experimental Strategy | `library:datatype`<br>ex: "Whole Exome, Genotyping Array, RNA-Seq, miRNA-Seq, Methylation Array, Protein Expression Array" | Kathy: Map this to data modality, talk to kathy about getting the mapping of terms |
| 5 | Data Use Limitation | `library:dataUseRestriction` | `TerraDCAT_ap:hasDataUsePermission.0`<br>Not a perfect mapping, we will need to normalize "General Research Use" to "TerraCore:GeneralResearchUse", preferably using the mapping found [here](https://github.com/DataBiosphere/terra-ui/blob/dev/src/pages/library/dataBrowser-utils.js#23) under `datasetReleasePolicies` |
| 6 | Cohort Phenotype/Indication | `library:indication`<br>ex: "Mesothelioma" | `samples.disease.0`<br>kathy: might not be useful, could consider pre-pending with source (ie: "TCGA" or "Anvil_phenotype") |
| 7 | Cohort Name | `library:datasetName` | `dct:title` |
| 8 | Dataset Version | `library:datasetVersion` | `dct:version` |
| 9 | Cohort Description | `library:datasetDescription` | `dct:description` |
| 10 | Dataset Owner | `library:datasetOwner`<br>ex: "NCI" | `TerraDCAT_ap:hasOwner`<br>`TerraDCAT_ap:hasDataCollection.0.dct:identifier`<br>If we have enough information, we can also build:<br> `TerraDCAT_AP:hasDataCollection.0.dct:publisher: National Cancer Institute`<br>`TerraDCAT_AP:hasDataCollection.0.dct:title: National Cancer Institute` |
| 11 | Dataset Custodian | `library:datasetCustodian`<br>ex: "dbGAP" | `TerraDCAT_ap:hasCustodian`<br>`TerraDCAT_ap:hasDataCollection.0.dct:identifier`<br>Note: This conflicts with "Dataset Owner", but I dont think we have another option for where to put this. |
| 12 | Dataset Depositor | `library:datasetDepositor` | `contributors.0.contactName`<br>`contributors.0.correspondingContributor = true` |
| 13 | Contact Email | `library:contactEmail` | `contributors.0.email` |
| 14 | Research Institute | `library:institute` | `contributors.0.institution`<br>`prov:wasAssociatedWith` |
| 15 | Primary Disease Site | `library:primaryDiseaseSite`<br>ex: "Pleura" | `samples.disease.0`?<br>ex: "Brain Cancer" |
| 16 | Project Name | `library:projectName`<br>ex: "TCGA" | `prov:wasGeneratedBy.0.TerraCore:hasAssayType.0` |
| 17 | Genome Reference Version | `library:reference`<br>ex: "GRCh37/hg19" |  |
| 18 | Data File Formats | `library:dataFileFormats`<br>ex: "TXT, MAF" | `files.0.dcat:mediaType`<br>`files.0.count = 0`<br>`files.0.byteSize = 0`<br>Note: No way of knowing how many files match each file format |
| 19 | Profiling Instrument Type | `library:technology` |  |
| 20 | Profiling Protocol | `library:profilingProtocol` |  |
| 21 | Depth of Sequencing Coverage (Average) | `library:coverage` |  |
| 22 | Study Design | `library:studyDesign`<br>ex: "Tumor/Normal" | `samples.disease.0`<br>Note: concat value with "Primary Disease Site"? |
| 23 | Cell Type | `library:cellType`<br>ex: "Primary tumor cell, Whole blood" | `samples.disease.0`<br>Note: concat value with "Primary Disease Site"? |
| 24 | Reported Ethnicity | `library:ethnicity` |  |
| 25 | Cohort Country of Origin | `library:cohortCountry`<br>ex: "USA" |  |
| 26 | Requires External Approval | `library:requiresExternalApproval` |  |
| 27 | library Metadata Schema Version Number | `library:lmsvn` |  |
| 28 | Structured Data Use Limitations Version Number | `library:dulvn` |  |
| 29 | ORSP Conset Code | `library:orsp` | `TerraDCAT_ap:hasConsentGroup` |
