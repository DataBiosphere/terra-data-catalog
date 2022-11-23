package bio.terra.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextCopierTest {
  @Test
  void setsRequestContextInsideThread() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("attribute", "value");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    RequestContextCopier.parallelWithRequest(Stream.of(1, 2))
        .forEach(
            val ->
                assertEquals(
                    Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                        .getAttribute("attribute", RequestAttributes.SCOPE_REQUEST),
                    "value"));
  }
}
