package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Ignore
public class TestApiGatewayTokenGenerator {

    private String token = "replaceme";
    private String key = "secret";
    private ApiGatewayTokenGenerator generator;

    @Before
    public void init() {
        generator = new ApiGatewayTokenGenerator(token, key);
    }

    @Test
    public void testTokenGenerator() throws Exception {

        final RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + generator.createGatewayToken());
        ResponseEntity<String> health = restTemplate.exchange(
                "https://noms-api-dev.dsd.io/elite2api/health",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<String>() {
                });

        assertThat(health).isNotNull();
        System.out.println(health.getBody());
    }
}
