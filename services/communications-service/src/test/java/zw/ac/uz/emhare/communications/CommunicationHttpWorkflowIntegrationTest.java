package zw.ac.uz.emhare.communications;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.communications.media.infrastructure.CommunicationMediaStorage;

/** End-to-end HTTP proof for editorial governance and public publication. @author Tinashe K */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "management.tracing.enabled=false"
    })
class CommunicationHttpWorkflowIntegrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("emhare_communications")
          .withUsername("emhare_service")
          .withPassword("emhare_test_password");

  private static final UUID AUTHOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID APPROVER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID READER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CommunicationMediaStorage mediaStorage;

  @BeforeEach
  void configureMediaStorage() {
    when(mediaStorage.maximumBytes()).thenReturn(10_485_760L);
    when(mediaStorage.read(anyString())).thenReturn("image".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void governsEventFromDraftThroughPublicReadCorrectionAndWithdrawal() throws Exception {
    JsonNode created =
        responseJson(
            mockMvc
                .perform(
                    post("/api/communications/editorial/items")
                        .with(author())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventDraft("open-day-integration")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("DRAFT"))
                .andReturn());

    String itemId = created.path("itemId").asText();
    String versionId = created.path("versionId").asText();
    long expectedVersion = created.path("expectedVersion").asLong();

    JsonNode edited =
        responseJson(
            mockMvc
                .perform(
                    put("/api/communications/editorial/versions/{versionId}", versionId)
                        .with(author())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editEvent(expectedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("University Open Day 2026"))
                .andReturn());

    JsonNode submitted =
        responseJson(
            mockMvc
                .perform(
                    post("/api/communications/editorial/versions/{versionId}/submit", versionId)
                        .with(author())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expectedVersion(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("IN_REVIEW"))
                .andReturn());

    mockMvc
        .perform(
            post("/api/communications/editorial/versions/{versionId}/approve", versionId)
                .with(authorAndApprover())
                .contentType(MediaType.APPLICATION_JSON)
                .content(expectedVersion(submitted)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"));

    JsonNode approved =
        responseJson(
            mockMvc
                .perform(
                    post("/api/communications/editorial/versions/{versionId}/approve", versionId)
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expectedVersion(submitted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("APPROVED"))
                .andReturn());

    mockMvc
        .perform(
            put("/api/communications/editorial/versions/{versionId}", versionId)
                .with(author())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editEvent(approved.path("expectedVersion").asLong())))
        .andExpect(status().isConflict());

    JsonNode scheduled =
        responseJson(
            mockMvc
                .perform(
                    post(
                            "/api/communications/editorial/versions/{versionId}/publications",
                            versionId)
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleNow()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("LIVE"))
                .andReturn());

    String publicationId = scheduled.path("publicationId").asText();
    long publicationVersion = scheduled.path("publicationExpectedVersion").asLong();

    mockMvc
        .perform(get("/api/communications/public/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upcomingEvents[0].slug").value("open-day-integration"));
    mockMvc
        .perform(get("/api/communications/public/items/open-day-integration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.timezone").value("Africa/Harare"));
    mockMvc
        .perform(get("/api/communications/public/events/open-day-integration/calendar.ics"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString("open-day-integration.ics")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "URL:http://localhost:3002/events/open-day-integration")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("TZID=Africa/Harare")));

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              put("/api/communications/publications/{publicationId}/read", publicationId)
                  .with(reader()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.publicationId").value(publicationId));
    }

    mockMvc
        .perform(
            post("/api/communications/editorial/items/{itemId}/corrections", itemId).with(author()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNumber").value(2))
        .andExpect(jsonPath("$.workflowStatus").value("DRAFT"));

    mockMvc
        .perform(
            post(
                    "/api/communications/editorial/publications/{publicationId}/withdraw",
                    publicationId)
                .with(approver())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"expectedVersion":%d,"reason":"Event cancelled"}
                    """
                        .formatted(publicationVersion)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationStatus").value("WITHDRAWN"));
    mockMvc
        .perform(get("/api/communications/public/items/open-day-integration"))
        .andExpect(status().isNotFound());
  }

  @Test
  void supportsEditorialQueuesRejectionCategoriesMediaAndValidation() throws Exception {
    mockMvc
        .perform(get("/api/communications/editorial/items"))
        .andExpect(status().isUnauthorized());

    JsonNode category =
        responseJson(
            mockMvc
                .perform(
                    post("/api/communications/editorial/categories")
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"code":"INTEGRATION","name":"Integration","description":"Test category","displayOrder":90,"active":true,"expectedVersion":0}
                            """))
                .andExpect(status().isOk())
                .andReturn());
    mockMvc
        .perform(
            put(
                    "/api/communications/editorial/categories/{categoryId}",
                    category.path("id").asText())
                .with(approver())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"INTEGRATION","name":"Integration notices","description":"Updated","displayOrder":91,"active":true,"expectedVersion":%d}
                    """
                        .formatted(category.path("expectedVersion").asLong())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Integration notices"));

    JsonNode notice = createSimpleDraft("NOTICE", "integration-notice", null);
    JsonNode submitted = submit(notice);
    mockMvc
        .perform(
            post(
                    "/api/communications/editorial/versions/{versionId}/reject",
                    notice.path("versionId").asText())
                .with(approver())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"expectedVersion":%d,"reason":"Needs a clearer source"}
                    """
                        .formatted(submitted.path("expectedVersion").asLong())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowStatus").value("REJECTED"));

    mockMvc
        .perform(
            get("/api/communications/editorial/items")
                .queryParam("query", "integration")
                .queryParam("kind", "NOTICE")
                .with(author()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    mockMvc
        .perform(get("/api/communications/editorial/items").with(author()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    mockMvc
        .perform(
            get("/api/communications/editorial/items/{itemId}", notice.path("itemId").asText())
                .with(author()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item.slug").value("integration-notice"));
    mockMvc
        .perform(get("/api/communications/editorial/categories").with(author()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").exists());

    mockMvc
        .perform(
            post("/api/communications/editorial/items")
                .with(author())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simpleDraft("LINK", "invalid-link", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"));

    MockMultipartFile image =
        new MockMultipartFile(
            "file", "poster.png", "image/png", "image".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile alternativeText =
        new MockMultipartFile(
            "alternativeText",
            "",
            "text/plain",
            "Open day poster".getBytes(StandardCharsets.UTF_8));
    JsonNode media =
        responseJson(
            mockMvc
                .perform(
                    multipart("/api/communications/editorial/media")
                        .file(image)
                        .file(alternativeText)
                        .with(author()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativeText").value("Open day poster"))
                .andReturn());
    mockMvc
        .perform(get("/api/communications/public/media/{assetId}", media.path("id").asText()))
        .andExpect(status().isOk())
        .andExpect(content().bytes("image".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void generatesUniquePublicSlugsFromDraftTitles() throws Exception {
    String draftWithoutSlug =
        """
        {"kind":"NEWS","title":"Café research showcase","summary":"Public research news.","structuredContent":[{"type":"PARAGRAPH","text":"Research information."}]}
        """;

    mockMvc
        .perform(
            post("/api/communications/editorial/items")
                .with(author())
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftWithoutSlug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("cafe-research-showcase"));

    mockMvc
        .perform(
            post("/api/communications/editorial/items")
                .with(author())
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftWithoutSlug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("cafe-research-showcase-2"));
  }

  private JsonNode createSimpleDraft(String kind, String slug, String externalUrl)
      throws Exception {
    return responseJson(
        mockMvc
            .perform(
                post("/api/communications/editorial/items")
                    .with(author())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(simpleDraft(kind, slug, externalUrl)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode submit(JsonNode item) throws Exception {
    return responseJson(
        mockMvc
            .perform(
                post(
                        "/api/communications/editorial/versions/{versionId}/submit",
                        item.path("versionId").asText())
                    .with(author())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(expectedVersion(item)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private String eventDraft(String slug) {
    return """
        {"kind":"EVENT","slug":"%s","title":"University Open Day","summary":"Meet the university community.","structuredContent":[{"type":"PARAGRAPH","text":"Applicants and families are welcome."}],"event":{"startsAt":"2026-09-12T07:00:00Z","endsAt":"2026-09-12T13:00:00Z","timezone":"Africa/Harare","attendanceMode":"IN_PERSON","venueName":"Great Hall","address":"630 Churchill Avenue","onlineUrl":null}}
        """
        .formatted(slug);
  }

  private String editEvent(long expectedVersion) {
    return """
        {"title":"University Open Day 2026","summary":"Meet academic teams and student services.","structuredContent":[{"type":"HEADING","text":"Plan your visit","level":2},{"type":"CALLOUT","text":"Admission is free.","tone":"INFO"}],"event":{"startsAt":"2026-09-12T07:00:00Z","endsAt":"2026-09-12T13:00:00Z","timezone":"Africa/Harare","attendanceMode":"IN_PERSON","venueName":"Great Hall","address":"630 Churchill Avenue","onlineUrl":null},"expectedVersion":%d}
        """
        .formatted(expectedVersion);
  }

  private String simpleDraft(String kind, String slug, String externalUrl) {
    String external =
        externalUrl == null ? "null" : objectMapper.valueToTree(externalUrl).toString();
    return """
        {"kind":"%s","slug":"%s","title":"Integration notice","summary":"A governed public notice.","structuredContent":[{"type":"PARAGRAPH","text":"Public information."}],"externalUrl":%s}
        """
        .formatted(kind, slug, external);
  }

  private String scheduleNow() {
    return """
        {"publishFrom":"%s","publishUntil":"%s","pinned":true,"featured":false,"displayOrder":1}
        """
        .formatted(Instant.now().minusSeconds(60), Instant.now().plusSeconds(86_400));
  }

  private String expectedVersion(JsonNode response) {
    return "{\"expectedVersion\":" + response.path("expectedVersion").asLong() + "}";
  }

  private JsonNode responseJson(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor author() {
    return authenticated(AUTHOR_ID, "ROLE_communications-author");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor approver() {
    return authenticated(APPROVER_ID, "ROLE_communications-approver");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authorAndApprover() {
    return jwt()
        .jwt(
            token ->
                token.subject(AUTHOR_ID.toString()).claim("emhare_user_id", AUTHOR_ID.toString()))
        .authorities(
            new SimpleGrantedAuthority("ROLE_communications-author"),
            new SimpleGrantedAuthority("ROLE_communications-approver"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
    return authenticated(READER_ID, "ROLE_student");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticated(
      UUID userId, String authority) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()).claim("emhare_user_id", userId.toString()))
        .authorities(new SimpleGrantedAuthority(authority));
  }
}
