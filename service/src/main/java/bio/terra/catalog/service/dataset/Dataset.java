package bio.terra.catalog.service.dataset;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Data
public class Dataset {
  @Id private UUID id;
  private String datasetId;
  private String storageSystem;
  private String metadata;
  private Instant createdDate;
}
