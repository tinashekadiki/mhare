package zw.ac.uz.emhare.admissions.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** @author Tinashe K */
class AdmissionsCircuitBreakerSecurityContextConfigurationTest {

    private ExecutorService executorService;

    @AfterEach
    void clearSecurityContextAndExecutor() {
        SecurityContextHolder.clearContext();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void preservesApplicantJwtWhenTheCircuitBreakerUsesAnotherThread() throws Exception {
        executorService = new AdmissionsCircuitBreakerSecurityContextConfiguration()
                .admissionsCircuitBreakerExecutor();
        Jwt applicantJwt = new Jwt(
                "applicant-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "applicant-user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(applicantJwt));

        Authentication workerAuthentication = executorService.submit(
                () -> SecurityContextHolder.getContext().getAuthentication()).get();

        assertThat(workerAuthentication).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(((JwtAuthenticationToken) workerAuthentication).getToken().getTokenValue())
                .isEqualTo("applicant-token");
    }
}
