package bio.terra.catalog.service;

import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.DatasetSummaryModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;

  @Autowired
  public DatasetService(DatarepoService datarepoService) {
    this.datarepoService = datarepoService;
  }

  public List<String> listDatasets(AuthenticatedUserRequest user) {
    return datarepoService.getDatasets(user.getToken()).stream().map(DatasetSummaryModel::getName).toList();
  }
}
