package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

@SpringBootApplication
@EnableResourceServer
public class KeyworkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }

}
