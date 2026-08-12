package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_keycloak_user_id", columnNames = "keycloak_user_id"),
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        })
public class PlatformUser extends AuditableEntity {

    @Column(name = "keycloak_user_id")
    private UUID keycloakUserId;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected PlatformUser() {
    }

    public PlatformUser(UUID keycloakUserId, String username, String email, String displayName) {
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.status = UserStatus.INVITED;
    }

    public void activate() {
        status = UserStatus.ACTIVE;
    }

    public void syncFromIdentityProvider(UUID identityProviderUserId, String preferredUsername, String primaryEmail, String name) {
        linkIdentityProvider(identityProviderUserId, preferredUsername, primaryEmail, name);
        lastLoginAt = Instant.now();
    }

    public void linkIdentityProvider(
            UUID identityProviderUserId,
            String preferredUsername,
            String primaryEmail,
            String name) {
        if (identityProviderUserId == null) {
            throw new IllegalArgumentException("Keycloak user id is required.");
        }
        if (keycloakUserId != null && !keycloakUserId.equals(identityProviderUserId)) {
            throw new IllegalStateException("The local user is already linked to a different Keycloak identity.");
        }
        keycloakUserId = identityProviderUserId;
        username = preferredUsername == null || preferredUsername.isBlank() ? primaryEmail : preferredUsername;
        email = primaryEmail == null || primaryEmail.isBlank() ? username : primaryEmail;
        displayName = name == null || name.isBlank() ? email : name;
    }

    public void updateProfile(String newDisplayName, String newPhoneNumber, UserStatus newStatus) {
        displayName = newDisplayName;
        phoneNumber = newPhoneNumber;
        status = newStatus;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
