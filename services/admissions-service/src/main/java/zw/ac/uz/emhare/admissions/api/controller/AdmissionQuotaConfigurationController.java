package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.api.model.ConfigureAdmissionQuotasRequest;
import zw.ac.uz.emhare.admissions.application.AdmissionQuotaConfigurationService;
import zw.ac.uz.emhare.admissions.application.AdmissionQuotaSummary;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/admissions/intakes/{intakeId}/programme-quotas")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
public class AdmissionQuotaConfigurationController {
    private final AdmissionQuotaConfigurationService service;
    private final CoreIdentityClient coreIdentityClient;

    public AdmissionQuotaConfigurationController(
            AdmissionQuotaConfigurationService service,
            CoreIdentityClient coreIdentityClient) {
        this.service = service;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    public List<AdmissionQuotaSummary> current(@PathVariable("intakeId") UUID intakeId) {
        return service.current(intakeId);
    }

    @PutMapping
    public List<AdmissionQuotaSummary> configure(
            Authentication authentication,
            @PathVariable("intakeId") UUID intakeId,
            @Valid @RequestBody ConfigureAdmissionQuotasRequest request) {
        UUID actorUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
        return service.configure(intakeId, actorUserId, request);
    }
}
