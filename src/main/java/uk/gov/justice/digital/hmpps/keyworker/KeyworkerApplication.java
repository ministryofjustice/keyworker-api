package uk.gov.justice.digital.hmpps.keyworker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayTokenGenerator;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContextInterceptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableResourceServer
@EnableAutoConfiguration
@Configuration
@RestController
public class KeyworkerApplication {

    @Value("${use.api.gateway.auth}")
    private boolean useApiGateway;

    @Autowired
    private ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    @RequestMapping("/")
    public String home(Principal user) {
        return "Hello " + user.getName();
    }

    @Primary
    @Bean
    public RestTemplate getCustomRestTemplate() {
        RestTemplate template = new RestTemplate();

        final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        final List<ClientHttpRequestInterceptor> currentInterceptors = template.getInterceptors();
        if (currentInterceptors != null) {
            interceptors.addAll(currentInterceptors);
        }

        interceptors.add(new UserContextInterceptor());
        if (useApiGateway) {
            interceptors.add(new ApiGatewayInterceptor(apiGatewayTokenGenerator));
        } else {
            interceptors.add(new JwtAuthInterceptor());

        }
        template.setInterceptors(interceptors);
        return template;
    }

    public static void main(String[] args) {
        SpringApplication.run(KeyworkerApplication.class, args);
    }

}
