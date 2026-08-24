package zw.ac.uz.emhare.admissions.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubject;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantIdentityNameCorrection;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.SubjectLevel;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionSubjectRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantIdentityNameCorrectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.DocumentOcrExtractionSnapshot;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;

/** Maps owner-authorised OCR facts into editable applicant proposals. @author Tinashe K */
@Service
public class ApplicantDocumentPrefillService {

  private static final Pattern RESULT_GRADE =
      Pattern.compile("(?:^|\\s)(A|B|C|D|E|U)(?:\\s|$)", Pattern.CASE_INSENSITIVE);

  private final ApplicationRepository applicationRepository;
  private final AdmissionSubjectRepository subjectRepository;
  private final DocumentsReportingClient documentsReportingClient;
  private final ApplicantIdentityNameCorrectionRepository identityNameCorrectionRepository;
  private final ObjectMapper objectMapper;

  public ApplicantDocumentPrefillService(
      ApplicationRepository applicationRepository,
      AdmissionSubjectRepository subjectRepository,
      DocumentsReportingClient documentsReportingClient,
      ApplicantIdentityNameCorrectionRepository identityNameCorrectionRepository,
      ObjectMapper objectMapper) {
    this.applicationRepository = applicationRepository;
    this.subjectRepository = subjectRepository;
    this.documentsReportingClient = documentsReportingClient;
    this.identityNameCorrectionRepository = identityNameCorrectionRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ApplicantDocumentPrefill prefill(
      UUID applicationId, UUID applicantUserId, UUID documentId, String qualificationLevel) {
    Application application =
        applicationRepository
            .findById(applicationId)
            .filter(
                value ->
                    !value.isDeleted() && value.getApplicant().getUserId().equals(applicantUserId))
            .orElseThrow(() -> new IllegalArgumentException("Application was not found."));
    UploadedDocumentSnapshot document = documentsReportingClient.getUploadedDocument(documentId);
    if (!"APPLICATION".equals(document.ownerType()) || !applicationId.equals(document.ownerId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "The selected document is not owned by this application.");
    }
    DocumentOcrExtractionSnapshot extraction =
        documentsReportingClient.getOcrExtraction(documentId);
    boolean manualEntryAllowed =
        "FAILED".equals(extraction.status()) || "UNSUPPORTED".equals(extraction.status());
    if (!"COMPLETED".equals(extraction.status())) {
      return new ApplicantDocumentPrefill(
          documentId,
          extraction.status(),
          manualEntryAllowed,
          Map.of(),
          List.of(),
          null,
          List.of(
              manualEntryAllowed
                  ? "OCR could not prepare this document. Enter the details manually."
                  : "OCR is still processing this document."));
    }
    Map<String, Object> facts = readMap(extraction.proposedFactsJson());
    List<String> warnings = readList(extraction.warningsJson());
    addIdentityMismatchWarnings(application, facts, warnings);
    IdentityNameCorrectionSummary identityNameMismatch =
        resolveIdentityNameMismatch(application, documentId, facts);
    List<QualificationResultProposal> results =
        qualificationLevel == null
            ? List.of()
            : resolveQualificationResults(facts, qualificationLevel);
    return new ApplicantDocumentPrefill(
        documentId,
        extraction.status(),
        true,
        facts,
        results,
        identityNameMismatch,
        List.copyOf(warnings));
  }

  private IdentityNameCorrectionSummary resolveIdentityNameMismatch(
      Application application, UUID documentId, Map<String, Object> facts) {
    var existing =
        identityNameCorrectionRepository.findByApplicationIdAndDocumentIdAndDeletedAtIsNull(
            application.getId(), documentId);
    if (existing.isPresent()) {
      IdentityNameCorrectionSummary summary = IdentityNameCorrectionSummary.from(existing.get());
      return namesMatch(summary.registeredName(), summary.documentName()) ? null : summary;
    }
    String documentFirstName = fact(facts, "firstName");
    String documentMiddleNames = fact(facts, "middleNames");
    String documentLastName = fact(facts, "lastName");
    IdentityNameCorrectionSummary.IdentityName registeredName =
        new IdentityNameCorrectionSummary.IdentityName(
            application.getApplicant().getFirstName(),
            application.getApplicant().getMiddleNames(),
            application.getApplicant().getLastName());
    IdentityNameCorrectionSummary.IdentityName documentName =
        new IdentityNameCorrectionSummary.IdentityName(
            documentFirstName, documentMiddleNames, documentLastName);
    if (documentFirstName == null
        || documentLastName == null
        || namesMatch(registeredName, documentName)) {
      return null;
    }
    var detectedMismatch =
        new ApplicantIdentityNameCorrection(
            application,
            documentId,
            documentName.firstName(),
            documentName.middleNames(),
            documentName.lastName());
    return IdentityNameCorrectionSummary.from(
        identityNameCorrectionRepository.saveAndFlush(detectedMismatch));
  }

  private boolean namesMatch(
      IdentityNameCorrectionSummary.IdentityName registeredName,
      IdentityNameCorrectionSummary.IdentityName documentName) {
    return normalizedName(registeredName.firstName())
            .equals(normalizedName(documentName.firstName()))
        && normalizedName(registeredName.lastName())
            .equals(normalizedName(documentName.lastName()));
  }

  private String fact(Map<String, Object> facts, String key) {
    Object value = facts.get(key);
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
  }

  private String normalizedName(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }

  private List<QualificationResultProposal> resolveQualificationResults(
      Map<String, Object> facts, String qualificationLevel) {
    SubjectLevel level;
    try {
      level = SubjectLevel.valueOf(qualificationLevel.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return List.of();
    }
    if (level != SubjectLevel.O_LEVEL && level != SubjectLevel.A_LEVEL) return List.of();
    List<AdmissionSubject> subjects =
        subjectRepository.findAllByLevelAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(level);
    Object linesValue = facts.get("lines");
    if (!(linesValue instanceof List<?> lines)) return List.of();
    List<QualificationResultProposal> proposals = new ArrayList<>();
    for (Object lineValue : lines) {
      String line = String.valueOf(lineValue).trim();
      String normalized = line.toUpperCase(Locale.ROOT);
      List<AdmissionSubject> matches =
          subjects.stream()
              .filter(
                  subject ->
                      normalized.equals(subject.getName().toUpperCase(Locale.ROOT))
                          || normalized.startsWith(subject.getName().toUpperCase(Locale.ROOT) + " ")
                          || normalized.matches(
                              ".*\\b"
                                  + Pattern.quote(subject.getCode().toUpperCase(Locale.ROOT))
                                  + "\\b.*"))
              .toList();
      Matcher grade = RESULT_GRADE.matcher(normalized);
      if (matches.isEmpty() || !grade.find()) continue;
      AdmissionSubject exact = matches.size() == 1 ? matches.getFirst() : null;
      proposals.add(
          new QualificationResultProposal(
              exact == null ? null : exact.getId(),
              exact == null ? line : exact.getName(),
              grade.group(1).toUpperCase(Locale.ROOT),
              matches.size() != 1,
              matches.stream().map(AdmissionSubject::getName).toList()));
      if (proposals.size() == 20) break;
    }
    return proposals;
  }

  private void addIdentityMismatchWarnings(
      Application application, Map<String, Object> facts, List<String> warnings) {
    compareIdentity("firstName", application.getApplicant().getFirstName(), facts, warnings);
    compareIdentity("lastName", application.getApplicant().getLastName(), facts, warnings);
  }

  private void compareIdentity(
      String key, String registeredValue, Map<String, Object> facts, List<String> warnings) {
    Object proposed = facts.get(key);
    if (proposed != null
        && registeredValue != null
        && !registeredValue.equalsIgnoreCase(String.valueOf(proposed).trim())) {
      warnings.add("The extracted " + key + " does not match the registered account name.");
    }
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    try {
      return new LinkedHashMap<>(
          objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
    } catch (Exception exception) {
      throw new IllegalStateException("OCR facts could not be read.", exception);
    }
  }

  private List<String> readList(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
    } catch (Exception exception) {
      return new ArrayList<>(
          List.of("OCR warnings could not be read; verify every proposed value."));
    }
  }

  public record ApplicantDocumentPrefill(
      UUID documentId,
      String extractionStatus,
      boolean manualEntryAllowed,
      Map<String, Object> personalFields,
      List<QualificationResultProposal> qualificationResults,
      IdentityNameCorrectionSummary identityNameMismatch,
      List<String> warnings) {}

  public record QualificationResultProposal(
      UUID subjectId,
      String subjectName,
      String grade,
      boolean confirmationRequired,
      List<String> candidateSubjects) {}
}
