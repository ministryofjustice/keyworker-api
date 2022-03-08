package uk.gov.justice.digital.hmpps.keyworker.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableWebSecurity
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter {
    
    @Override
    public void configure(final HttpSecurity http) throws Exception {

        http.sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and().csrf().disable()// Can't have CSRF protection as requires session
            .authorizeRequests(auth ->
                auth.antMatchers("/webjars/**", "/favicon.ico", "/csrf",
                        "/health/**", "/info", "/ping",
                        "/v3/api-docs", "/v2/api-docs", "/swagger-ui/**",
                        "/swagger-resources", "/swagger-resources/configuration/ui", "/swagger-resources/configuration/security",
                        "/queue-admin/retry-all-dlqs")
                        .permitAll().anyRequest().authenticated())
        .oauth2ResourceServer().jwt().jwtAuthenticationConverter(new AuthAwareTokenConverter());
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
