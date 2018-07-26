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
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContextInterceptor;

import java.util.Arrays;
import java.util.List;

@Configuration
public class RestTemplateConfiguration {

    private final OAuth2ClientContext oauth2ClientContext;
    private final ClientCredentialsResourceDetails elite2apiDetails;

    @Value("${elite2.uri.root}")
    private String elite2UriRoot;

    @Value("${elite2.api.uri.root}")
    private String apiRootUri;

    @Autowired
    public RestTemplateConfiguration(
            OAuth2ClientContext oauth2ClientContext,
            ClientCredentialsResourceDetails elite2apiDetails) {
        this.oauth2ClientContext = oauth2ClientContext;
        this.elite2apiDetails = elite2apiDetails;
    }

    @Bean(name = "elite2ApiRestTemplate")
    public RestTemplate elite2ApiRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, apiRootUri);
    }

    @Bean(name = "elite2ApiHealthRestTemplate")
    public RestTemplate elite2ApiHealthRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, elite2UriRoot);
    }

    private RestTemplate getRestTemplate(RestTemplateBuilder restTemplateBuilder, String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(getRequestInterceptors())
                .build();
    }

    private List<ClientHttpRequestInterceptor> getRequestInterceptors() {
        return Arrays.asList(
                new UserContextInterceptor(),
                new JwtAuthInterceptor());
    }

    @Bean
    public OAuth2RestTemplate elite2SystemRestTemplate(GatewayAwareAccessTokenProvider accessTokenProvider) {

        OAuth2RestTemplate elite2SystemRestTemplate = new OAuth2RestTemplate(elite2apiDetails, oauth2ClientContext);
        List<ClientHttpRequestInterceptor> systemInterceptors = elite2SystemRestTemplate.getInterceptors();
        systemInterceptors.add(new UserContextInterceptor());

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
