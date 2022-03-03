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
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
    try {
      return jdbcTemplate.queryForObject(sql, params, new DatasetMapper());
    } catch (EmptyResultDataAccessException ex) {
      throw new DatasetNotFoundException("Dataset not found for id " + id);
    }
  }

  @WriteTransaction
  public Dataset create(Dataset dataset) {
    String sql =
        "INSERT INTO dataset (dataset_id, storage_system, metadata) "
            + "VALUES (:dataset_id, :storage_system, cast(:metadata as jsonb))";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("dataset_id", dataset.datasetId())
            .addValue("storage_system", String.valueOf(dataset.storageSystem()))
            .addValue("metadata", dataset.metadata());
    DaoKeyHolder keyHolder = new DaoKeyHolder();
    try {
      jdbcTemplate.update(sql, params, keyHolder);
    } catch (DuplicateKeyException e) {
      throw new InvalidDatasetException(
          "A dataset for this dataset_id and storage_system already exists");
    }

    return new Dataset(
        keyHolder.getId(),
        keyHolder.getString("dataset_id"),
        StorageSystem.valueOf(keyHolder.getString("storage_system")),
        keyHolder.getString("metadata"),
        keyHolder.getTimestamp("created_date").toInstant());
  }

  @WriteTransaction
  public boolean delete(UUID id) {
    String sql = "DELETE FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
    int rowsAffected = jdbcTemplate.update(sql, params);
    return rowsAffected > 0;
  }

  private static class DatasetMapper implements RowMapper<Dataset> {
    public Dataset mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Dataset(
          rs.getObject("id", UUID.class),
          rs.getString("dataset_id"),
          StorageSystem.valueOf(rs.getString("storage_system")),
          rs.getString("metadata"),
          rs.getTimestamp("created_date").toInstant());
    }
  }
}
