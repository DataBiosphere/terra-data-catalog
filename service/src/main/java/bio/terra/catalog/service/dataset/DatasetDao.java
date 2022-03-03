package bio.terra.catalog.service.dataset;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetDao extends CrudRepository<Dataset, UUID> {}
