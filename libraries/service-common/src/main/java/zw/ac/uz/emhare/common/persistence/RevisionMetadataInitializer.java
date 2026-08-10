package zw.ac.uz.emhare.common.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RevisionMetadataInitializer implements ApplicationRunner {

    private final Environment environment;

    public RevisionMetadataInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        EmhareRevisionContext.setServiceName(environment.getProperty("spring.application.name"));
    }
}
