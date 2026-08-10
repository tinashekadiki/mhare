package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.ApplicationDocumentRegister;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantProfile;

/** Applicant workspace and staff-verification API contracts. @author Tinashe K */
public final class ApplicantApplicationWorkspaceViews {
    private ApplicantApplicationWorkspaceViews() {
    }

    public record ApplicationWorkspace(
            ApplicationSummary application,
            ApplicantProfile profile,
            List<ApplicationSectionSummary> sections,
            List<NextOfKinSummary> nextOfKin,
            List<EmploymentHistorySummary> employmentHistory,
            List<RefereeSummary> referees,
            List<QualificationSittingSummary> qualifications,
            ApplicationDocumentRegister documents,
            boolean readyForSubmission,
            List<String> missingRequirements,
            Instant declarationAcceptedAt,
            String declarationVersion,
            AdmissionsApplicationWorkflowProgress workflowProgress) {
    }

    public record ApplicationSectionSummary(
            UUID id,
            String code,
            String name,
            boolean required,
            boolean repeatable,
            int minimumRecords,
            int sortOrder,
            String status,
            Instant completedAt,
            String completionSummary,
            long version) {
        static ApplicationSectionSummary from(ApplicationSection section) {
            return new ApplicationSectionSummary(
                    section.getId(), section.getSectionCode(), section.getSectionName(), section.isRequired(),
                    section.isRepeatable(), section.getMinimumRecords(), section.getSortOrder(),
                    section.getStatus().name(), section.getCompletedAt(), section.getCompletionSummary(), section.getVersion());
        }
    }

    public record NextOfKinSummary(
            UUID id,
            String fullName,
            String relationshipCode,
            String phoneNumber,
            String email,
            String address,
            boolean primary,
            long version) {
        static NextOfKinSummary from(ApplicantNextOfKin value) {
            return new NextOfKinSummary(value.getId(), value.getFullName(), value.getRelationshipCode(),
                    value.getPhoneNumber(), value.getEmail(), value.getAddress(), value.isPrimary(), value.getVersion());
        }
    }

    public record EmploymentHistorySummary(
            UUID id,
            String employerName,
            String positionTitle,
            LocalDate startedOn,
            LocalDate endedOn,
            boolean current,
            String responsibilities,
            long version) {
        static EmploymentHistorySummary from(ApplicantEmploymentHistory value) {
            return new EmploymentHistorySummary(value.getId(), value.getEmployerName(), value.getPositionTitle(),
                    value.getStartedOn(), value.getEndedOn(), value.isCurrent(), value.getResponsibilities(), value.getVersion());
        }
    }

    public record RefereeSummary(
            UUID id,
            String fullName,
            String title,
            String organisation,
            String positionTitle,
            String email,
            String phoneNumber,
            String verificationStatus,
            UUID referenceDocumentId,
            String rejectionReason,
            String invitationStatus,
            Instant invitedAt,
            Instant referenceSubmittedAt,
            long version) {
        static RefereeSummary from(ApplicantReferee value, ApplicantRefereeInvitation invitation) {
            return new RefereeSummary(value.getId(), value.getFullName(), value.getTitle(), value.getOrganisation(),
                    value.getPositionTitle(), value.getEmail(), value.getPhoneNumber(),
                    value.getVerificationStatus().name(), value.getReferenceDocumentId(), value.getRejectionReason(),
                    invitation == null ? "NOT_SENT" : invitation.getStatus().name(),
                    invitation == null ? null : invitation.getSentAt(),
                    invitation == null ? null : invitation.getSubmittedAt(),
                    value.getVersion());
        }
    }

    public record QualificationSittingSummary(
            UUID id,
            String level,
            ReferenceOption examBody,
            String institutionName,
            String centreNumber,
            String candidateNumber,
            Integer yearWritten,
            UUID countryId,
            UUID documentId,
            String verificationStatus,
            UUID verifiedByUserId,
            Instant verifiedAt,
            String rejectionReason,
            List<QualificationResultSummary> results,
            long version) {
    }

    public record QualificationResultSummary(
            UUID id,
            ReferenceOption subject,
            String subjectNameSnapshot,
            String grade,
            BigDecimal mark,
            BigDecimal points,
            Boolean principalSubject,
            String resultStatus,
            long version) {
    }

    public record QualificationReferenceData(
            List<ReferenceOption> examBodies,
            List<SubjectReferenceOption> oLevelSubjects,
            List<SubjectReferenceOption> aLevelSubjects,
            List<SubjectReferenceOption> otherSubjects,
            List<GradeReferenceOption> oLevelGrades,
            List<GradeReferenceOption> aLevelGrades) {
    }

    public record QualificationReferenceManagementData(
            List<SubjectReferenceOption> oLevelSubjects,
            List<SubjectReferenceOption> aLevelSubjects,
            List<GradeReferenceOption> oLevelGrades,
            List<GradeReferenceOption> aLevelGrades) {
    }

    public record ReferenceOption(UUID id, String code, String name, Boolean scienceSubject) {
    }

    public record SubjectReferenceOption(
            UUID id,
            String code,
            String name,
            String subjectGroupCode,
            boolean scienceSubject,
            boolean active,
            long version) {
    }

    public record GradeReferenceOption(
            UUID id,
            String grade,
            BigDecimal points,
            boolean pass,
            int sortOrder,
            long version) {
    }

    public record VerificationQueue(
            List<ApplicationSectionVerificationRow> applicationSections,
            List<QualificationSittingVerificationRow> qualifications,
            List<ApplicationDocumentVerificationRow> documents) {
    }

    public record ApplicationSectionVerificationRow(
            UUID applicationId,
            String applicationNumber,
            String applicantName,
            String sectionCode,
            String sectionName,
            String status,
            String completionSummary,
            long version) {
    }

    public record QualificationSittingVerificationRow(
            UUID applicationId,
            String applicationNumber,
            String applicantName,
            QualificationSittingSummary qualification) {
    }

    public record ApplicationDocumentVerificationRow(
            UUID applicationId,
            String applicationNumber,
            String applicantName,
            ApplicationDocumentRegister documents) {
    }
}
