package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "login_events")
public class LoginEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private PlatformUser user;

    @Column(name = "keycloak_user_id")
    private UUID keycloakUserId;

    @Column(length = 150)
    private String username;

    @Column(length = 200)
    private String email;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "identity_session_id", length = 150)
    private String identitySessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoginOutcome outcome;

    protected LoginEvent() {
    }

    public LoginEvent(
            PlatformUser user,
            UUID keycloakUserId,
            String username,
            String email,
            String ipAddress,
            String userAgent,
            String identitySessionId,
            LoginOutcome outcome) {
        this.user = user;
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.identitySessionId = identitySessionId;
        this.outcome = outcome;
        this.occurredAt = Instant.now();
    }

    public PlatformUser getUser() {
        return user;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public String getIdentitySessionId() {
        return identitySessionId;
    }
}
