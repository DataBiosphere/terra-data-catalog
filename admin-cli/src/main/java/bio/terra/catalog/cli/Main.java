package bio.terra.catalog.cli;

import bio.terra.catalog.service.JsonValidationService;
import bio.terra.catalog.service.dataset.DatasetDao;
import bio.terra.catalog.service.dataset.DatasetId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class Main {

  private final DatasetDao datasetDao;
  private final JsonValidationService jsonValidationService;
  private final ObjectMapper objectMapper;

  private final Map<String, Consumer<ApplicationArguments>> commands;

  Main(
      DatasetDao datasetDao,
      JsonValidationService jsonValidationService,
      ObjectMapper objectMapper) {
    this.datasetDao = datasetDao;
    this.jsonValidationService = jsonValidationService;
    this.objectMapper = objectMapper;

    commands = Map.of("list", this::list, "validate", this::validate, "get", this::get);
  }

  public void run(ApplicationArguments args) {
    if (args.getNonOptionArgs().size() != 1) {
      fail("One argument is required");
    }
    var arg = args.getNonOptionArgs().get(0);
    var command = commands.get(arg);
    if (command != null) {
      command.accept(args);
      return;
    }
    fail("unknown command " + arg);
  }

  private void fail(String s) {
    fail(s, true);
  }

  private void fail(String s, boolean includeHelp) {
    String commandHelp = "";
    if (includeHelp) {
      commandHelp =
          ". Valid commands are: "
              + commands.keySet().stream().collect(Collectors.joining(", ", "'", "'"));
    }
    System.err.println(s + commandHelp);
    System.exit(-1);
  }

  private void outputAsJson(Object o) {
    try {
      objectMapper.writeValue(System.out, o);
    } catch (IOException e) {
      fail("An error occurred: " + e, false);
    }
  }

  private void list(ApplicationArguments args) {
    outputAsJson(datasetDao.listAllDatasets());
  }

  private void get(ApplicationArguments args) {
    if (!args.containsOption("id")) {
      fail("the 'get' command requires at least one '--id=<uuid>' argument");
    }
    var datasets =
        args.getOptionValues("id").stream()
            .map(UUID::fromString)
            .map(DatasetId::new)
            .map(datasetDao::retrieve)
            .toList();
    outputAsJson(datasets);
  }

  record ValidationResult(UUID id, List<String> messages) {
    ValidationResult(DatasetId id, Set<ValidationMessage> messages) {
      this(id.uuid(), messages.stream().map(ValidationMessage::getMessage).toList());
    }
  }

  private ObjectNode toJson(String metadata) {
    try {
      return objectMapper.readValue(metadata, ObjectNode.class);
    } catch (JsonProcessingException e) {
      fail("An error occurred: " + e, false);
      return null;
    }
  }

  private void validate(ApplicationArguments args) {
    var results =
        datasetDao.listAllDatasets().stream()
            .map(
                dataset ->
                    new ValidationResult(
                        dataset.id(), jsonValidationService.validate(toJson(dataset.metadata()))))
            .filter(result -> !result.messages.isEmpty())
            .toList();
    outputAsJson(results);
  }
}
