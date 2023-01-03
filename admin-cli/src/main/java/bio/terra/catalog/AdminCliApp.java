package bio.terra.catalog;

import bio.terra.catalog.cli.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from scanning.
      DataSourceAutoConfiguration.class,
    },
    scanBasePackages = {
      // Scan for logging-related components & configs
      "bio.terra.common.logging",
      // Scan for Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Transaction management and DB retry configuration
      "bio.terra.common.retry.transaction",
      // Scan all service-specific packages beneath the current package
      "bio.terra.catalog"
    })
@ConfigurationPropertiesScan("bio.terra.catalog")
public class AdminCliApp {

  private static final Logger logger = LoggerFactory.getLogger(AdminCliApp.class);

  public static void main(String[] args) {
    try {
      new SpringApplicationBuilder(AdminCliApp.class)
          .bannerMode(Banner.Mode.OFF)
          .web(WebApplicationType.NONE)
          .run(args);
    } catch (Exception e) {
      logger.error("Unexpected error running '{}'", args, e);
      System.exit(-1);
    }
    System.exit(0);
  }

  @Bean
  public ApplicationRunner commandLineRunner(Main main) {
    return main::run;
  }
}
