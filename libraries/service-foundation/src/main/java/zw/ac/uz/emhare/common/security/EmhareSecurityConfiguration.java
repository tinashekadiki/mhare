package zw.ac.uz.emhare.common.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class EmhareSecurityConfiguration {

  @Bean
  SecurityFilterChain emhareSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .hasRole("system-admin")
                    .requestMatchers("/actuator/metrics/**", "/actuator/prometheus")
                    .hasRole("system-admin")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
    return jwt ->
        new JwtAuthenticationToken(
            jwt, authorities(jwt), jwt.getClaimAsString("preferred_username"));
  }

  private Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    Object realmAccess = jwt.getClaim("realm_access");
    if (realmAccess instanceof Map<?, ?> realmAccessMap) {
      Object roles = realmAccessMap.get("roles");
      if (roles instanceof Collection<?> roleValues) {
        for (Object role : roleValues) {
          if (role instanceof String roleName && !roleName.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
          }
        }
      }
    }
    return authorities;
  }
}
