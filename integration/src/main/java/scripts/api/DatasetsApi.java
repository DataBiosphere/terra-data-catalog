package scripts.api;

import scripts.client.CatalogClient;

public class DatasetsApi extends bio.terra.catalog.api.DatasetsApi {

  private final CatalogClient catalogClient;

  public DatasetsApi(CatalogClient catalogClient) {
    super(catalogClient);
    this.catalogClient = catalogClient;
  }

  public CatalogClient getApiClient() {
    return catalogClient;
  }
}
