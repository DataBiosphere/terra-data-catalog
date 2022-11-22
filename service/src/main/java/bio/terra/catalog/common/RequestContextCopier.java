package bio.terra.catalog.common;

import java.util.stream.Stream;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * This class supports creating a parallel {@code Stream} with a copy of the current thread's Spring
 * request attributes copied to the stream's sub-threads. This has the effect of allowing request
 * scope injected fields to be used in classes that are passed to the sub-threads.
 *
 * <p>For example, if {@code service} uses a request scope injection, and you want to run it in
 * parallel, you can write:
 *
 * <pre>{@code
 * int sum = RequestContextCopier.parallelWithRequest(widgets.stream())
 *                  .mapToInt(w -> service.getWeight(w))
 *                  .sum();
 * }</pre>
 *
 * @see RequestContextHolder
 * @see RequestAttributes
 * @see Stream#parallel()
 */
public class RequestContextCopier {

  private final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

  private RequestContextCopier() {}

  private <T> void copyToThread(T target) {
    if (RequestContextHolder.getRequestAttributes() == null) {
      RequestContextHolder.setRequestAttributes(attributes);
    }
  }

  /**
   * Given a {@code Stream}, make a parallel version of it where the current request scope
   * attributes are copied to each of the sub-threads. The parallel version is created using {@link
   * Stream#parallel()}.
   *
   * @param stream the stream to parallelize
   * @return the new parallel stream
   * @param <S> the type of the stream elements
   */
  // This usage of `peek()` is correct, even though it generates a sonar warning. If it's never
  // called (because the element in the stream is never consumed), that's fine.
  @SuppressWarnings("java:S3864")
  public static <S> Stream<S> parallelWithRequest(Stream<S> stream) {
    // Spring request context is limited to the main thread, in order
    // to have the token and auth information available, we need to copy
    // the current thread requestAttributes to the child threads.
    RequestContextCopier copier = new RequestContextCopier();
    return stream.parallel().peek(copier::copyToThread);
  }
}
