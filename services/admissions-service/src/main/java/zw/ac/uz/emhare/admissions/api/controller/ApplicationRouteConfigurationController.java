package zw.ac.uz.emhare.admissions.api.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest;
import zw.ac.uz.emhare.admissions.application.ApplicationRouteConfigurationService;
import zw.ac.uz.emhare.admissions.application.ApplicationRouteConfigurationSummary;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/admissions/application-types/{applicationTypeId}/route-configuration")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
public class ApplicationRouteConfigurationController {
    private final ApplicationRouteConfigurationService service;
    private final CoreIdentityClient coreIdentityClient;

    public ApplicationRouteConfigurationController(
            ApplicationRouteConfigurationService service, CoreIdentityClient coreIdentityClient) {
        this.service = service;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    public ApplicationRouteConfigurationSummary configuration(
            @PathVariable("applicationTypeId") UUID applicationTypeId) {
        return service.configuration(applicationTypeId);
    }

    @PutMapping
    public ApplicationRouteConfigurationSummary configure(
            Authentication authentication,
            @PathVariable("applicationTypeId") UUID applicationTypeId,
            @Valid @RequestBody ConfigureApplicationRouteRequest request) {
        return service.configure(applicationTypeId, coreIdentityClient.syncCurrentUser(authentication).user().id(), request);
    }
}
