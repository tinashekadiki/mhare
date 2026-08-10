package zw.ac.uz.emhare.finance.collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Proves Flyway V10 and every Finance JPA/Envers mapping agree on PostgreSQL 18. @author Tinashe K */
@Testcontainers
@SpringBootTest(properties={"spring.task.scheduling.enabled=false","spring.rabbitmq.listener.simple.auto-startup=false","management.health.rabbit.enabled=false"})
class FinanceCollectionsJpaValidationTest {
    @Container static final PostgreSQLContainer POSTGRESQL=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).withDatabaseName("emhare_finance").withUsername("emhare_service").withPassword("emhare_test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword()).locations("classpath:db/migration").load().migrate();registry.add("spring.datasource.url",POSTGRESQL::getJdbcUrl);registry.add("spring.datasource.username",POSTGRESQL::getUsername);registry.add("spring.datasource.password",POSTGRESQL::getPassword);}
    @Autowired ApplicationContext applicationContext;
    @Test void startsWithValidatedCollectionsMappings(){assertNotNull(applicationContext.getBean(GovernedFinanceCollectionsService.class));}
}
