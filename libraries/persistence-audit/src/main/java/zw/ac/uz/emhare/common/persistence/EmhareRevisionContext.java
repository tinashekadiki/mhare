package zw.ac.uz.emhare.common.persistence;

import java.util.Optional;
import org.slf4j.MDC;

public final class EmhareRevisionContext {

    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<String> reason = new ThreadLocal<>();
    private static volatile String serviceName = "unknown-service";

    private EmhareRevisionContext() {
    }

    public static void setServiceName(String configuredServiceName) {
        if (configuredServiceName != null && !configuredServiceName.isBlank()) {
            serviceName = configuredServiceName;
        }
    }

    public static String getServiceName() {
        return serviceName;
    }

    public static Optional<String> getCorrelationId() {
        String requestCorrelationId = correlationId.get();
        return Optional.ofNullable(requestCorrelationId == null ? MDC.get("correlationId") : requestCorrelationId);
    }

    public static Optional<String> getReason() {
        return Optional.ofNullable(reason.get());
    }

    public static void setRequestMetadata(String requestCorrelationId, String revisionReason) {
        correlationId.set(requestCorrelationId);
        reason.set(revisionReason);
    }

    public static void clearRequestMetadata() {
        correlationId.remove();
        reason.remove();
    }
}
