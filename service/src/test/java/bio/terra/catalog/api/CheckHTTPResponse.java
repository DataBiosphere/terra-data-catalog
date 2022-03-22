package bio.terra.catalog.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.catalog.app.App;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
public class CheckHTTPResponse {
  @LocalServerPort private int port;

  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  public void shouldPassIfStringMatches() {
    assertEquals(
        "Hello World from String Boot",
        testRestTemplate.getForObject("http://localhost:" + port + "/", String.class));
  }
}
