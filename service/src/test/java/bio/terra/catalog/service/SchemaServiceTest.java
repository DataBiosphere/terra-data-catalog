package bio.terra.catalog.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.catalog.config.BeanConfig;
import bio.terra.catalog.config.SchemaConfiguration;
import bio.terra.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

public class SchemaServiceTest {
  private static final String BASE_PATH = "src/main/resources/schemas/test.schema.json";

  private static final ObjectMapper objectMapper = new BeanConfig().objectMapper();

  @Test
  void testValidateInvalidMetadata() throws FileNotFoundException {
    SchemaService service = new SchemaService(new SchemaConfiguration(BASE_PATH));
    JsonNode json = objectMapper.createObjectNode();
    assertThrows(BadRequestException.class, () -> service.validateMetadata(json));
  }

  @Test
  void testValidateMetadata() throws FileNotFoundException {
    SchemaService service = new SchemaService(new SchemaConfiguration(BASE_PATH));
    ObjectNode json = objectMapper.createObjectNode();
    json.put("requiredField", "string");
    service.validateMetadata(json);
  }
}
