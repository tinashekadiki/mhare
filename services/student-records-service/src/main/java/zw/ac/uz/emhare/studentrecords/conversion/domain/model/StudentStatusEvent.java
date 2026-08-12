package zw.ac.uz.emhare.studentrecords.conversion.domain.model;

import zw.ac.uz.emhare.studentrecords.conversion.*;

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

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_status_events")
public class StudentStatusEvent extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private StudentStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private StudentStatus toStatus;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected StudentStatusEvent() {
    }

    public StudentStatusEvent(StudentProfile student, StudentStatus fromStatus, StudentStatus toStatus, String reason, Instant changedAt) {
        this.student = student;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}
