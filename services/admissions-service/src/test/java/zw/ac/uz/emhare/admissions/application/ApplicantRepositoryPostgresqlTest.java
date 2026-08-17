package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;

/**
 * @author Tinashe K
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.task.scheduling.enabled=false",
      "management.health.rabbit.enabled=false",
      "emhare.messaging.integration-enabled=false"
    })
class ApplicantRepositoryPostgresqlTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("emhare_admissions")
          .withUsername("emhare_service")
          .withPassword("emhare_test_password");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    Flyway.configure()
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
  }

  @Autowired private ApplicantRepository applicantRepository;

  @Autowired private MockMvc mockMvc;

  @Autowired private EntityManager entityManager;

  @Test
  void applicantRegisterQuerySupportsCaseInsensitivePostgresqlPagination() {
    var applicantPage =
        applicantRepository.findRegisterPage(
            "",
            null,
            null,
            PageRequest.of(
                0,
                25,
                Sort.by(
                    Sort.Order.asc("lastName").ignoreCase(),
                    Sort.Order.asc("firstName").ignoreCase(),
                    Sort.Order.asc("applicantNumber"))));

    assertEquals(0, applicantPage.getTotalElements());
    assertEquals(0, applicantPage.getContent().size());
  }

  @Test
  void canonicalSoftDeleteRestrictionAppliesToGeneratedRepositoryQueries() {
    UUID userId = UUID.randomUUID();
    Applicant applicant =
        applicantRepository.saveAndFlush(
            new Applicant(
                userId,
                "EMH-SOFT-DELETE-001",
                "LOCAL",
                "Soft",
                "Deleted",
                "soft-delete@example.test"));

    applicant.markDeleted(UUID.randomUUID());
    applicantRepository.saveAndFlush(applicant);
    entityManager.clear();

    assertFalse(applicantRepository.findById(applicant.getId()).isPresent());
    assertFalse(applicantRepository.findByUserId(userId).isPresent());
  }

  @Test
  void generatesTheAdmissionsOpenApiDocumentFromLiveControllerMappings() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs").with(jwt().authorities(() -> "ROLE_system-admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.1.0"))
        .andExpect(jsonPath("$.info.title").value("admissions-service API"))
        .andExpect(jsonPath("$.paths['/api/admissions/applications']").exists());
  }
}
