package zw.ac.uz.emhare.admissions.security;

import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

@Component("admissionsRbac")
public class AdmissionsRbacGuard {

    private static final Map<String, String> PERMISSION_ROLE_MAP = Map.ofEntries(
            Map.entry("ADMISSIONS_APPLICATION_CONFIRM", "admissions-officer"),
            Map.entry("ADMISSIONS_ELIGIBILITY_REVIEW", "admissions-officer"),
            Map.entry("ADMISSIONS_DECISION_MAKE", "admissions-officer"),
            Map.entry("ADMISSIONS_ACADEMIC_REVIEW_RELEASE", "admissions-officer"),
            Map.entry("ADMISSIONS_ACADEMIC_UNIT_RECOMMEND", "academic-unit-staff"),
            Map.entry("ADMISSIONS_SELECTION_APPROVE", "admissions-officer"),
            Map.entry("ADMISSIONS_OFFER_MANAGE", "admissions-officer"),
            Map.entry("ADMISSIONS_OFFER_APPROVE", "admissions-officer"),
            Map.entry("ADMISSIONS_OFFER_DISPATCH", "admissions-officer"),
            Map.entry("ADMISSIONS_APPLICATION_APPLY", "applicant"),
            Map.entry("ADMISSIONS_APPLICATION_REVIEW", "admissions-officer"),
            Map.entry("ADMISSIONS_SETUP_MANAGE", "admissions-officer"),
            Map.entry("ADMISSIONS_PAYMENT_OVERRIDE", "finance-officer"));

    private final EmhareCurrentUserResolver currentUserResolver;

    public AdmissionsRbacGuard(EmhareCurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    public boolean has(Authentication authentication, String permissionCode) {
        Optional<EmhareCurrentUser> currentUser = currentUserResolver.fromAuthentication(authentication);
        if (currentUser.isEmpty()) {
            return false;
        }
        if (currentUser.get().hasRealmRole("system-admin")) {
            return true;
        }
        if ("ADMISSIONS_ACADEMIC_UNIT_RECOMMEND".equals(permissionCode)) {
            // The application service enforces the authoritative active Core role assignment
            // at the exact snapshotted highest academic unit.
            return true;
        }
        String role = PERMISSION_ROLE_MAP.get(permissionCode);
        if (role == null) {
            return false;
        }
        if ("applicant".equals(role)) {
            return true;
        }
        return currentUser.get().hasRealmRole(role);
    }
}
