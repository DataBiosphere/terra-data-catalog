package bio.terra.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from scanning.
      DataSourceAutoConfiguration.class
    },
    scanBasePackages = {
      // Scan for Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Transaction management and DB retry configuration
      "bio.terra.common.retry.transaction",
      // Scan all service-specific packages beneath the current package
      "bio.terra.catalog"
    })
@ConfigurationPropertiesScan("bio.terra.catalog")
@EnableRetry
@EnableTransactionManagement
public class CommonApp {

  private static final Logger logger = LoggerFactory.getLogger(CommonApp.class);

  // This is a stub spring boot app that makes it easier to work with the spring boot
  // gradle plugin. It has no `main` and should never be run.
  public static void main(String[] args) {
    logger.error("The common app can't be run.");
    System.exit(-1);
  }
}
