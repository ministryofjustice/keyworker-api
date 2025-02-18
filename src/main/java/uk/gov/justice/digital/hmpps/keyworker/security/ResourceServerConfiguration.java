package uk.gov.justice.digital.hmpps.keyworker.security;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableWebSecurity
public class ResourceServerConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        return http.headers(headersConfigurer -> headersConfigurer.frameOptions(FrameOptionsConfig::sameOrigin))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(httpRequest -> httpRequest.requestMatchers(
                "/webjars/**", "/favicon.ico", "/csrf",
                "/health/**", "/info", "/ping",
                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                "/swagger-resources", "/swagger-resources/configuration/ui", "/swagger-resources/configuration/security",
                "/queue-admin/retry-all-dlqs",
                "/batch/key-worker-recon", "/batch/update-status", "/batch/generate-stats",
                "/prison-statistics/calculate"
            ).permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(new AuthAwareTokenConverter())))
            .build();
    }

    @Service(value = "auditorAware")
    public class AuditorAwareImpl implements AuditorAware<String> {
        AuthenticationFacade authenticationFacade;

        public AuditorAwareImpl(final AuthenticationFacade authenticationFacade) {
            this.authenticationFacade = authenticationFacade;
        }

        @Override
        public Optional<String> getCurrentAuditor() {
            return Optional.ofNullable(authenticationFacade.getCurrentUsername());
        }
    }
}
