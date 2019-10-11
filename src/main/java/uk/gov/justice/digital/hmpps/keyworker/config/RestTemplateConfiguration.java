package uk.gov.justice.digital.hmpps.keyworker.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RootUriTemplateHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthInterceptor;

import java.time.Duration;

@Configuration
public class RestTemplateConfiguration {

    private final OAuth2ClientContext oauth2ClientContext;
    private final ClientCredentialsResourceDetails elite2apiDetails;

    private final String elite2UriRoot;
    private final String apiRootUri;
    private final Duration healthTimeout;

    @Autowired
    public RestTemplateConfiguration(
            final OAuth2ClientContext oauth2ClientContext,
            final ClientCredentialsResourceDetails elite2apiDetails,
            @Value("${elite2.uri.root}") final String elite2UriRoot,
            @Value("${elite2.api.uri.root}") final String apiRootUri,
            @Value("${api.health-timeout:1s}") final Duration healthTimeout) {
        this.oauth2ClientContext = oauth2ClientContext;
        this.elite2apiDetails = elite2apiDetails;
        this.elite2UriRoot = elite2UriRoot;
        this.apiRootUri = apiRootUri;
        this.healthTimeout = healthTimeout;
    }

    @Bean(name = "elite2ApiRestTemplate")
    public RestTemplate elite2ApiRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, apiRootUri);
    }

    @Bean(name = "elite2ApiHealthRestTemplate")
    public RestTemplate elite2ApiHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getHealthRestTemplate(restTemplateBuilder, elite2UriRoot);
    }

    private RestTemplate getRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(new JwtAuthInterceptor())
                .build();
    }

    private RestTemplate getHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(new JwtAuthInterceptor())
                .setConnectTimeout(healthTimeout)
                .setReadTimeout(healthTimeout)
                .build();
    }

    @Bean
    public OAuth2RestTemplate elite2SystemRestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider) {

        final var elite2SystemRestTemplate = new OAuth2RestTemplate(elite2apiDetails, oauth2ClientContext);

        elite2SystemRestTemplate.setAccessTokenProvider(accessTokenProvider);

        RootUriTemplateHandler.addTo(elite2SystemRestTemplate, this.apiRootUri);
        return elite2SystemRestTemplate;
    }

    /**
     * This subclass is necessary to make OAuth2AccessTokenSupport.getRestTemplate() public
     */
    @Component("accessTokenProvider")
    public static class GatewayAwareAccessTokenProvider extends ClientCredentialsAccessTokenProvider {
        @Override
        public RestOperations getRestTemplate() {
            return super.getRestTemplate();
        }
    }
}
