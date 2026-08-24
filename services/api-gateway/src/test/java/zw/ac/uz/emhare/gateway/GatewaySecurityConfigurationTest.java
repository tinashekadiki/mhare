package zw.ac.uz.emhare.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * @author Tinashe K
 */
class GatewaySecurityConfigurationTest {

  @Test
  void gatewayCorsConfigurationSource_shouldUseConfiguredPortalOrigins() {
    List<String> configuredOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3010");
    CorsConfigurationSource source =
        new GatewaySecurityConfiguration().gatewayCorsConfigurationSource(configuredOrigins);

    CorsConfiguration configuration =
        source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/core/me"));

    assertNotNull(configuration);
    assertEquals(configuredOrigins, configuration.getAllowedOrigins());
  }
}
