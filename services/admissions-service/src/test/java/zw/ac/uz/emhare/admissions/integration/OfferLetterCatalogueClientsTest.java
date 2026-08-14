package zw.ac.uz.emhare.admissions.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.AcademicUnitPathItem;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveAcademicFeeStructureRequest;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeLine;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeStructure;
import zw.ac.uz.emhare.admissions.integration.http.CoreIdentityHttpService;
import zw.ac.uz.emhare.admissions.integration.http.FinanceHttpService;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
class OfferLetterCatalogueClientsTest {
    @Test
    void returnsTheCoreOwnedInstitutionProfile() {
        CoreIdentityHttpService http = mock(CoreIdentityHttpService.class);
        CoreIdentityClient.CoreInstitutionProfile expected = profile();
        when(http.institutionProfile("Bearer token")).thenReturn(expected);

        assertSame(expected, new CoreIdentityClient(http).institutionProfile("Bearer token"));
    }

    @Test
    void translatesEmptyAndFailedCoreProfileCalls() {
        CoreIdentityHttpService http = mock(CoreIdentityHttpService.class);
        CoreIdentityClient client = new CoreIdentityClient(http);
        when(http.institutionProfile("empty")).thenReturn(null);
        when(http.institutionProfile("response-error")).thenThrow(mock(RestClientResponseException.class));
        when(http.institutionProfile("runtime-error")).thenThrow(new IllegalStateException("offline"));

        assertThrows(ServiceDependencyUnavailableException.class, () -> client.institutionProfile("empty"));
        assertThrows(ServiceDependencyUnavailableException.class, () -> client.institutionProfile("response-error"));
        assertThrows(ServiceDependencyUnavailableException.class, () -> client.institutionProfile("runtime-error"));
    }

    @Test
    void returnsCompleteFinanceOwnedAcademicSchedule() {
        FinanceHttpService http = mock(FinanceHttpService.class);
        ResolveAcademicFeeStructureRequest request = request();
        ResolvedAcademicFeeStructure expected = schedule();
        when(http.resolveAcademicFeeStructure("Bearer token", request)).thenReturn(expected);

        assertSame(expected, new FinanceCatalogueClient(http).resolveAcademicFeeStructure("Bearer token", request));
        assertEquals("TUIT", expected.lines().getFirst().feeCode());
    }

    @Test
    void rejectsIncompleteOrFinanceRejectedAcademicSchedule() {
        FinanceHttpService http = mock(FinanceHttpService.class);
        FinanceCatalogueClient client = new FinanceCatalogueClient(http);
        ResolveAcademicFeeStructureRequest request = request();
        when(http.resolveAcademicFeeStructure("empty", request)).thenReturn(null);
        when(http.resolveAcademicFeeStructure("no-lines", request)).thenReturn(
                new ResolvedAcademicFeeStructure(UUID.randomUUID(), "UG", "UG", "ACADEMIC", "ACTIVE", 1,
                        "USD", List.of()));
        RestClientResponseException badRequest = mock(RestClientResponseException.class);
        when(badRequest.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(http.resolveAcademicFeeStructure("bad", request)).thenThrow(badRequest);
        RestClientResponseException conflict = mock(RestClientResponseException.class);
        when(conflict.getStatusCode()).thenReturn(HttpStatus.CONFLICT);
        when(http.resolveAcademicFeeStructure("conflict", request)).thenThrow(conflict);
        RestClientResponseException server = mock(RestClientResponseException.class);
        when(server.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
        when(http.resolveAcademicFeeStructure("server", request)).thenThrow(server);
        when(http.resolveAcademicFeeStructure("transport", request)).thenThrow(new RestClientException("offline"));

        assertThrows(ServiceDependencyUnavailableException.class,
                () -> client.resolveAcademicFeeStructure("empty", request));
        assertThrows(ServiceDependencyUnavailableException.class,
                () -> client.resolveAcademicFeeStructure("no-lines", request));
        assertThrows(IllegalStateException.class, () -> client.resolveAcademicFeeStructure("bad", request));
        assertThrows(IllegalStateException.class, () -> client.resolveAcademicFeeStructure("conflict", request));
        assertThrows(ServiceDependencyUnavailableException.class,
                () -> client.resolveAcademicFeeStructure("server", request));
        assertThrows(ServiceDependencyUnavailableException.class,
                () -> client.resolveAcademicFeeStructure("transport", request));
    }

    private CoreIdentityClient.CoreInstitutionProfile profile() {
        return new CoreIdentityClient.CoreInstitutionProfile(UUID.randomUUID(), "UZ", "University of Zimbabwe",
                "University of Zimbabwe", "USD", "ZW", "Africa/Harare", "{}", "{}", "UZ");
    }

    private ResolveAcademicFeeStructureRequest request() {
        return new ResolveAcademicFeeStructureRequest("ACADEMIC", Instant.parse("2028-03-04T00:00:00Z"), null,
                UUID.randomUUID(), List.of(new AcademicUnitPathItem(UUID.randomUUID(), "SCI", "Science")),
                UUID.randomUUID(), "UNDERGRADUATE", UUID.randomUUID(), "LOCAL", 1);
    }

    private ResolvedAcademicFeeStructure schedule() {
        ResolvedAcademicFeeLine line = new ResolvedAcademicFeeLine(UUID.randomUUID(), 1, UUID.randomUUID(),
                "TUIT", "Tuition", "Tuition", new BigDecimal("1200.00"), "USD", "USD", null, null,
                new BigDecimal("1200.00"), "RATED", "APPROVED");
        return new ResolvedAcademicFeeStructure(UUID.randomUUID(), "UG", "UG", "ACADEMIC", "ACTIVE", 2,
                "USD", List.of(line));
    }
}
