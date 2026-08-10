package zw.ac.uz.emhare.gateway;

import java.net.URI;
import java.time.Duration;

/** @author Tinashe K */
@FunctionalInterface
interface GatewayUpstreamProbe {

    GatewayUpstreamProbeResult probe(URI serviceBaseUri, Duration timeout);
}

record GatewayUpstreamProbeResult(boolean available, Integer httpStatus, long responseTimeMilliseconds, String failure) {

    static GatewayUpstreamProbeResult available(int httpStatus, long responseTimeMilliseconds) {
        return new GatewayUpstreamProbeResult(true, httpStatus, responseTimeMilliseconds, null);
    }

    static GatewayUpstreamProbeResult unavailable(Integer httpStatus, long responseTimeMilliseconds, String failure) {
        return new GatewayUpstreamProbeResult(false, httpStatus, responseTimeMilliseconds, failure);
    }
}
