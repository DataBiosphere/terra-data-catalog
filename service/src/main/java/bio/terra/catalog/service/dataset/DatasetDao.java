package bio.terra.catalog.service.dataset;

import bio.terra.catalog.common.DaoKeyHolder;
import bio.terra.catalog.common.StorageSystem;
import bio.terra.catalog.service.dataset.exception.DatasetNotFoundException;
import bio.terra.catalog.service.dataset.exception.InvalidDatasetException;
import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatasetDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private static final String ID_FIELD = "id";
  private static final String DATASET_ID_FIELD = "dataset_id";
  private static final String STORAGE_SYSTEM_FIELD = "storage_system";
  private static final String METADATA_FIELD = "metadata";
  private static final String CREATED_DATE_FIELD = "created_date";

  @Autowired
  public DatasetDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @ReadTransaction
  public List<Dataset> enumerate() {
    String sql = "SELECT id, dataset_id, storage_system, metadata, created_date FROM dataset";
    MapSqlParameterSource params = new MapSqlParameterSource();
    return jdbcTemplate.query(sql, params, new DatasetMapper());
  }

  @ReadTransaction
  public Dataset retrieve(UUID id) {
    String sql =
        "SELECT id, dataset_id, storage_system, metadata, created_date FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue(ID_FIELD, id);
    try {
      return jdbcTemplate.queryForObject(sql, params, new DatasetMapper());
    } catch (EmptyResultDataAccessException ex) {
      throw new DatasetNotFoundException("Dataset not found for id " + id, ex);
    }
  }

  private Dataset createOrUpdate(String sql, MapSqlParameterSource params) {
    DaoKeyHolder keyHolder = new DaoKeyHolder();
    try {
      jdbcTemplate.update(sql, params, keyHolder);
    } catch (EmptyResultDataAccessException ex) {
      throw new DatasetNotFoundException("Dataset not found", ex);
    } catch (DuplicateKeyException ex) {
      throw new InvalidDatasetException(
          "A dataset for this dataset_id and storage_system already exists", ex);
    }
    return new Dataset(
        keyHolder.getId(),
        keyHolder.getString(DATASET_ID_FIELD),
        StorageSystem.valueOf(keyHolder.getString(STORAGE_SYSTEM_FIELD)),
        keyHolder.getString(METADATA_FIELD),
        keyHolder.getTimestamp(CREATED_DATE_FIELD).toInstant());
  }

  @WriteTransaction
  public Dataset create(Dataset dataset) {
    String sql =
        "INSERT INTO dataset (dataset_id, storage_system, metadata) "
            + "VALUES (:dataset_id, :storage_system, cast(:metadata as jsonb))";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue(DATASET_ID_FIELD, dataset.datasetId())
            .addValue(STORAGE_SYSTEM_FIELD, String.valueOf(dataset.storageSystem()))
            .addValue(METADATA_FIELD, dataset.metadata());
    return createOrUpdate(sql, params);
  }

  @WriteTransaction
  public Dataset update(Dataset dataset) {
    String sql =
        "UPDATE dataset "
            + "SET dataset_id = :dataset_id, storage_system = :storage_system, "
            + "metadata = cast(:metadata as jsonb) "
            + "WHERE id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue(ID_FIELD, dataset.id())
            .addValue(DATASET_ID_FIELD, dataset.datasetId())
            .addValue(STORAGE_SYSTEM_FIELD, String.valueOf(dataset.storageSystem()))
            .addValue(METADATA_FIELD, dataset.metadata());
    return createOrUpdate(sql, params);
  }

  @WriteTransaction
  public boolean delete(UUID id) {
    String sql = "DELETE FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue(ID_FIELD, id);
    int rowsAffected = jdbcTemplate.update(sql, params);
    return rowsAffected > 0;
  }

  private static class DatasetMapper implements RowMapper<Dataset> {
    public Dataset mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Dataset(
          rs.getObject(ID_FIELD, UUID.class),
          rs.getString(DATASET_ID_FIELD),
          StorageSystem.valueOf(rs.getString(STORAGE_SYSTEM_FIELD)),
          rs.getString(METADATA_FIELD),
          rs.getTimestamp(CREATED_DATE_FIELD).toInstant());
    }
  }
}
