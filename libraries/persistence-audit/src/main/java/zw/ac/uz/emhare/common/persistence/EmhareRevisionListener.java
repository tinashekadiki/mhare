package zw.ac.uz.emhare.common.persistence;

import java.util.Optional;
import java.util.UUID;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class EmhareRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        EmhareRevisionEntity emhareRevisionEntity = (EmhareRevisionEntity) revisionEntity;
        emhareRevisionEntity.setActorUserId(resolveCurrentUserId().orElse(null));
        emhareRevisionEntity.setServiceName(EmhareRevisionContext.getServiceName());
        emhareRevisionEntity.setCorrelationId(EmhareRevisionContext.getCorrelationId().orElse(null));
        emhareRevisionEntity.setReason(EmhareRevisionContext.getReason().orElse(null));
    }

    private Optional<UUID> resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return resolveAuditor(jwtAuthenticationToken.getToken());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return resolveAuditor(jwt);
        }
        return Optional.empty();
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private Optional<UUID> resolveAuditor(Jwt jwt) {
        Optional<UUID> localUserId = parseUuid(jwt.getClaimAsString("emhare_user_id"));
        return localUserId.isPresent() ? localUserId : parseUuid(jwt.getSubject());
    }
}
