package bio.terra.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.Scope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextCopierTest {

  int checkAndSumAttributeValueFromStream(@NotNull Stream<String> stream) {
    Scope scope = new RequestScope();
    return stream.mapToInt(value -> (int) scope.get("attribute", () -> 1)).sum();
  }

  @Test
  void setsRequestContextInsideThread() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    int streamValuesWithAttribute =
        checkAndSumAttributeValueFromStream(
            RequestContextCopier.parallelWithRequest(Stream.generate(() -> "ignored").limit(2)));
    assertEquals(streamValuesWithAttribute, 2);
  }

  @Test
  void requestContextDoesntFollowToChildThreads() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThrows(
        IllegalStateException.class,
        () ->
            checkAndSumAttributeValueFromStream(
                Stream.generate(() -> "ignored").limit(2).parallel()));
  }
}
