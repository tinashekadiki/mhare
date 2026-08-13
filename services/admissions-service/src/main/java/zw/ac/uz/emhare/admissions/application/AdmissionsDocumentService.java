package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocument;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRequirementSnapshotRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocumentRequirementSnapshot;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.AcademicUnitApplicationDocumentEntry;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.ApplicationDocumentRegister;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.ApplicationDocumentRequirementState;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.DocumentRequirementSummary;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;

/** @author Tinashe K */
@Service
public class AdmissionsDocumentService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationTypeRepository applicationTypeRepository;
    private final ApplicationTypeDocumentRequirementRepository requirementRepository;
    private final ApplicationDocumentRequirementSnapshotRepository requirementSnapshotRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    private final DocumentsReportingClient documentsReportingClient;
    private final AdmissionsIntegrationOutboxService integrationOutboxService;
    private final Clock clock;

    public AdmissionsDocumentService(
            ApplicationRepository applicationRepository,
            ApplicationTypeRepository applicationTypeRepository,
            ApplicationTypeDocumentRequirementRepository requirementRepository,
            ApplicationDocumentRequirementSnapshotRepository requirementSnapshotRepository,
            ApplicationDocumentRepository documentRepository,
            ApplicationProgrammeChoiceRepository programmeChoiceRepository,
            DocumentsReportingClient documentsReportingClient,
            AdmissionsIntegrationOutboxService integrationOutboxService,
            Clock clock) {
        this.applicationRepository = applicationRepository;
        this.applicationTypeRepository = applicationTypeRepository;
        this.requirementRepository = requirementRepository;
        this.requirementSnapshotRepository = requirementSnapshotRepository;
        this.documentRepository = documentRepository;
        this.programmeChoiceRepository = programmeChoiceRepository;
        this.documentsReportingClient = documentsReportingClient;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public DocumentRequirementSummary createRequirement(
            UUID applicationTypeId,
            String requirementCode,
            String requirementName,
            boolean required,
            int sortOrder) {
        ApplicationType applicationType = applicationTypeRepository.findById(applicationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Application type was not found."));
        String normalizedCode = normalizeCode(requirementCode);
        if (requirementRepository
                .findByApplicationTypeIdAndRequirementCodeAndActiveTrueAndDeletedAtIsNull(
                        applicationTypeId, normalizedCode)
                .isPresent()) {
            throw new IllegalStateException("This application type already has the document requirement.");
        }
        return requirementSummary(requirementRepository.saveAndFlush(new ApplicationTypeDocumentRequirement(
                applicationType, normalizedCode, requirementName, required, sortOrder)));
    }

    @Transactional
    public List<DocumentRequirementSummary> requirements(UUID applicationTypeId) {
        return requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        applicationTypeId)
                .stream().map(this::requirementSummary).toList();
    }

    @Transactional
    public void snapshotRequirements(Application application) {
        if (!requirementSnapshotRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(application.getId())
                .isEmpty()) {
            return;
        }
        List<ApplicationDocumentRequirementSnapshot> snapshots = requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        application.getApplicationType().getId())
                .stream().map(requirement -> new ApplicationDocumentRequirementSnapshot(application, requirement)).toList();
        if (!snapshots.isEmpty()) requirementSnapshotRepository.saveAllAndFlush(snapshots);
    }

    @Transactional
    public ApplicationDocumentRegister linkApplicantDocument(
            UUID applicationId,
            UUID applicantUserId,
            UUID documentId,
            String requirementCode) {
        Application application = requireApplicantOwnedApplication(applicationId, applicantUserId);
        assertDocumentIntakeOpen(application);
        String normalizedCode = normalizeCode(requirementCode);
        DocumentRule requirement = documentRule(application, normalizedCode);
        UploadedDocumentSnapshot document = documentsReportingClient.getUploadedDocument(documentId);
        validateUploadedDocument(application, requirement, document);

        ApplicationDocument current = documentRepository
                .findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(applicationId, normalizedCode)
                .orElse(null);
        if (current == null && document.replacesDocumentId() != null) {
            throw new IllegalArgumentException("Replacement document has no current rejected application document.");
        }
        if (current != null) {
            if (!current.getDocumentId().equals(document.replacesDocumentId())) {
                throw new IllegalArgumentException("Replacement must identify the current rejected document.");
            }
            current.supersede();
            documentRepository.save(current);
        }

        ApplicationDocument linked = new ApplicationDocument(
                application,
                document.id(),
                requirement.code(),
                requirement.required(),
                document.originalFileName(),
                document.mimeType(),
                document.checksumSha256(),
                clock.instant(),
                current == null ? null : current.getId());
        documentRepository.saveAndFlush(linked);
        return register(application);
    }

    @Transactional
    public ApplicationDocumentRegister applicantRegister(UUID applicationId, UUID applicantUserId) {
        return register(requireApplicantOwnedApplication(applicationId, applicantUserId));
    }

    @Transactional
    public ApplicationDocumentRegister staffRegister(UUID applicationId) {
        return register(requireApplication(applicationId));
    }

    public List<AcademicUnitApplicationDocumentEntry> academicUnitRegister(UUID owningAcademicUnitId) {
        List<UUID> applicationIds = programmeChoiceRepository.findAllByOwningAcademicUnitId(owningAcademicUnitId)
                .stream()
                .map(choice -> choice.getApplication().getId())
                .distinct()
                .toList();
        List<Application> applications = applicationRepository.findAllById(applicationIds);
        return applications.stream()
                .filter(application -> !application.isDeleted())
                .map(application -> new AcademicUnitApplicationDocumentEntry(
                        application.getId(),
                        application.getApplicationNumber(),
                        application.getApplicant().getFirstName() + " " + application.getApplicant().getLastName(),
                        application.getStatusCode(),
                        register(application)))
                .toList();
    }

    @Transactional
    public void assertReadyForSubmission(Application application) {
        ApplicationDocumentRegister register = register(application);
        if (!register.requiredDocumentsUploaded()) {
            throw new IllegalStateException("All required documents must be uploaded before final submission.");
        }
    }

    @Transactional
    public void assertReadyForReview(Application application) {
        if (!isReadyForReview(application)) {
            throw new IllegalStateException("All required documents must be verified before application review begins.");
        }
    }

    public boolean isReadyForReview(Application application) {
        return register(application).requiredDocumentsVerified();
    }

    @Transactional
    public void applyVerification(DocumentVerificationChangedEvent event) {
        ApplicationDocument document = documentRepository.findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(event.documentId())
                .orElse(null);
        if (document == null) return;
        if (!document.applyVerification(event)) return;
        documentRepository.saveAndFlush(document);
        if (document.getStatus() == ApplicationDocument.VerificationStatus.REJECTED) {
            integrationOutboxService.enqueueMissingDocumentsNotification(
                    document.getApplication(),
                    document.getRequirementCode(),
                    document.getRejectionReason(),
                    document.getDocumentId(),
                    event.documentVersion(),
                    event.verifiedByUserId());
        }
    }

    private void validateUploadedDocument(
            Application application,
            DocumentRule requirement,
            UploadedDocumentSnapshot document) {
        if (!"APPLICATION".equals(document.ownerType()) || !application.getId().equals(document.ownerId())) {
            throw new IllegalArgumentException("Uploaded document must be owned by this application.");
        }
        if (!requirement.code().equals(document.documentTypeCode())) {
            throw new IllegalArgumentException("Uploaded document type does not match the selected requirement.");
        }
        if (!"PENDING".equals(document.verificationStatus())) {
            throw new IllegalStateException("Only a newly uploaded pending document can be linked.");
        }
    }

    private ApplicationDocumentRegister register(Application application) {
        List<DocumentRule> requirements = documentRules(application);
        Map<String, ApplicationDocument> currentDocuments = new LinkedHashMap<>();
        documentRepository.findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(
                        application.getId())
                .forEach(document -> currentDocuments.put(document.getRequirementCode(), document));
        List<String> missing = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<ApplicationDocumentRequirementState> states = new ArrayList<>();
        for (DocumentRule requirement : requirements) {
            ApplicationDocument document = currentDocuments.get(requirement.code());
            String state = document == null ? "MISSING" : document.getStatus().name();
            if (requirement.required()) {
                if (document == null) missing.add(requirement.code());
                else if (document.getStatus() == ApplicationDocument.VerificationStatus.PENDING) {
                    pending.add(requirement.code());
                } else if (document.getStatus() == ApplicationDocument.VerificationStatus.REJECTED) {
                    rejected.add(requirement.code());
                }
            }
            states.add(new ApplicationDocumentRequirementState(
                    requirement.code(), requirement.name(), requirement.required(), state,
                    document == null ? null : document.getId(), document == null ? null : document.getDocumentId(),
                    document == null ? null : document.getDocumentFileName(),
                    document == null ? null : document.getDocumentMimeType(),
                    document == null ? null : document.getDocumentChecksumSha256(),
                    document == null ? null : document.getLinkedAt(),
                    document == null ? null : document.getVerifiedByUserId(),
                    document == null ? null : document.getVerifiedAt(),
                    document == null ? null : document.getRejectionReason(),
                    document == null ? 0 : document.getLastDocumentVersion(),
                    document == null ? 0 : document.getVersion()));
        }
        boolean hasRequirements = !requirements.isEmpty();
        return new ApplicationDocumentRegister(
                application.getId(), application.getApplicationNumber(),
                hasRequirements && missing.isEmpty() && rejected.isEmpty(),
                hasRequirements && missing.isEmpty() && pending.isEmpty() && rejected.isEmpty(),
                List.copyOf(missing), List.copyOf(pending), List.copyOf(rejected), List.copyOf(states));
    }

    private DocumentRule documentRule(Application application, String requirementCode) {
        return documentRules(application).stream()
                .filter(requirement -> requirement.code().equals(requirementCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document requirement is not part of this application's snapshot."));
    }

    private List<DocumentRule> documentRules(Application application) {
        List<ApplicationDocumentRequirementSnapshot> snapshots = requirementSnapshotRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(application.getId());
        if (!snapshots.isEmpty()) {
            return snapshots.stream().map(snapshot -> new DocumentRule(
                    snapshot.getRequirementCode(), snapshot.getRequirementName(), snapshot.isRequired())).toList();
        }
        return requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        application.getApplicationType().getId())
                .stream().map(requirement -> new DocumentRule(
                        requirement.getRequirementCode(), requirement.getRequirementName(), requirement.isRequired())).toList();
    }

    private record DocumentRule(String code, String name, boolean required) { }

    private Application requireApplicantOwnedApplication(UUID applicationId, UUID applicantUserId) {
        Application application = requireApplication(applicationId);
        if (!application.getApplicant().getUserId().equals(applicantUserId)) {
            throw new IllegalArgumentException("Application was not found.");
        }
        return application;
    }

    private void assertDocumentIntakeOpen(Application application) {
        if (application.getStatus() != ApplicationStatus.DRAFT
                && application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Documents can only be uploaded while the application is in draft or awaiting review.");
        }
        if (java.time.LocalDate.now(clock).isAfter(application.getIntakeEndsOn())) {
            throw new IllegalStateException(
                    "The application document deadline has passed. Contact Admissions for an authorised exception.");
        }
    }

    private Application requireApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application was not found."));
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Requirement code is required.");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private DocumentRequirementSummary requirementSummary(ApplicationTypeDocumentRequirement requirement) {
        return new DocumentRequirementSummary(
                requirement.getId(), requirement.getApplicationType().getId(), requirement.getRequirementCode(),
                requirement.getRequirementName(), requirement.isRequired(), requirement.getSortOrder(),
                requirement.isActive(), requirement.getVersion());
    }
}
