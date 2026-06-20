package com.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  public static final String[] SECURITY_EXCLUDE_PATHS = {
      "/public/**", "/api/swagger-ui/**", "/swagger-ui/**", "/swagger-ui.html",
      "/api/v3/api-docs/**", "/v3/api-docs/**", "/favicon.ico", "/actuator/**",
      "/swagger-resources/**", "/external/**", "/api/auth/**"
  };

  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .securityContext(context -> context
            .securityContextRepository(securityContextRepository())
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .maximumSessions(1)
            .maxSessionsPreventsLogin(false)
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(SECURITY_EXCLUDE_PATHS).permitAll()
            .requestMatchers("/api/**").hasRole("USER")
            .anyRequest().authenticated()
        )
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              response.setContentType("application/json;charset=UTF-8");
              ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
                  .error(ApiResponse.Error.of("UNAUTHORIZED", "Authentication required"))
                  .build();
              response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            })
        );

    return http.build();
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
