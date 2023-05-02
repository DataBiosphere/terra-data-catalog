package bio.terra.catalog.service;

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
public class CatalogStatusService {
  private static final Logger logger = LoggerFactory.getLogger(CatalogStatusService.class);
  /** Number of seconds to wait for a connection to the database. */
  public static final int DB_CONNECTION_TIMEOUT = 1;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CatalogStatusService(
      StatusCheckService statusCheckService,
      NamedParameterJdbcTemplate jdbcTemplate,
      SamService samService,
      DatarepoService datarepoService,
      RawlsService rawlsService) {
    this.jdbcTemplate = jdbcTemplate;
    statusCheckService.registerStatusCheck("CloudSQL", this::databaseStatus);
    statusCheckService.registerStatusCheck("SAM", samService::status);
    statusCheckService.registerStatusCheck("Data Repo", datarepoService::status);
    statusCheckService.registerStatusCheck("Rawls", rawlsService::status);
  }

  @VisibleForTesting
  SystemStatusSystems databaseStatus() {
    try {
      logger.debug("Checking database connection valid");
      return new SystemStatusSystems()
          .ok(jdbcTemplate.getJdbcTemplate().execute((Connection conn) -> conn.isValid(DB_CONNECTION_TIMEOUT)));
    } catch (Exception ex) {
      String errorMsg = "Database status check failed";
      logger.error(errorMsg, ex);
      return new SystemStatusSystems().ok(false).addMessagesItem(errorMsg + ": " + ex.getMessage());
    }
  }
}
