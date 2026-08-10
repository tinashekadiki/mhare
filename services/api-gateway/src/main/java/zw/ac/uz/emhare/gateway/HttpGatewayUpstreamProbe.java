package zw.ac.uz.emhare.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Performs bounded, read-only health probes against configured gateway upstreams. @author Tinashe K */
@Component
class HttpGatewayUpstreamProbe implements GatewayUpstreamProbe {

    private final HttpClient httpClient;

    HttpGatewayUpstreamProbe() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpGatewayUpstreamProbe(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GatewayUpstreamProbeResult probe(URI serviceBaseUri, Duration timeout) {
        URI healthUri = serviceBaseUri.resolve("/actuator/health");
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        long startedAtNanoseconds = System.nanoTime();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long responseTimeMilliseconds = elapsedMilliseconds(startedAtNanoseconds);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return GatewayUpstreamProbeResult.available(response.statusCode(), responseTimeMilliseconds);
            }
            return GatewayUpstreamProbeResult.unavailable(
                    response.statusCode(), responseTimeMilliseconds, "Health endpoint returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return GatewayUpstreamProbeResult.unavailable(null, elapsedMilliseconds(startedAtNanoseconds), "Health probe interrupted");
        } catch (IOException | RuntimeException exception) {
            return GatewayUpstreamProbeResult.unavailable(
                    null, elapsedMilliseconds(startedAtNanoseconds), conciseFailure(exception));
        }
    }

    private static long elapsedMilliseconds(long startedAtNanoseconds) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanoseconds).toMillis();
    }

    private static String conciseFailure(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
