package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "application_exam_arrangements")
public class ApplicationExamArrangement extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "programme_choice_id")
    private UUID programmeChoiceId;

    @Column(name = "exam_type_code", nullable = false, length = 50)
    private String examTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionExamStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "exam_session_id")
    private UUID examSessionId;

    @Column(name = "score", precision = 8, scale = 2)
    private BigDecimal score;

    @Column(name = "outcome_code", length = 50)
    private String outcomeCode;

    @Column(length = 1000)
    private String notes;

    protected ApplicationExamArrangement() {
    }

    public ApplicationExamArrangement(Application application, String examTypeCode, AdmissionExamStatus status) {
        this.application = application;
        this.examTypeCode = examTypeCode;
        this.status = status;
    }
}
