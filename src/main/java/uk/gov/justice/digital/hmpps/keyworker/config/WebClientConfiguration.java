package uk.gov.justice.digital.hmpps.keyworker.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContext;

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
        oauth2Client.setDefaultClientRegistrationId("elite2-api");
        return builder.baseUrl(elite2ApiRootUri)
                .apply(oauth2Client.oauth2Configuration())
                .build();
    }

    @Bean
    WebClient webClient(final WebClient.Builder builder) {
        return builder
                .baseUrl(elite2ApiRootUri)
                .filter(addAuthHeaderFilterFunction())
                .build();
    }

    @Bean
    WebClient healthWebClient(final WebClient.Builder builder) {
        return builder
                .baseUrl(healthRootUri)
                .filter(addAuthHeaderFilterFunction())
                .build();
    }

    @NotNull
    private ExchangeFilterFunction addAuthHeaderFilterFunction() {
        return (request, next) -> {
            ClientRequest filtered = ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken())
                    .build();
            return next.exchange(filtered);
        };
    }


}
