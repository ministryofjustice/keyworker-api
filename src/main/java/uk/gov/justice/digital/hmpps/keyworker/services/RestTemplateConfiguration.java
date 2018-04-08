package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayTokenGenerator;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContextInterceptor;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfiguration {

    @Bean
    RestTemplate restTemplate(
            ApiGatewayTokenGenerator apiGatewayTokenGenerator,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${elite2.api.uri.root:http://localhost:8080/api}") String apiRootUri,
            @Value("${use.api.gateway.auth}") boolean useApiGateway) {

        List<ClientHttpRequestInterceptor> additionalInterceptors = new ArrayList<>();

        additionalInterceptors.add(new UserContextInterceptor());

        if (useApiGateway) {
            additionalInterceptors.add(new ApiGatewayInterceptor(apiGatewayTokenGenerator));
        } else {
            additionalInterceptors.add(new JwtAuthInterceptor());

        }

        return restTemplateBuilder
                .rootUri(apiRootUri)
                .additionalInterceptors(additionalInterceptors)
                .build();

    }
}
