package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantDetails;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantProfile;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantRegisterPage;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantRegisterRow;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

@Service
public class AdmissionsApplicantService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ApplicantRepository applicantRepository;
    private final ApplicationRepository applicationRepository;
    private final AdmissionsApplicationService admissionsApplicationService;

    public AdmissionsApplicantService(
            ApplicantRepository applicantRepository,
            ApplicationRepository applicationRepository,
            AdmissionsApplicationService admissionsApplicationService) {
        this.applicantRepository = applicantRepository;
        this.applicationRepository = applicationRepository;
        this.admissionsApplicationService = admissionsApplicationService;
    }

    @Transactional
    public ApplicantRegisterPage listApplicants(
            String searchText,
            String applicantCategoryCode,
            String applicationStatus,
            int page,
            int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAXIMUM_PAGE_SIZE));
        String normalizedSearchText = searchText == null ? "" : searchText.trim();
        String normalizedCategoryCode = normalizeApplicantCategory(applicantCategoryCode);
        ApplicationStatus normalizedApplicationStatus = normalizeApplicationStatus(applicationStatus);
        var applicantPage = applicantRepository.findRegisterPage(
                normalizedSearchText,
                normalizedCategoryCode,
                normalizedApplicationStatus,
                PageRequest.of(safePage, safeSize, Sort.by(
                        Sort.Order.asc("lastName").ignoreCase(),
                        Sort.Order.asc("firstName").ignoreCase(),
                        Sort.Order.asc("applicantNumber"))));

        List<UUID> applicantIds = applicantPage.getContent().stream().map(Applicant::getId).toList();
        Map<UUID, List<Application>> applicationsByApplicantId = applicantIds.isEmpty()
                ? Collections.emptyMap()
                : applicationRepository.findAllByApplicantIdInAndDeletedAtIsNullOrderByCreatedAtDesc(applicantIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                application -> application.getApplicant().getId(),
                                java.util.LinkedHashMap::new,
                                Collectors.toList()));

        return new ApplicantRegisterPage(
                applicantPage.getContent().stream()
                        .map(applicant -> registerRow(
                                applicant,
                                applicationsByApplicantId.getOrDefault(applicant.getId(), List.of())))
                        .toList(),
                applicantPage.getNumber(),
                applicantPage.getSize(),
                applicantPage.getTotalElements(),
                applicantPage.getTotalPages());
    }

    @Transactional
    public ApplicantDetails getApplicant(UUID applicantId) {
        Applicant applicant = requireApplicant(applicantId);
        return new ApplicantDetails(
                profile(applicant),
                admissionsApplicationService.listApplicationsForApplicantRecord(applicantId));
    }

    @Transactional
    public ApplicantDetails correctApplicant(
            UUID applicantId,
            UpdateApplicantProfileCommand command) {
        Applicant applicant = requireApplicant(applicantId);
        String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
        EmhareRevisionContext.setRequestMetadata(correlationId, command.changeReason().trim());
        try {
            applicant.correctProfile(command);
            applicantRepository.saveAndFlush(applicant);
            return new ApplicantDetails(
                    profile(applicant),
                    admissionsApplicationService.listApplicationsForApplicantRecord(applicantId));
        } finally {
            EmhareRevisionContext.setRequestMetadata(correlationId, null);
        }
    }

    private Applicant requireApplicant(UUID applicantId) {
        return applicantRepository.findByIdAndDeletedAtIsNull(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("Applicant was not found."));
    }

    private ApplicantRegisterRow registerRow(Applicant applicant, List<Application> applications) {
        Application latestApplication = applications.stream().findFirst().orElse(null);
        ApplicantProfile profile = ApplicantProfileAssembler.profile(applicant);
        return new ApplicantRegisterRow(
                applicant.getId(),
                applicant.getApplicantNumber(),
                applicant.getDisplayName(),
                applicant.getApplicantCategoryCode(),
                applicant.getPrimaryEmail(),
                applicant.getPrimaryPhone(),
                profile.completenessPercentage(),
                applications.size(),
                latestApplication == null ? null : latestApplication.getApplicationNumber(),
                latestApplication == null ? null : latestApplication.getStatusCode(),
                latestApplication == null ? null : latestApplication.getAdmissionCycle().getCode(),
                applicant.getUpdatedAt(),
                applicant.getVersion());
    }

    ApplicantProfile profile(Applicant applicant) {
        return ApplicantProfileAssembler.profile(applicant);
    }

    private String normalizeApplicantCategory(String applicantCategoryCode) {
        return applicantCategoryCode == null || applicantCategoryCode.isBlank()
                ? null
                : ApplicantCategoryCode.from(applicantCategoryCode).name();
    }

    private ApplicationStatus normalizeApplicationStatus(String applicationStatus) {
        if (applicationStatus == null || applicationStatus.isBlank()) return null;
        try {
            return ApplicationStatus.valueOf(applicationStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported application status: " + applicationStatus.trim(), exception);
        }
    }

}
