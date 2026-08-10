package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicantService;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantDetails;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantRegisterPage;
import zw.ac.uz.emhare.admissions.application.UpdateApplicantProfileCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** Staff applicant profile register. Applicant creation remains an authenticated self-service action. @author Tinashe K */
@Validated
@RestController
@RequestMapping("/api/admissions/applicants")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
public class AdmissionsApplicantController {

    private final AdmissionsApplicantService admissionsApplicantService;
    private final CoreIdentityClient coreIdentityClient;

    public AdmissionsApplicantController(
            AdmissionsApplicantService admissionsApplicantService,
            CoreIdentityClient coreIdentityClient) {
        this.admissionsApplicantService = admissionsApplicantService;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    public ApplicantRegisterPage listApplicants(
            @RequestParam(name = "search", defaultValue = "") @Size(max = 200) String search,
            @RequestParam(name = "category", required = false) @Size(max = 30) String category,
            @RequestParam(name = "applicationStatus", required = false) @Size(max = 30) String applicationStatus,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size) {
        return admissionsApplicantService.listApplicants(search, category, applicationStatus, page, size);
    }

    @GetMapping("/{applicantId}")
    public ApplicantDetails getApplicant(@PathVariable("applicantId") UUID applicantId) {
        return admissionsApplicantService.getApplicant(applicantId);
    }

    @PutMapping("/{applicantId}")
    public ApplicantDetails correctApplicant(
            Authentication authentication,
            @PathVariable("applicantId") UUID applicantId,
            @Valid @RequestBody UpdateApplicantProfileRequest request) {
        UUID actorUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
        return admissionsApplicantService.correctApplicant(applicantId, new UpdateApplicantProfileCommand(
                request.applicantCategoryCode(),
                request.titleCode(),
                request.firstName(),
                request.middleNames(),
                request.lastName(),
                request.dateOfBirth(),
                request.genderCode(),
                request.maritalStatusCode(),
                request.nationalIdNumber(),
                request.passportNumber(),
                request.countryId(),
                request.nationalityCountryId(),
                request.placeOfBirth(),
                request.disabilityStatusCode(),
                request.specialNeeds(),
                request.sponsorTypeCode(),
                request.primaryEmail(),
                request.primaryPhone(),
                request.postalAddress(),
                request.residentialAddress(),
                request.changeReason(),
                request.expectedVersion()));
    }
}
