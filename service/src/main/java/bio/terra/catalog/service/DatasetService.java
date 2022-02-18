package bio.terra.catalog.service;

import bio.terra.common.iam.AuthenticatedUserRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DatasetService {
  public List<String> listDatasets(AuthenticatedUserRequest user) {
    return List.of();
  }
}
