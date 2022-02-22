package bio.terra.catalog.datarepo;

import bio.terra.catalog.config.DatarepoConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.datarepo.api.DatasetsApi;
import bio.terra.datarepo.api.UnauthenticatedApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.DatasetSummaryModel;
import bio.terra.datarepo.model.EnumerateSortByParam;
import bio.terra.datarepo.model.RepositoryStatusModel;
import bio.terra.datarepo.model.SqlSortDirection;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatarepoService {
  private static final Logger logger = LoggerFactory.getLogger(DatarepoService.class);
  private final DatarepoConfiguration datarepoConfig;
  private final Client commonHttpClient;

  @Autowired
  public DatarepoService(DatarepoConfiguration datarepoConfig) {
    this.datarepoConfig = datarepoConfig;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  public List<DatasetSummaryModel> getDatasets(String userToken) {
    try {
      return datasetsApi(userToken)
          .enumerateDatasets(0, 100, EnumerateSortByParam.NAME, SqlSortDirection.ASC, "", "")
          .getItems();
    } catch (ApiException e) {
      throw new DatarepoException("Enumerate Datasets failed", e);
    }
  }

  @VisibleForTesting
  DatasetsApi datasetsApi(String accessToken) {
    return new DatasetsApi(getApiClient(accessToken));
  }

  private ApiClient getApiClient(String accessToken) {
    // OkHttpClient objects manage their own thread pools, so it's much more performant to share one
    // across requests.
    ApiClient apiClient =
        new ApiClient().setHttpClient(commonHttpClient).setBasePath(datarepoConfig.basePath());
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  public SystemStatusSystems status() {
    // No access token needed since this is an unauthenticated API.
    UnauthenticatedApi api = new UnauthenticatedApi(getApiClient(null));
    var result = new SystemStatusSystems();
    try {
      // Don't retry status check
      RepositoryStatusModel status = api.serviceStatus();
      result.ok(status.isOk());
      // Populate error message if Sam status is non-ok
      if (!result.isOk()) {
        String errorMsg = "Sam status check failed. Messages = " + status.getSystems();
        logger.error(errorMsg);
        result.addMessagesItem(errorMsg);
      }
    } catch (Exception e) {
      String errorMsg = "Data repo status check failed";
      logger.error(errorMsg, e);
      result.ok(false).addMessagesItem(errorMsg);
    }
    return result;
  }
}
