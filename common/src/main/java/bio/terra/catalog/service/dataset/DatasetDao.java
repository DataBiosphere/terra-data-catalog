package bio.terra.catalog.service.dataset;

import bio.terra.catalog.common.DaoKeyHolder;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.InternalServerErrorException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatasetDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private static final String ID_FIELD = "id";
  private static final String STORAGE_SOURCE_ID_FIELD = "storage_source_id";
  private static final String STORAGE_SYSTEM_FIELD = "storage_system";
  private static final String METADATA_FIELD = "metadata";
  private static final String CREATED_DATE_FIELD = "created_date";

  @Autowired
  public DatasetDao(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @VisibleForTesting
  protected ObjectNode toJsonNode(String json) {
    try {
      return objectMapper.readValue(json, ObjectNode.class);
    } catch (JsonProcessingException e) {
      // This should never occur because the data is validated and stored as JSONB in postgres
      throw new InternalServerErrorException(
          "Catalog metadata must be a valid json object in database", e);
    }
  }

  @ReadTransaction
  public Dataset retrieve(DatasetId id) {
    String sql =
        "SELECT id, storage_source_id, storage_system, metadata, created_date FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue(ID_FIELD, id.uuid());
    try {
      return jdbcTemplate.queryForObject(sql, params, new DatasetMapper());
    } catch (EmptyResultDataAccessException ex) {
      throw new DatasetNotFoundException("Dataset not found for " + id, ex);
    }
  }

  private Dataset createOrUpdate(String sql, MapSqlParameterSource params) {
    DaoKeyHolder keyHolder = new DaoKeyHolder();
    int rowsAffected;
    rowsAffected = jdbcTemplate.update(sql, params, keyHolder);
    if (rowsAffected != 1) {
      throw new DatasetNotFoundException("Dataset not found");
    }
    return new Dataset(
        keyHolder.getId(),
        keyHolder.getString(STORAGE_SOURCE_ID_FIELD),
        StorageSystem.valueOf(keyHolder.getString(STORAGE_SYSTEM_FIELD)),
        toJsonNode(keyHolder.getField(METADATA_FIELD, PGobject.class).toString()),
        keyHolder.getCreatedDate());
  }

  @WriteTransaction
  public Dataset upsert(Dataset dataset) {
    String sql =
        "INSERT INTO dataset (storage_source_id, storage_system, metadata) "
            + "VALUES (:storage_source_id, :storage_system, cast(:metadata as jsonb)) "
            + "ON CONFLICT ON CONSTRAINT dataset_unique_constraint DO UPDATE SET metadata = cast(:metadata as jsonb)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue(STORAGE_SOURCE_ID_FIELD, dataset.storageSourceId())
            .addValue(STORAGE_SYSTEM_FIELD, String.valueOf(dataset.storageSystem()))
            .addValue(METADATA_FIELD, dataset.metadata().toString());
    return createOrUpdate(sql, params);
  }

  @WriteTransaction
  public void update(Dataset dataset) {
    String sql =
        "UPDATE dataset "
            + "SET storage_source_id = :storage_source_id, storage_system = :storage_system, "
            + "metadata = cast(:metadata as jsonb) "
            + "WHERE id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue(ID_FIELD, dataset.id().uuid())
            .addValue(STORAGE_SOURCE_ID_FIELD, dataset.storageSourceId())
            .addValue(STORAGE_SYSTEM_FIELD, String.valueOf(dataset.storageSystem()))
            .addValue(METADATA_FIELD, dataset.metadata().toString());
    createOrUpdate(sql, params);
  }

  @WriteTransaction
  public boolean delete(Dataset dataset) {
    String sql = "DELETE FROM dataset WHERE id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue(ID_FIELD, dataset.id().uuid());
    int rowsAffected = jdbcTemplate.update(sql, params);
    return rowsAffected > 0;
  }

  private class DatasetMapper implements RowMapper<Dataset> {
    public Dataset mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Dataset(
          new DatasetId(rs.getObject(ID_FIELD, UUID.class)),
          rs.getString(STORAGE_SOURCE_ID_FIELD),
          StorageSystem.valueOf(rs.getString(STORAGE_SYSTEM_FIELD)),
          toJsonNode(rs.getString(METADATA_FIELD)),
          rs.getTimestamp(CREATED_DATE_FIELD).toInstant());
    }
  }

  @ReadTransaction
  // This code is safe because it builds a template query string using ?s only. It relies on
  // JdbcTemplate to perform all text substitutions.
  @SuppressWarnings("java:S2077")
  public List<Dataset> find(Map<StorageSystem, Collection<String>> systemsAndIds) {
    String query = "(storage_system = ? AND storage_source_id IN (%s))";
    List<Object> args = new ArrayList<>();
    String whereClause =
        systemsAndIds.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(
                entry -> {
                  args.add(String.valueOf(entry.getKey()));
                  args.addAll(entry.getValue());
                  return String.format(
                      query,
                      Stream.generate(() -> "?")
                          .limit(entry.getValue().size())
                          .collect(Collectors.joining(", ")));
                })
            .collect(Collectors.joining(" OR "));

    if (whereClause.isEmpty()) {
      return List.of();
    }

    String sql = "SELECT * FROM dataset WHERE " + whereClause;
    return jdbcTemplate.getJdbcTemplate().query(sql, new DatasetMapper(), args.toArray());
  }

  @ReadTransaction
  public List<Dataset> listAllDatasets() {
    String sql = "SELECT * FROM dataset";
    return jdbcTemplate.query(sql, new DatasetMapper());
  }

  @ReadTransaction
  public List<Dataset> listAllDatasets(StorageSystem storageSystem) {
    String sql = "SELECT * FROM dataset WHERE storage_system = :storage_system";
    var param = Map.of(STORAGE_SYSTEM_FIELD, String.valueOf(storageSystem));
    return jdbcTemplate.query(sql, param, new DatasetMapper());
  }
}
