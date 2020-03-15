package uk.gov.justice.digital.hmpps.keyworker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfiguration {

    private String elite2ApiRootUri;
    private String healthRootUri;

    public WebClientConfiguration(
            @Value("${elite2.api.uri.root}") final String elite2ApiRootUri,
            @Value("${elite2.uri.root}") final String healthRootUri
    ) {
        this.elite2ApiRootUri = elite2ApiRootUri;
        this.healthRootUri = healthRootUri;
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(final ClientRegistrationRepository clientRegistrationRepository,
                                                          final OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        final var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();
        final var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    @Bean
    WebClient oauth2WebClient(final OAuth2AuthorizedClientManager authorizedClientManager, final WebClient.Builder builder) {
        final var oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        final var exchangeStrategies =
                ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build();
        return builder.baseUrl(elite2ApiRootUri)
                .apply(oauth2Client.oauth2Configuration())
                .exchangeStrategies(exchangeStrategies)
                .build();
    }

    @Bean
    WebClient webClient(final WebClient.Builder builder) {
        return builder.baseUrl(elite2ApiRootUri).build();
    }

    @Bean
    WebClient healthWebClient(final WebClient.Builder builder) {
        return builder.baseUrl(healthRootUri).build();
    }
}
