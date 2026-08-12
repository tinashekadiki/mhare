package zw.ac.uz.emhare.studentrecords.conversion.api.controller;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.api.model.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.studentrecords.conversion.StudentConversionService;
import zw.ac.uz.emhare.studentrecords.conversion.StudentConversionSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/student-records/conversions")
public class StudentConversionController {
    private final StudentConversionService conversionService;
    private final EmhareCurrentUserResolver currentUserResolver;

    public StudentConversionController(
            StudentConversionService conversionService,
            EmhareCurrentUserResolver currentUserResolver) {
        this.conversionService = conversionService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin', 'ROLE_registry-officer')")
    public List<StudentConversionSummary> listConversions() {
        return conversionService.listConversions();
    }

    @PostMapping("/{conversionRequestId}/retry")
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin', 'ROLE_registry-officer')")
    public StudentConversionSummary retryConversion(
            Authentication authentication,
            @PathVariable UUID conversionRequestId,
            @Valid @RequestBody RetryStudentConversionRequest request) {
        UUID actorUserId = currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
        return conversionService.retryProvisioning(
                conversionRequestId, request.reason(), actorUserId);
    }
}
