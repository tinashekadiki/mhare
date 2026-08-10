package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitation;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitationService;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitationViews.PublicReferenceRequest;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitationViews.SubmitReferenceCommand;

/** Public token-protected referee reference endpoints. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions/referee-references")
public class ApplicantRefereeReferenceController {

    private final ApplicantRefereeInvitationService invitationService;

    public ApplicantRefereeReferenceController(ApplicantRefereeInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/{token}")
    public PublicReferenceRequest referenceRequest(@PathVariable("token") String token) {
        return invitationService.openReferenceRequest(token);
    }

    @PostMapping("/{token}")
    public PublicReferenceRequest submitReference(
            @PathVariable("token") String token,
            @Valid @RequestBody SubmitReferenceRequest request) {
        return invitationService.submitReference(token, new SubmitReferenceCommand(
                request.relationshipToApplicant(), request.yearsKnown(), request.recommendation(),
                request.comments(), request.declarationAccepted()));
    }

    public record SubmitReferenceRequest(
            @NotBlank @Size(max = 200) String relationshipToApplicant,
            @Min(0) @Max(100) int yearsKnown,
            @NotNull ApplicantRefereeInvitation.Recommendation recommendation,
            @NotBlank @Size(max = 5000) String comments,
            @AssertTrue boolean declarationAccepted) {
    }
}
