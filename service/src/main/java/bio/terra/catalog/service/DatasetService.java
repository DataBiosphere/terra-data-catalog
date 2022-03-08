package bio.terra.catalog.service;

import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.model.DatasetsListResponse;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.datarepo.model.SnapshotSummaryModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  private final DatarepoService datarepoService;
  private final ObjectMapper objectMapper;

  @Autowired
  public DatasetService(DatarepoService datarepoService, ObjectMapper objectMapper) {
    this.datarepoService = datarepoService;
    this.objectMapper = objectMapper;
  }

  public DatasetsListResponse listDatasets(AuthenticatedUserRequest user) {
    var response = new DatasetsListResponse();
    for (SnapshotSummaryModel model : datarepoService.getSnapshots(user.getToken())) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("id", model.getId().toString());
      node.put("dct:title", model.getName());
      response.addResultItem(node);
    }
    return response;
  }
}
