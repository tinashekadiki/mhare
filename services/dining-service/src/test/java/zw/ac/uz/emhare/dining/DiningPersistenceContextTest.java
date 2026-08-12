package zw.ac.uz.emhare.dining;

import zw.ac.uz.emhare.dining.setup.domain.model.DiningHall;
import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlanMeal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** @author Tinashe K */
@Testcontainers
@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/test-jwks","spring.rabbitmq.listener.simple.auto-startup=false"})
class DiningPersistenceContextTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("emhare_dining_mapping").withUsername("emhare_service").withPassword("emhare_test_password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();
        registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired EntityManagerFactory entityManagerFactory;
    @Test void validatesDiningMappingsAgainstMigratedPostgresqlSchema(){
        var names=Arrays.stream(entityManagerFactory.getMetamodel().getEntities().toArray()).map(Object::toString).toList();
        assertTrue(names.stream().anyMatch(name->name.contains("DiningHall")));
        assertTrue(names.stream().anyMatch(name->name.contains("DiningPlanMeal")));
    }
}
