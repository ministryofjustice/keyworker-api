package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@SpringBootApplication
@EnableResourceServer
@EnableAutoConfiguration
@Configuration
@RestController
public class KeyworkerApplication {

    @RequestMapping("/")
    public String home(Principal user) {
        return "Hello " + user.getName();
    }


    public static void main(String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }

}
