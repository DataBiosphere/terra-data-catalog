package bio.terra.catalog.app;

import bio.terra.catalog.config.CatalogDatabaseConfiguration;
import bio.terra.common.migrate.LiquibaseMigrator;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    var profileDatabaseConfiguration =
        applicationContext.getBean(CatalogDatabaseConfiguration.class);

    // Migrate the database
    if (profileDatabaseConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, profileDatabaseConfiguration.getDataSource());
    } else if (profileDatabaseConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, profileDatabaseConfiguration.getDataSource());
    }
  }
}
