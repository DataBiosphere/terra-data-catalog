package bio.terra.catalog.api;

import bio.terra.catalog.service.DatasetService;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.iam.AuthenticatedUserRequestFactory;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class DatasetApiController implements DatasetApi {
  private final HttpServletRequest request;
  private final DatasetService datasetService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;

  @Autowired
  public DatasetApiController(
      HttpServletRequest request,
      DatasetService datasetService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory) {
    this.request = request;
    this.datasetService = datasetService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
  }

  @Override
  public ResponseEntity<List<String>> listDatasets() {
    AuthenticatedUserRequest user = authenticatedUserRequestFactory.from(request);
    return ResponseEntity.ok(datasetService.listDatasets(user));
  }
}