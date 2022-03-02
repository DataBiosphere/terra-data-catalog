package bio.terra.catalog.service.dataset;

import bio.terra.catalog.service.dataset.exception.InvalidDatasetException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DatasetDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public DatasetDao(NamedParameterJdbcTemplate jdbcTemplate) throws SQLException {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public List<Dataset> enumerate() {
    String sql = "SELECT id, dataset_id, storage_system, metadata, created_date FROM dataset";
    MapSqlParameterSource params = new MapSqlParameterSource();
    return jdbcTemplate.query(sql, params, new DatasetMapper());
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public Dataset retrieve(UUID id) {
    String sql =
        "SELECT id, dataset_id, storage_system, metadata, created_date FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
    return jdbcTemplate.queryForObject(sql, params, new DatasetMapper());
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void create(Dataset dataset) throws IOException {
    String sql =
        "INSERT INTO dataset (dataset_id, storage_system, metadata) "
            + "VALUES (:dataset_id, :storage_system, cast(:metadata as jsonb))";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("dataset_id", dataset.getDatasetId())
            .addValue("storage_system", dataset.getStorageSystem())
            .addValue("metadata", dataset.getMetadata());
    try {
      jdbcTemplate.update(sql, params);
    } catch (DuplicateKeyException e) {
      throw new InvalidDatasetException(
          "A dataset for this dataset_id and storage_system already exists");
    }
  }

  @Transactional
  public boolean delete(UUID id) {
    String sql = "DELETE FROM dataset WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
    int rowsAffected = jdbcTemplate.update(sql, params);
    return rowsAffected > 0;
  }

  private static class DatasetMapper implements RowMapper<Dataset> {
    public Dataset mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Dataset()
          .id(rs.getObject("id", UUID.class))
          .datasetId(rs.getString("dataset_id"))
          .storageSystem(rs.getString("storage_system"))
          .metadata(rs.getString("metadata"))
          .createdDate(rs.getTimestamp("created_date").toInstant());
    }
  }
}
