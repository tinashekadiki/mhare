package zw.ac.uz.emhare.admissions.application;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantIdentityNameCorrection;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantIdentityNameCorrectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;

/**
 * Owns applicant review and staff approval of identity-document name mismatches. @author Tinashe K
 */
@Service
public class ApplicantIdentityNameCorrectionService {

  private final ApplicationRepository applicationRepository;
  private final ApplicantRepository applicantRepository;
  private final ApplicationDocumentRepository applicationDocumentRepository;
  private final ApplicantIdentityNameCorrectionRepository correctionRepository;
  private final Clock clock;

  public ApplicantIdentityNameCorrectionService(
      ApplicationRepository applicationRepository,
      ApplicantRepository applicantRepository,
      ApplicationDocumentRepository applicationDocumentRepository,
      ApplicantIdentityNameCorrectionRepository correctionRepository,
      Clock clock) {
    this.applicationRepository = applicationRepository;
    this.applicantRepository = applicantRepository;
    this.applicationDocumentRepository = applicationDocumentRepository;
    this.correctionRepository = correctionRepository;
    this.clock = clock;
  }

  @Transactional
  public IdentityNameCorrectionSummary reviewOcrReading(
      UUID applicationId,
      UUID applicantUserId,
      UUID documentId,
      String documentFirstName,
      String documentMiddleNames,
      String documentLastName) {
    Application application = requireApplicantOwnedApplication(applicationId, applicantUserId);
    requireCurrentApplicationDocument(applicationId, documentId);
    ApplicantIdentityNameCorrection correction =
        correctionRepository
            .findByApplicationIdAndDocumentIdAndDeletedAtIsNull(applicationId, documentId)
            .orElseGet(
                () ->
                    new ApplicantIdentityNameCorrection(
                        application,
                        documentId,
                        documentFirstName,
                        documentMiddleNames,
                        documentLastName));
    if (correction.getId() != null) {
      correction.reviewOcrReading(documentFirstName, documentMiddleNames, documentLastName);
    }
    return IdentityNameCorrectionSummary.from(correctionRepository.saveAndFlush(correction));
  }

  @Transactional
  public IdentityNameCorrectionSummary requestOfficialNameCorrection(
      UUID applicationId,
      UUID applicantUserId,
      UUID documentId,
      String documentFirstName,
      String documentMiddleNames,
      String documentLastName,
      String reason) {
    IdentityNameCorrectionSummary reviewed =
        reviewOcrReading(
            applicationId,
            applicantUserId,
            documentId,
            documentFirstName,
            documentMiddleNames,
            documentLastName);
    if (namesMatch(reviewed.registeredName(), reviewed.documentName())) {
      throw new IllegalArgumentException(
          "The corrected document reading already matches the registered account name.");
    }
    ApplicantIdentityNameCorrection correction =
        correctionRepository
            .findById(reviewed.id())
            .orElseThrow(
                () -> new IllegalStateException("Identity-name correction was not saved."));
    correction.request(applicantUserId, reason, clock.instant());
    return IdentityNameCorrectionSummary.from(correctionRepository.saveAndFlush(correction));
  }

  @Transactional(readOnly = true)
  public ApprovalContext approvalContext(UUID correctionId) {
    ApplicantIdentityNameCorrection correction = requireCorrection(correctionId);
    if (!"REQUESTED".equals(correction.getStatus().name())) {
      throw new IllegalStateException("Only a requested official-name correction can be approved.");
    }
    return new ApprovalContext(
        correction.getId(),
        correction.getApplication().getId(),
        correction.getDocumentId(),
        correction.getApplicant().getUserId(),
        correction.getDocumentFirstName(),
        correction.getDocumentMiddleNames(),
        correction.getDocumentLastName());
  }

  @Transactional
  public IdentityNameCorrectionSummary completeApproval(
      UUID correctionId, UUID staffUserId, String reason) {
    ApplicantIdentityNameCorrection correction = requireCorrection(correctionId);
    correction
        .getApplicant()
        .synchronizeApprovedOfficialName(
            correction.getDocumentFirstName(),
            correction.getDocumentMiddleNames(),
            correction.getDocumentLastName());
    correction
        .getApplication()
        .synchronizeOfficialName(
            correction.getDocumentFirstName(),
            correction.getDocumentMiddleNames(),
            correction.getDocumentLastName());
    correction.approve(staffUserId, reason, clock.instant());
    applicantRepository.save(correction.getApplicant());
    applicationRepository.save(correction.getApplication());
    return IdentityNameCorrectionSummary.from(correctionRepository.saveAndFlush(correction));
  }

  @Transactional
  public IdentityNameCorrectionSummary reject(UUID correctionId, UUID staffUserId, String reason) {
    ApplicantIdentityNameCorrection correction = requireCorrection(correctionId);
    correction.reject(staffUserId, reason, clock.instant());
    return IdentityNameCorrectionSummary.from(correctionRepository.saveAndFlush(correction));
  }

  @Transactional(readOnly = true)
  public IdentityNameCorrectionSummary latestForApplication(UUID applicationId) {
    return correctionRepository
        .findFirstByApplicationIdAndStatusNotAndDeletedAtIsNullOrderByUpdatedAtDesc(
            applicationId,
            zw.ac.uz.emhare.admissions.domain.model.IdentityNameCorrectionStatus.SUPERSEDED)
        .map(IdentityNameCorrectionSummary::from)
        .filter(IdentityNameCorrectionSummary::hasMismatch)
        .orElse(null);
  }

  private Application requireApplicantOwnedApplication(UUID applicationId, UUID applicantUserId) {
    return applicationRepository
        .findById(applicationId)
        .filter(application -> !application.isDeleted())
        .filter(application -> application.getApplicant().getUserId().equals(applicantUserId))
        .orElseThrow(
            () -> new AccessDeniedException("Application was not found for this applicant."));
  }

  private void requireCurrentApplicationDocument(UUID applicationId, UUID documentId) {
    applicationDocumentRepository
        .findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(documentId)
        .filter(document -> document.getApplication().getId().equals(applicationId))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "The identity document is not current for this application."));
  }

  private ApplicantIdentityNameCorrection requireCorrection(UUID correctionId) {
    return correctionRepository
        .findById(correctionId)
        .filter(correction -> !correction.isDeleted())
        .orElseThrow(() -> new IllegalArgumentException("Identity-name correction was not found."));
  }

  private boolean namesMatch(
      IdentityNameCorrectionSummary.IdentityName registered,
      IdentityNameCorrectionSummary.IdentityName document) {
    return normalized(registered.firstName()).equals(normalized(document.firstName()))
        && normalized(registered.lastName()).equals(normalized(document.lastName()));
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }

  public record ApprovalContext(
      UUID correctionId,
      UUID applicationId,
      UUID documentId,
      UUID coreUserId,
      String approvedFirstName,
      String approvedMiddleNames,
      String approvedLastName) {}
}
