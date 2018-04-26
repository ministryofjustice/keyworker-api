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

    @Bean
    public OAuth2RestTemplate elite2SystemRestTemplate(GatewayAwareAccessTokenProvider accessTokenProvider) {

        OAuth2RestTemplate elite2SystemRestTemplate = new OAuth2RestTemplate(elite2apiDetails, oauth2ClientContext);
        List<ClientHttpRequestInterceptor> systemInterceptors = elite2SystemRestTemplate.getInterceptors();
        systemInterceptors.add(new UserContextInterceptor());
        if (useApiGateway) {
            systemInterceptors.add(new ApiGatewayBatchRequestInterceptor(apiGatewayTokenGenerator));
            // The access token provider's rest template also needs to know how to get through the gateway
            List<ClientHttpRequestInterceptor> tokenProviderInterceptors = ((RestTemplate) accessTokenProvider.getRestTemplate()).getInterceptors();
            tokenProviderInterceptors.add(new ApiGatewayBatchRequestInterceptor(apiGatewayTokenGenerator));
        } else {
            systemInterceptors.add(new JwtAuthInterceptor());
        }

        elite2SystemRestTemplate.setAccessTokenProvider(accessTokenProvider);

        RootUriTemplateHandler.addTo(elite2SystemRestTemplate, this.apiRootUri);
        return elite2SystemRestTemplate;
    }

    /**
     * This subclass is necessary to make OAuth2AccessTokenSupport.getRestTemplate() public
     */
    @Component("accessTokenProvider")
    public class GatewayAwareAccessTokenProvider extends ClientCredentialsAccessTokenProvider {
        @Override
        public RestOperations getRestTemplate() {
            return super.getRestTemplate();
        }
    }
}
