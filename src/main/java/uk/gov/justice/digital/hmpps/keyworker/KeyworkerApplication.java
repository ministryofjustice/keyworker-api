package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class KeyworkerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }
}
