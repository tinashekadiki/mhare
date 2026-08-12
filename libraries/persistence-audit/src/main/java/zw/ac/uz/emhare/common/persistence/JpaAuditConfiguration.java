package zw.ac.uz.emhare.common.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "zw.ac.uz.emhare")
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditorAware")
@EnableJpaRepositories(basePackages = "zw.ac.uz.emhare")
public class JpaAuditConfiguration {
}
