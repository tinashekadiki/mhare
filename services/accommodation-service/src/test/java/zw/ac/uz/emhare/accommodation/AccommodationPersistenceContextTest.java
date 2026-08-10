package zw.ac.uz.emhare.accommodation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import jakarta.persistence.EntityManagerFactory;

/** @author Tinashe K */
@Testcontainers
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/test-jwks",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AccommodationPersistenceContextTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("emhare_accommodation")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void validatesAccommodationMappingsAgainstTheMigratedPostgresqlSchema() {
        var entityNames = Arrays.stream(entityManagerFactory.getMetamodel().getEntities().toArray())
                .map(Object::toString)
                .toList();
        assertTrue(entityNames.stream().anyMatch(name -> name.contains("AccommodationPremise")));
        assertTrue(entityNames.stream().anyMatch(name -> name.contains("AccommodationApplicationPeriod")));
    }
}
