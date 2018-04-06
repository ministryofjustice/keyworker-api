package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.HealthMvcEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

@SpringBootApplication
@EnableResourceServer
public class KeyworkerApplication {

    @Bean public ConversionService conversionService() {
        return new DefaultConversionService();
    }

    /**
     * Override default health endpoint (defined in EndpointWebMvcManagementContextConfiguration) to disable restricted
     * security mode but keep security for other endpoints such as /dump etc
     */
    @Bean
    public HealthMvcEndpoint healthMvcEndpoint(@Autowired(required = false) HealthEndpoint delegate) {
        if (delegate == null) {
            // This happens in unit test environment
            return null;
        }
        HealthMvcEndpoint healthMvcEndpoint = new HealthMvcEndpoint(delegate, false, null);
        return healthMvcEndpoint;
    }

    public static void main(String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }
}
