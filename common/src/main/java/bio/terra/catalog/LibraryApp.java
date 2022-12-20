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
public class LibraryApp {

  private static final Logger logger = LoggerFactory.getLogger(LibraryApp.class);

  public static void main(String[] args) {
    logger.error("The library app can't be run.");
    System.exit(-1);
  }
}
