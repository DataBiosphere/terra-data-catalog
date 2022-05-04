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

| Attribute | Rawls Workspace Path | Data Catalog Entry Path |
| --------- | -------------------- | -------------------------- |
| Cohort Phenotype/Indication (Disease Ontology)  | `library:diseaseOntologyLabel` |  |
| No. of Subjects | `library:numSubjects` | `counts.donors` |
| Data Category | `library:dataCategory`<br>ex: "Simple Nucleotide Variation, Copy Number Variation, Expression Quantification, DNA-Methylation, Clinical phenotypes, Biosample metadata" |  |
| Experimental Strategy | `library:datatype`<br>ex: "Whole Exome, Genotyping Array, RNA-Seq, miRNA-Seq, Methylation Array, Protein Expression Array" | `prov:wasGeneratedBy.3.TerraCore:hasAssayCategory`<br>Note: RNA-Seq would be found in this field in existing data catalog entries, but I don't know if all of these values would also fit in the same one. |
| Data Use Limitation | `library:dataUseRestriction` | `TerraDCAT_ap:hasDataUsePermission.0`<br>Not a perfect mapping, we will need to normalize "General Research Use" to "TerraCore:NoRestriction" |
| Cohort Phenotype/Indication | `library:indication`<br>ex: "Mesothelioma" | `samples.disease.0` |
| Cohort Name | `library:datasetName` | `dct:title` |
| Dataset Version | `library:datasetVersion` | `dct:version` |
| Cohort Description | `library:datasetDescription` | `dct:description` |
| Dataset Owner | `library:datasetOwner`<br>ex: "NCI" | `TerraDCAT_ap:hasDataCollection.0.dct:identifier`<br>If we have enough information, we can also build:<br> `TerraDCAT_AP:hasDataCollection.0.dct:publisher: National Cancer Institute`<br>`TerraDCAT_AP:hasDataCollection.0.dct:title: National Cancer Institute` |
| Dataset Custodian | `library:datasetCustodian`<br>ex: "dbGAP" | `TerraDCAT_ap:hasDataCollection.0.dct:identifier`<br>Note: This conflicts with "Dataset Owner", but I dont think we have another option for where to put this. |
| Dataset Depositor | `library:datasetDepositor` | `contributors.0.contactName`<br>`contributors.0.correspondingContributor = true` |
| Contact Email | `library:contactEmail` | `contributors.0.email` |
| Research Institute | `library:institute` | `contributors.0.institution` |
| Primary Disease Site | `library:primaryDiseaseSite`<br>ex: "Pleura" | `samples.disease.0`?<br>ex: "Brain Cancer" |
| Project Name | `library:projectName`<br>ex: "TCGA" |  |
| Genome Reference Version | `library:reference`<br>ex: "GRCh37/hg19" |  |
| Data File Formats | `library:dataFileFormats`<br>ex: "TXT, MAF" | `files.0.dcat:mediaType`<br>`files.0.count = 0`<br>`files.0.byteSize = 0`<br>Note: No way of knowing how many files match each file format |
| Profiling Instrument Type | `library:technology` |  |
| Profiling Protocol | `library:profilingProtocol` |  |
| Depth of Sequencing Coverage (Average) | `library:coverage` |  |
| Study Design | `library:studyDesign`<br>ex: "Tumor/Normal" | `samples.disease.0`<br>Note: concat value with "Primary Disease Site"? |
| Cell Type | `library:cellType`<br>ex: "Primary tumor cell, Whole blood" | `samples.disease.0`<br>Note: concat value with "Primary Disease Site"? |
| Reported Ethnicity | `library:ethnicity` |  |
| Cohort Country of Origin | `library:cohortCountry`<br>ex: "USA" |  |
| Requires External Approval | `library:requiresExternalApproval` |  |
| library Metadata Schema Version Number | `library:lmsvn` |  |
| Structured Data Use Limitations Version Number | `library:dulvn` |  |
| ORSP Conset Code | `library:orsp` |  |
