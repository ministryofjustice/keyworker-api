package uk.gov.justice.digital.hmpps.keyworker.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.stereotype.Service;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import uk.gov.justice.digital.hmpps.keyworker.controllers.KeyworkerServiceController;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;

@Configuration
@EnableSwagger2
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableWebSecurity
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter {
    
    @Override
    public void configure(final HttpSecurity http) throws Exception {

        http
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                // Can't have CSRF protection as requires session
                .and().csrf().disable()
        .authorizeRequests(auth ->
                auth.antMatchers("/webjars/**", "/favicon.ico", "/csrf",
                        "/health/**", "/info", "/ping",
                        "/v3/api-docs", "/swagger-ui/**",
                        "/swagger-resources", "/swagger-resources/configuration/ui", "/swagger-resources/configuration/security")
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
