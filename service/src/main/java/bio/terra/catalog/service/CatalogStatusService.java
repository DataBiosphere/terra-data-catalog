package bio.terra.catalog.service;

import bio.terra.catalog.config.StatusCheckConfiguration;
import bio.terra.catalog.model.SystemStatusSystems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Connection;

@Component
public class CatalogStatusService extends BaseStatusService {
  private static final Logger logger = LoggerFactory.getLogger(CatalogStatusService.class);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CatalogStatusService(
      NamedParameterJdbcTemplate jdbcTemplate,
      StatusCheckConfiguration configuration) {
    super(configuration);
    this.jdbcTemplate = jdbcTemplate;
    super.registerStatusCheck("CloudSQL", this::databaseStatus);
  }

  private SystemStatusSystems databaseStatus() {
    try {
      logger.debug("Checking database connection valid");
      return new SystemStatusSystems()
          .ok(jdbcTemplate.getJdbcTemplate().execute((Connection conn) -> conn.isValid(5000)));
    } catch (Exception ex) {
      String errorMsg = "Database status check failed";
      logger.error(errorMsg, ex);
      return new SystemStatusSystems()
          .ok(false)
          .addMessagesItem(errorMsg + ": " + ex.getMessage());
    }
  }
}
