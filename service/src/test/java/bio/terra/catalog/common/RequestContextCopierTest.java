package bio.terra.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.Scope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestContextCopierTest {
  public static final int THREADS = 2;

  int runTestWithProvider(Function<Stream<String>, Stream<String>> provider) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    Scope scope = new RequestScope();
    return provider
        .apply(Stream.generate(() -> "ignored").limit(THREADS))
        .mapToInt(value -> (int) scope.get("attribute", () -> 1))
        .sum();
  }

  @Test
  void setsRequestContextInsideThread() {
    assertEquals(THREADS, runTestWithProvider(RequestContextCopier::parallelWithRequest));
  }

  @Test
  @Disabled(
      value =
          "This is disabled because the way in which Java optimizes parallelization is tricky to test in junit. It works consistently in the positive test, but in the negative test it sometimes optimizes things away where we would like it not to.")
  void requestContextDoesntFollowToChildThreads() {
    assertThrows(IllegalStateException.class, () -> runTestWithProvider(Stream::parallel));
  }
}
