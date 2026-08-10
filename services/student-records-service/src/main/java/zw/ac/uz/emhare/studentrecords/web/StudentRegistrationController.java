package zw.ac.uz.emhare.studentrecords.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.CreateRegistration;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.CreateOwnRegistration;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.RegistrationDecision;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.SubmitOwnRegistration;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationSummary;
import zw.ac.uz.emhare.studentrecords.registration.StudentRegistrationService;
import zw.ac.uz.emhare.studentrecords.integration.CoreIdentityClient;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/student-records/registrations")
public class StudentRegistrationController {

    private static final String REGISTRY_OR_ADMIN =
            "hasAnyAuthority('ROLE_system-admin', 'ROLE_registry-officer')";
    private static final String ACADEMIC_OR_ADMIN =
            "hasAnyAuthority('ROLE_system-admin', 'ROLE_academic-admin')";
    private static final String STUDENT = "hasAuthority('ROLE_student')";

    private final StudentRegistrationService registrationService;
    private final EmhareCurrentUserResolver currentUserResolver;
    private final CoreIdentityClient coreIdentityClient;

    public StudentRegistrationController(
            StudentRegistrationService registrationService,
            EmhareCurrentUserResolver currentUserResolver,
            CoreIdentityClient coreIdentityClient) {
        this.registrationService = registrationService;
        this.currentUserResolver = currentUserResolver;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    @PreAuthorize(REGISTRY_OR_ADMIN + " or " + ACADEMIC_OR_ADMIN)
    public List<RegistrationSummary> list() {
        return registrationService.list();
    }

    @GetMapping("/mine")
    @PreAuthorize(STUDENT)
    public List<RegistrationSummary> listMine(Authentication authentication) {
        return registrationService.listForUser(studentActor(authentication));
    }

    @PostMapping("/mine")
    @PreAuthorize(STUDENT)
    public ResponseEntity<RegistrationSummary> createMine(
            Authentication authentication,
            @Valid @RequestBody CreateOwnRegistration request) {
        RegistrationSummary created = registrationService.createForUser(request, studentActor(authentication));
        return ResponseEntity.created(URI.create("/api/student-records/registrations/" + created.id()))
                .body(created);
    }

    @PostMapping("/mine/{registrationId}/submit")
    @PreAuthorize(STUDENT)
    public RegistrationSummary submitMine(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @Valid @RequestBody SubmitOwnRegistration request) {
        return registrationService.submitForUser(
                registrationId, request.expectedVersion(), studentActor(authentication));
    }

    @PostMapping
    @PreAuthorize(REGISTRY_OR_ADMIN)
    public ResponseEntity<RegistrationSummary> create(
            Authentication authentication,
            @Valid @RequestBody CreateRegistration request) {
        RegistrationSummary created = registrationService.create(request, actor(authentication));
        return ResponseEntity.created(URI.create("/api/student-records/registrations/" + created.id()))
                .body(created);
    }

    @PostMapping("/{registrationId}/submit")
    @PreAuthorize(REGISTRY_OR_ADMIN)
    public RegistrationSummary submit(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @Valid @RequestBody RegistrationDecision request) {
        return registrationService.submit(
                registrationId, request.expectedVersion(), request.reason(), actor(authentication));
    }

    @PostMapping("/{registrationId}/academic-approve")
    @PreAuthorize(ACADEMIC_OR_ADMIN)
    public RegistrationSummary approveAcademically(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @Valid @RequestBody RegistrationDecision request) {
        return registrationService.approveAcademically(
                registrationId, request.expectedVersion(), request.reason(), actor(authentication));
    }

    @PostMapping("/{registrationId}/confirm")
    @PreAuthorize(REGISTRY_OR_ADMIN)
    public RegistrationSummary confirm(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @Valid @RequestBody RegistrationDecision request) {
        return registrationService.confirm(
                registrationId, request.expectedVersion(), request.reason(), actor(authentication));
    }

    @PostMapping("/{registrationId}/reject")
    @PreAuthorize(REGISTRY_OR_ADMIN + " or " + ACADEMIC_OR_ADMIN)
    public RegistrationSummary reject(
            Authentication authentication,
            @PathVariable UUID registrationId,
            @Valid @RequestBody RegistrationDecision request) {
        return registrationService.reject(
                registrationId, request.expectedVersion(), request.reason(), actor(authentication));
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }

    private UUID studentActor(Authentication authentication) {
        return coreIdentityClient.syncCurrentUserId(authentication);
    }
}
