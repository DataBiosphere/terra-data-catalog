package bio.terra.catalog.service;

import bio.terra.catalog.config.SchemaConfiguration;
import bio.terra.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JsonValidationService {

  private final JsonSchema schema;

  @Autowired
  public JsonValidationService(SchemaConfiguration schemaConfiguration) {
    this.schema = getJsonSchemaFromFile(schemaConfiguration.basePath());
  }

  private JsonSchema getJsonSchemaFromFile(String file) {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    try (var input = new FileInputStream(file)) {
      return factory.getSchema(input);
    } catch (IOException e) {
      throw new SchemaConfigurationException(e);
    }
  }

  public void validateMetadata(JsonNode json) {
    Set<ValidationMessage> errors = schema.validate(json);
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
