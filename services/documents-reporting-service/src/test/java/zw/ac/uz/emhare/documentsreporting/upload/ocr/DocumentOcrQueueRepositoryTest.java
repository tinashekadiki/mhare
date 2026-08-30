package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.DocumentOcrExtractionRepository;

/** Tests queue progress against real PostgreSQL with inaccessible historical uploads. @author Tinashe K */
@Testcontainers
class DocumentOcrQueueRepositoryTest {
  @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
  static EntityManagerFactory factory;

  @BeforeAll
  static void configurePersistence() {
    var dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    var configuration = new LocalContainerEntityManagerFactoryBean();
    configuration.setDataSource(dataSource);
    configuration.setPackagesToScan("zw.ac.uz.emhare.documentsreporting.upload.domain.model");
    configuration.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    configuration.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
    configuration.afterPropertiesSet();
    factory = configuration.getObject();
  }

  @AfterAll static void closePersistence() { if (factory != null) factory.close(); }

  @Test
  void skipsMissingAndSoftDeletedUploadsWithoutStarvingTheNextDocument() {
    try (EntityManager entityManager = factory.createEntityManager()) {
      entityManager.getTransaction().begin();
      Instant now = Instant.now();
      var orphan = queue(entityManager, now.minusSeconds(60));
      var deleted = queue(entityManager, now.minusSeconds(50));
      var ready = queue(entityManager, now.minusSeconds(40));
      entityManager.flush();
      UUID readyId = ready.getId();
      entityManager.createNativeQuery("set local session_replication_role = replica").executeUpdate();
      entityManager.createNativeQuery("delete from uploaded_documents where id = :id")
          .setParameter("id", orphan.getUploadedDocument().getId()).executeUpdate();
      entityManager.createNativeQuery("update uploaded_documents set deleted_at = now(), deleted_by_user_id = :actor where id = :id")
          .setParameter("actor", UUID.randomUUID()).setParameter("id", deleted.getUploadedDocument().getId()).executeUpdate();
      entityManager.clear();
      var repository = new JpaRepositoryFactory(entityManager).getRepository(DocumentOcrExtractionRepository.class);
      var selected = repository.findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(DocumentOcrStatus.QUEUED, now).orElseThrow();
      assertThat(selected.getId()).isEqualTo(readyId);
      assertThat(selected.getUploadedDocument().getOriginalFileName()).isEqualTo("identity.pdf");
      entityManager.getTransaction().rollback();
    }
  }

  private DocumentOcrExtraction queue(EntityManager entityManager, Instant at) {
    var document = new UploadedDocument(UploadedDocument.OwnerType.APPLICATION, UUID.randomUUID(), "IDENTITY_DOCUMENT", "identity.pdf", "test", UUID.randomUUID().toString(), null, "application/pdf", 10, "a".repeat(64), UUID.randomUUID(), at, null);
    timestamps(document, at);
    entityManager.persist(document);
    var extraction = new DocumentOcrExtraction(document, "TEST", "1", at);
    timestamps(extraction, at);
    entityManager.persist(extraction);
    return extraction;
  }
  private void timestamps(AuditableEntity entity, Instant at) {
    ReflectionTestUtils.setField(entity, "createdAt", at);
    ReflectionTestUtils.setField(entity, "updatedAt", at);
  }
}
