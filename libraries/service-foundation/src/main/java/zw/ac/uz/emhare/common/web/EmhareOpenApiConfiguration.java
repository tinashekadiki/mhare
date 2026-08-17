package zw.ac.uz.emhare.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Generates a consistently identified OpenAPI document for every HTTP service. @author Tinashe K
 */
@Configuration
public class EmhareOpenApiConfiguration {

  @Bean
  OpenAPI emhareOpenApi(
      @Value("${spring.application.name:emhare-service}") String applicationName) {
    return new OpenAPI()
        .info(
            new Info()
                .title(applicationName + " API")
                .version("v1")
                .description("Generated eMhare service contract."));
  }
}
