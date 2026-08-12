package zw.ac.uz.emhare.studentrecords.conversion.api.controller;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.api.model.*;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.studentrecords.conversion.StudentSelfServiceService;
import zw.ac.uz.emhare.studentrecords.conversion.StudentWorkspaceSummary;
import zw.ac.uz.emhare.studentrecords.integration.CoreIdentityClient;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/student-records/me")
@PreAuthorize("hasAuthority('ROLE_student')")
public class StudentSelfServiceController {

    private final StudentSelfServiceService studentSelfService;
    private final CoreIdentityClient coreIdentityClient;

    public StudentSelfServiceController(
            StudentSelfServiceService studentSelfService,
            CoreIdentityClient coreIdentityClient) {
        this.studentSelfService = studentSelfService;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    public StudentWorkspaceSummary workspace(Authentication authentication) {
        return studentSelfService.workspaceForUser(actor(authentication));
    }

    private UUID actor(Authentication authentication) {
        return coreIdentityClient.syncCurrentUserId(authentication);
    }
}
