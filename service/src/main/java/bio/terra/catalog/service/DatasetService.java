package bio.terra.catalog.service;

import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;

  @Autowired
  public DatasetService(DatarepoService datarepoService) {
    this.datarepoService = datarepoService;
  }

  public List<String> listDatasets(AuthenticatedUserRequest user) {
    return datarepoService.getSnapshots(user.getToken()).stream()
        .map(SnapshotSummaryModel::getName)
        .toList();
  }
}
