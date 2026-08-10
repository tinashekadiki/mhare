package zw.ac.uz.emhare.admissions.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves applicant-owned names from immutable registration identity claims. @author Tinashe K */
@Component
public class ApplicantRegistrationIdentityResolver {

    public ApplicantRegistrationIdentity requireIdentity(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new IllegalStateException("JWT authentication is required.");
        }
        String firstName = requiredClaim(
                jwtAuthenticationToken.getToken().getClaimAsString("given_name"), "First name");
        String lastName = requiredClaim(
                jwtAuthenticationToken.getToken().getClaimAsString("family_name"), "Last name");
        return new ApplicantRegistrationIdentity(firstName, lastName);
    }

    private String requiredClaim(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    fieldName + " is missing from the registered account. Update the account profile before applying.");
        }
        return value.trim();
    }

    public record ApplicantRegistrationIdentity(String firstName, String lastName) {
    }
}
