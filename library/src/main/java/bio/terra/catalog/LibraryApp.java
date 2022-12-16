package bio.terra.catalog;

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

  public static void main(String[] args) {
    System.err.println("The library app can't be run.");
    System.exit(-1);
  }
}
