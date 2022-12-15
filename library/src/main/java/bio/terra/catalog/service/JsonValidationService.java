package bio.terra.catalog.service;

import bio.terra.catalog.config.SchemaConfiguration;
import bio.terra.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JsonValidationService {

  private final JsonSchema schema;

  @Autowired
  public JsonValidationService(SchemaConfiguration schemaConfiguration) {
    schema = getJsonSchemaFromFile(schemaConfiguration.basePath());
  }

  private JsonSchema getJsonSchemaFromFile(String file) {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    try (var input = getClass().getClassLoader().getResourceAsStream(file)) {
      return factory.getSchema(input);
    } catch (IOException | NullPointerException | IllegalArgumentException e) {
      throw new SchemaConfigurationException(e);
    }
  }

  public Set<ValidationMessage> validate(JsonNode json) {
    return schema.validate(json);
  }

  public void validateMetadata(JsonNode json) {
    Set<ValidationMessage> errors = validate(json);
    if (!errors.isEmpty()) {
      throw new BadRequestException("Catalog entry is invalid: " + errors);
    }
  }

  public static class SchemaConfigurationException extends RuntimeException {
    public SchemaConfigurationException(Exception e) {
      super(e);
    }
  }
}
