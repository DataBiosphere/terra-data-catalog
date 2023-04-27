package bio.terra.catalog.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.catalog.datarepo.DatarepoService;
import bio.terra.catalog.iam.SamService;
import bio.terra.catalog.rawls.RawlsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CatalogStatusServiceTest {
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Mock private JdbcTemplate jdbcTemplate;

  private CatalogStatusService catalogStatusService;

  @BeforeEach
  public void beforeEach() {
    catalogStatusService =
        new CatalogStatusService(
            namedParameterJdbcTemplate,
            null,
            mock(SamService.class),
            mock(DatarepoService.class),
            mock(RawlsService.class));
    when(namedParameterJdbcTemplate.getJdbcTemplate()).thenReturn(jdbcTemplate);
  }

  @Test
  void databaseStatus() {
    when(jdbcTemplate.execute(ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
        .thenReturn(true);
    assertTrue(catalogStatusService.databaseStatus().isOk());
  }

  @Test
  void databaseStatusError() {
    var message = "expected error message";
    when(jdbcTemplate.execute(ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
        .thenThrow(new DataAccessException(message) {});
    var status = catalogStatusService.databaseStatus();
    assertFalse(status.isOk());
    assertThat(status.getMessages(), hasItem(containsString(message)));
  }
}
