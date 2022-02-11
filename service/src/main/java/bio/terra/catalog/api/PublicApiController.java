package bio.terra.catalog.api;

import bio.terra.catalog.config.VersionConfiguration;
import bio.terra.catalog.model.SystemStatus;
import bio.terra.catalog.model.VersionProperties;
import bio.terra.catalog.service.CatalogStatusService;
import bio.terra.common.iam.AuthenticatedUserRequestFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Controller
public class PublicApiController implements PublicApi {
  private final CatalogStatusService statusService;
  private final VersionProperties currentVersion;

  @Autowired
  public PublicApiController(CatalogStatusService statusService,
                             VersionConfiguration versionConfiguration) {
    this.statusService = statusService;
    this.currentVersion =
        new VersionProperties()
            .gitTag(versionConfiguration.getGitTag())
            .gitHash(versionConfiguration.getGitHash())
            .github(
                "https://github.com/DataBiosphere/terra-billing-profile-manager/commit/"
                    + versionConfiguration.getGitHash())
            .build(versionConfiguration.getBuild());
  }

  @Override
  public ResponseEntity<SystemStatus> getStatus() {
    SystemStatus systemStatus = statusService.getCurrentStatus();
    HttpStatus httpStatus = systemStatus.isOk() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return new ResponseEntity<>(systemStatus, httpStatus);
  }

  @Override
  public ResponseEntity<VersionProperties> getVersion() {
    return ResponseEntity.ok(currentVersion);
  }
}
