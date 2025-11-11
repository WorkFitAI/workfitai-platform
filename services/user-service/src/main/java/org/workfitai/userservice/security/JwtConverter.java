package org.workfitai.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.spec.SecretKeySpec;
import java.util.HashSet;

@Configuration
public class JwtConverter {

  @Value("${jwt.secret}")
  private String jwtSecret;

  /**
   * Verify JWT từ auth-service (HS256)
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(
        new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256")
    ).build();
  }

  /**
   * Converter roles + perms từ JWT claims -> GrantedAuthorities
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
    rolesConverter.setAuthoritiesClaimName("roles");
    rolesConverter.setAuthorityPrefix("ROLE_");

    JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
    permsConverter.setAuthoritiesClaimName("perms");
    permsConverter.setAuthorityPrefix("");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      var authorities = new HashSet<>(rolesConverter.convert(jwt));
      authorities.addAll(permsConverter.convert(jwt));
      return authorities;
    });

    return converter;
  }
}
