package bio.terra.catalog.service;

import bio.terra.catalog.config.StatusCheckConfiguration;
import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.rawls.RawlsService;
import com.google.common.annotations.VisibleForTesting;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogStatusService extends BaseStatusService {
  private static final Logger logger = LoggerFactory.getLogger(CatalogStatusService.class);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CatalogStatusService(
      NamedParameterJdbcTemplate jdbcTemplate,
      StatusCheckConfiguration configuration,
      SamService samService,
      DatarepoService datarepoService,
      RawlsService rawlsService) {
    super(configuration);
    this.jdbcTemplate = jdbcTemplate;
    registerStatusCheck("CloudSQL", this::databaseStatus);
    registerStatusCheck("SAM", samService::status);
    registerStatusCheck("Data Repo", datarepoService::status);
    registerStatusCheck("Rawls", rawlsService::status);
  }

  @VisibleForTesting
  SystemStatusSystems databaseStatus() {
    try {
      logger.debug("Checking database connection valid");
      return new SystemStatusSystems()
          .ok(jdbcTemplate.getJdbcTemplate().execute((Connection conn) -> conn.isValid(1)));
    } catch (Exception ex) {
      String errorMsg = "Database status check failed";
      logger.error(errorMsg, ex);
      return new SystemStatusSystems().ok(false).addMessagesItem(errorMsg + ": " + ex.getMessage());
    }
  }
}
