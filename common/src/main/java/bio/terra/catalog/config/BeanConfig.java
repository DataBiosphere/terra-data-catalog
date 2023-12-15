package bio.terra.catalog.config;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class BeanConfig {

  @Bean("jdbcTemplate")
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(
      CatalogDatabaseConfiguration config) {
    return new NamedParameterJdbcTemplate(config.getDataSource());
  }

  @Bean("objectMapper")
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new ParameterNamesModule())
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);
  }

  @Bean
  @RequestScope
  public BearerToken bearerToken(HttpServletRequest request) {
    return new BearerTokenFactory().from(request);
  }
}
