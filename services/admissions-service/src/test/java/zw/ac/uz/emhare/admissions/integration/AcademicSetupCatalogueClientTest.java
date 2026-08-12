package zw.ac.uz.emhare.admissions.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import zw.ac.uz.emhare.admissions.integration.http.AcademicSetupHttpService;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
class AcademicSetupCatalogueClientTest {

    private static final UUID ACADEMIC_YEAR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID INTAKE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private MockRestServiceServer server;
    private AcademicSetupCatalogueClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder
                .baseUrl("http://academic-setup.test")
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth("forwarded-jwt");
                    return execution.execute(request, body);
                })
                .build();
        AcademicSetupHttpService httpService = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(AcademicSetupHttpService.class);
        client = new AcademicSetupCatalogueClient(httpService);

        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "forwarded-jwt",
                now,
                now.plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.verify();
    }

    @Test
    void getAdmissionsCatalogue_shouldPreserveAcademicBusinessConflict() {
        expectCatalogueRequest(HttpStatus.CONFLICT, "The intake must be open before applications can start.");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.getAdmissionsCatalogue(ACADEMIC_YEAR_ID, INTAKE_ID));

        assertEquals("The intake must be open before applications can start.", exception.getMessage());
    }

    @Test
    void getAdmissionsCatalogue_shouldPreserveInvalidAcademicSelection() {
        expectCatalogueRequest(HttpStatus.BAD_REQUEST, "The selected intake does not belong to the academic year.");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.getAdmissionsCatalogue(ACADEMIC_YEAR_ID, INTAKE_ID));

        assertEquals("The selected intake does not belong to the academic year.", exception.getMessage());
    }

    @Test
    void getAdmissionsCatalogue_shouldFailClosedForDependencyFailure() {
        expectCatalogueRequest(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected dependency failure.");

        assertThrows(
                ServiceDependencyUnavailableException.class,
                () -> client.getAdmissionsCatalogue(ACADEMIC_YEAR_ID, INTAKE_ID));
    }

    @Test
    void getProgrammeHierarchyForwardsAuthenticationAndPreservesTheRootPathContract() {
        UUID programmeId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID rootUnitId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        UUID leafUnitId = UUID.fromString("50000000-0000-0000-0000-000000000005");
        String root = "{\"id\":\"" + rootUnitId + "\",\"academicUnitTypeId\":\"" + UUID.randomUUID()
                + "\",\"academicUnitTypeCode\":\"COLLEGE\",\"parentId\":null,\"code\":\"SCI\"," 
                + "\"name\":\"College of Science\",\"status\":\"ACTIVE\",\"version\":0}";
        String leaf = "{\"id\":\"" + leafUnitId + "\",\"academicUnitTypeId\":\"" + UUID.randomUUID()
                + "\",\"academicUnitTypeCode\":\"SCHOOL\",\"parentId\":\"" + rootUnitId
                + "\",\"code\":\"COMP\",\"name\":\"School of Computing\",\"status\":\"ACTIVE\",\"version\":0}";
        server.expect(requestTo("http://academic-setup.test/api/academic/programmes/" + programmeId + "/hierarchy"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer forwarded-jwt"))
                .andRespond(withSuccess("{\"programmeId\":\"" + programmeId
                        + "\",\"programmeCode\":\"BSC-CS\",\"programmeName\":\"Computer Science\"," 
                        + "\"owningAcademicUnit\":" + leaf + ",\"highestAcademicUnit\":" + root
                        + ",\"ancestorPath\":[" + root + "," + leaf + "]}", MediaType.APPLICATION_JSON));

        var hierarchy = client.getProgrammeHierarchy(programmeId);

        assertEquals(rootUnitId, hierarchy.highestAcademicUnit().id());
        assertEquals(leafUnitId, hierarchy.owningAcademicUnit().id());
        assertEquals(2, hierarchy.ancestorPath().size());
    }

    private void expectCatalogueRequest(HttpStatus status, String detail) {
        String requestUrl = "http://academic-setup.test/api/academic/admissions-catalogue"
                + "?academicYearId=" + ACADEMIC_YEAR_ID
                + "&intakeId=" + INTAKE_ID;
        server.expect(requestTo(requestUrl))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer forwarded-jwt"))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Academic catalogue rejected\",\"detail\":\"" + detail + "\"}"));
    }
}
