package zw.ac.uz.emhare.admissions.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;

/** Produces the persisted duplicate-check evidence required before Admissions clearance. @author Tinashe K */
@Service
public class ApplicationDuplicateCheckService {

    private static final String PASSED_SUMMARY =
            "Applicant identity, intake application, programme choice rank and programme choice uniqueness checks passed.";

    private final ApplicantRepository applicantRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository choiceRepository;

    public ApplicationDuplicateCheckService(
            ApplicantRepository applicantRepository,
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository choiceRepository) {
        this.applicantRepository = applicantRepository;
        this.applicationRepository = applicationRepository;
        this.choiceRepository = choiceRepository;
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResult check(Application application) {
        Applicant applicant = application.getApplicant();
        List<String> failures = new ArrayList<>();

        checkIdentityOwnership(applicant, failures);
        checkIntakeApplicationUniqueness(application, applicant, failures);
        checkProgrammeChoiceUniqueness(application.getId(), failures);

        return failures.isEmpty()
                ? new DuplicateCheckResult(true, PASSED_SUMMARY)
                : new DuplicateCheckResult(false, String.join(" ", failures));
    }

    private void checkIdentityOwnership(Applicant applicant, List<String> failures) {
        if (hasText(applicant.getNationalIdNumber())) {
            applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull(applicant.getNationalIdNumber().trim())
                    .filter(foundApplicant -> !foundApplicant.getId().equals(applicant.getId()))
                    .ifPresent(ignored -> failures.add("National ID is linked to another applicant account."));
        }
        if (hasText(applicant.getPassportNumber())) {
            applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull(applicant.getPassportNumber().trim())
                    .filter(foundApplicant -> !foundApplicant.getId().equals(applicant.getId()))
                    .ifPresent(ignored -> failures.add("Passport number is linked to another applicant account."));
        }
    }

    private void checkIntakeApplicationUniqueness(
            Application application,
            Applicant applicant,
            List<String> failures) {
        if (!hasText(applicant.getNationalIdNumber())) {
            return;
        }
        boolean anotherApplicationExists = applicationRepository
                .findByIntakeIdAndApplicantNationalIdNumber(
                        application.getIntakeId(),
                        applicant.getNationalIdNumber().trim())
                .stream()
                .anyMatch(foundApplication -> !foundApplication.getId().equals(application.getId()));
        if (anotherApplicationExists) {
            failures.add("Another application already exists for this applicant identity in the intake.");
        }
    }

    private void checkProgrammeChoiceUniqueness(UUID applicationId, List<String> failures) {
        List<ApplicationProgrammeChoice> choices = choiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(applicationId);
        Set<Integer> uniqueRanks = new HashSet<>();
        Set<UUID> uniqueProgrammeIds = new HashSet<>();
        if (choices.stream().anyMatch(choice -> !uniqueRanks.add(choice.getChoiceRank()))) {
            failures.add("Programme choice ranks contain duplicates.");
        }
        if (choices.stream().anyMatch(choice -> !uniqueProgrammeIds.add(choice.getProgrammeId()))) {
            failures.add("The same programme appears more than once in the application.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record DuplicateCheckResult(boolean passed, String summary) {
    }
}
