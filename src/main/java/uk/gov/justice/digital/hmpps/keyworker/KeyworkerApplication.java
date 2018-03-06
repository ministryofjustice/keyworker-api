package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.boot.SpringApplication;
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

    public static void main(String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }
}
