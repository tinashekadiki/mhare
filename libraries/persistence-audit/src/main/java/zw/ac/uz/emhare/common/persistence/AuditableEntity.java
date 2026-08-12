package zw.ac.uz.emhare.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Audited
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by_user_id", updatable = false)
    private UUID createdByUserId;

    @LastModifiedBy
    @Column(name = "modified_by_user_id")
    private UUID modifiedByUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getModifiedByUserId() {
        return modifiedByUserId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedByUserId() {
        return deletedByUserId;
    }

    public long getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(UUID actorUserId) {
        deletedAt = Instant.now();
        deletedByUserId = actorUserId;
    }
}
