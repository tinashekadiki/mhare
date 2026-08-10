package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "applicant_qualification_results")
public class ApplicantQualificationResult extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_sitting_id", nullable = false)
    private ApplicantQualificationSitting qualificationSitting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private AdmissionSubject subject;

    @Column(name = "subject_name_snapshot", nullable = false, length = 150)
    private String subjectNameSnapshot;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(precision = 8, scale = 2)
    private BigDecimal mark;

    @Column(precision = 8, scale = 2)
    private BigDecimal points;

    @Column(name = "is_principal_subject")
    private Boolean principalSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 30)
    private QualificationResultStatus resultStatus;

    protected ApplicantQualificationResult() {
    }

    public ApplicantQualificationResult(ApplicantQualificationSitting qualificationSitting, AdmissionSubject subject, String subjectNameSnapshot, String grade) {
        this.qualificationSitting = qualificationSitting;
        this.subject = subject;
        this.subjectNameSnapshot = subjectNameSnapshot;
        this.grade = grade;
        this.resultStatus = QualificationResultStatus.CAPTURED;
    }

    public ApplicantQualificationResult(
            ApplicantQualificationSitting qualificationSitting,
            AdmissionSubject subject,
            String subjectNameSnapshot,
            String grade,
            BigDecimal mark,
            BigDecimal points,
            Boolean principalSubject) {
        this(qualificationSitting, subject, subjectNameSnapshot, grade);
        this.mark = mark;
        this.points = points;
        this.principalSubject = principalSubject;
    }

    public void update(String grade, BigDecimal mark, BigDecimal points, Boolean principalSubject) {
        if (resultStatus == QualificationResultStatus.VERIFIED) {
            throw new IllegalStateException("A verified qualification result cannot be edited.");
        }
        if (grade == null || grade.isBlank()) throw new IllegalArgumentException("Grade is required.");
        this.grade = grade.trim().toUpperCase(java.util.Locale.ROOT);
        this.mark = mark;
        this.points = points;
        this.principalSubject = principalSubject;
        this.resultStatus = QualificationResultStatus.CAPTURED;
    }

    public void applyCalculatedPoints(BigDecimal calculatedPoints) {
        this.points = calculatedPoints;
    }

    public void reopenForApplicantCorrection() {
        resultStatus = QualificationResultStatus.CAPTURED;
    }

    public void verify() { resultStatus = QualificationResultStatus.VERIFIED; }
    public void reject() { resultStatus = QualificationResultStatus.REJECTED; }

    public ApplicantQualificationSitting getQualificationSitting() {
        return qualificationSitting;
    }

    public AdmissionSubject getSubject() {
        return subject;
    }

    public String getSubjectNameSnapshot() {
        return subjectNameSnapshot;
    }

    public String getGrade() {
        return grade;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public BigDecimal getMark() { return mark; }
    public Boolean getPrincipalSubject() { return principalSubject; }

    public QualificationResultStatus getResultStatus() {
        return resultStatus;
    }
}
