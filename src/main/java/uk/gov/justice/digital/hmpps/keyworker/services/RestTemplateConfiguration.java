package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RootUriTemplateHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.utils.*;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfiguration {

    private final OAuth2ClientContext oauth2ClientContext;
    private final ClientCredentialsResourceDetails elite2apiDetails;
    private final ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    @Value("${elite2.api.uri.root}")
    private String apiRootUri;
    @Value("${use.api.gateway.auth}")
    private boolean useApiGateway;

    @Autowired
    public RestTemplateConfiguration(
            OAuth2ClientContext oauth2ClientContext,
            ClientCredentialsResourceDetails elite2apiDetails,
            ApiGatewayTokenGenerator apiGatewayTokenGenerator) {
        this.oauth2ClientContext = oauth2ClientContext;
        this.elite2apiDetails = elite2apiDetails;
        this.apiGatewayTokenGenerator = apiGatewayTokenGenerator;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {

        List<ClientHttpRequestInterceptor> additionalInterceptors = new ArrayList<>();
        configureInterceptors(additionalInterceptors);

        return restTemplateBuilder
                .rootUri(apiRootUri)
                .additionalInterceptors(additionalInterceptors)
                .build();
    }

    @Bean
    public OAuth2RestTemplate elite2SystemRestTemplate(final RestTemplate restTemplate,
                                                       GatewayAwareAccessTokenProvider accessTokenProvider) {

        OAuth2RestTemplate elite2SystemRestTemplate = new OAuth2RestTemplate(elite2apiDetails, oauth2ClientContext);
        configureInterceptors(elite2SystemRestTemplate.getInterceptors());

        if (useApiGateway) {
            // The access token provider needs to know how to get through the gateway
            final List<ClientHttpRequestInterceptor> interceptors = ((RestTemplate) accessTokenProvider.getRestTemplate()).getInterceptors();
            interceptors.add(new ApiGatewayTokenRequestInterceptor(apiGatewayTokenGenerator));
        }
        elite2SystemRestTemplate.setAccessTokenProvider(accessTokenProvider);

        RootUriTemplateHandler.addTo(elite2SystemRestTemplate, this.apiRootUri);
        return elite2SystemRestTemplate;
    }

    private void configureInterceptors(List<ClientHttpRequestInterceptor> interceptors) {
        interceptors.add(new UserContextInterceptor());
        if (useApiGateway) {
            interceptors.add(new ApiGatewayInterceptor(apiGatewayTokenGenerator));
        } else {
            interceptors.add(new JwtAuthInterceptor());
        }
    }

    @Component
    public class GatewayAwareAccessTokenProvider extends ClientCredentialsAccessTokenProvider {
        @Override
        public RestOperations getRestTemplate() {
            return super.getRestTemplate();
        }
    }
}
