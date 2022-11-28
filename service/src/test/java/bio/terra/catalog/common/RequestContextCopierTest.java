package bio.terra.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextCopierTest {

  int checkAndSumAttributeValueFromStream(@NotNull Stream<String> stream) {
    return stream
        .mapToInt(
            value -> {
              RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
              try {
                // We need to sleep to force the other threads not to reuse the current thread
                Thread.sleep(100);
              } catch (InterruptedException e) {
                return 0;
              }
              return requestAttributes == null
                  ? 0
                  : (int)
                      Objects.requireNonNullElse(
                          requestAttributes.getAttribute(
                              "attribute", RequestAttributes.SCOPE_REQUEST),
                          0);
            })
        .sum();
  }

  @Test
  void setsRequestContextInsideThread() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("attribute", 1);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    int streamValuesWithAttribute =
        checkAndSumAttributeValueFromStream(
            RequestContextCopier.parallelWithRequest(Stream.generate(() -> "ignored").limit(5)));
    assertEquals(streamValuesWithAttribute, 5);
  }

  @Test
  void requestContextDoesntFollowToChildThreads() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("attribute", 1);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    int streamValuesWithAttribute =
        checkAndSumAttributeValueFromStream(Stream.generate(() -> "ignored").limit(5).parallel());
    // This should be one, because one of the threads used will be the original
    assertEquals(streamValuesWithAttribute, 1);
  }
}
