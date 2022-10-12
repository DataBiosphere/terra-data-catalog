package bio.terra.catalog.service;

import bio.terra.catalog.config.SchemaConfiguration;
import bio.terra.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {

  private final JsonSchema schema;

  @Autowired
  public SchemaService(SchemaConfiguration schemaConfiguration) throws FileNotFoundException {
    this.schema = getJsonSchemaFromUrl(schemaConfiguration.basePath());
  }

  private JsonSchema getJsonSchemaFromUrl(String uri) throws FileNotFoundException {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
    return factory.getSchema(new FileInputStream(uri));
  }

  public void validateMetadata(JsonNode json) {
    Set<ValidationMessage> errors = schema.validate(json);
    if (errors.size() > 0) {
      throw new BadRequestException("Catalog entry is invalid: " + errors);
    }
  }
}
