package bio.terra.catalog.common;

import bio.terra.catalog.model.DatasetPreviewTable;
import bio.terra.catalog.model.SystemStatusSystems;
import bio.terra.catalog.model.TableMetadata;
import bio.terra.catalog.service.dataset.DatasetAccessLevel;
import bio.terra.common.iam.AuthenticatedUserRequest;
import java.util.List;
import java.util.Map;

/**
 * An implementation of a storage system for catalog. A storage system adds support to catalog by
 * implementing these APIs.
 */
public interface StorageSystemService {

  /**
   * @return an object indicating the system's status
   */
  SystemStatusSystems status();

  /**
   * Return all objects in the storage system that are visible to the user, and the storage system
   * information for each object.
   *
   * @param user the user
   * @return a map of storage object ID to access level
   */
  Map<String, StorageSystemInformation> getDatasets(AuthenticatedUserRequest user);

  /**
   * Given a storage object, return the user's access level.
   *
   * @param user the user
   * @param storageSourceId the storage object ID
   * @return the user's access level
   */
  DatasetAccessLevel getRole(AuthenticatedUserRequest user, String storageSourceId);

  /**
   * Return a list of tables for a storage object that are visible to the user. For each table, its
   * name and a flag indicating if the table has data is returned.
   *
   * @param user the user
   * @param storageSourceId the storage object ID
   * @return the tables in the object
   */
  List<TableMetadata> getPreviewTables(AuthenticatedUserRequest user, String storageSourceId);

  /**
   * Generate a preview of a table in a storage object. A preview contains columns information and
   * rows of data.
   *
   * @param user the user
   * @param storageSourceId the storage object ID
   * @param tableName the name of the table to preview
   * @param maxRows the maximum number of rows to return
   * @return the table preview data
   */
  DatasetPreviewTable previewTable(
      AuthenticatedUserRequest user, String storageSourceId, String tableName, int maxRows);

  /**
   * Export the tables in a storage object to a Terra workspace.
   *
   * @param user the user
   * @param storageSourceId the storage object ID
   * @param workspaceIdDest the workspace ID that the object's tables will be exported to
   */
  void exportToWorkspace(
      AuthenticatedUserRequest user, String storageSourceId, String workspaceIdDest);
}
