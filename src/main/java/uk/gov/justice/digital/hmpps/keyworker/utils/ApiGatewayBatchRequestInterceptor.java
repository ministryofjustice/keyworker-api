package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

/**
 * Moves an existing Authorisation header param to elite-authorization and adds the gateway Jwt
 */
public class ApiGatewayBatchRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    public ApiGatewayBatchRequestInterceptor(ApiGatewayTokenGenerator apiGatewayTokenGenerator) {
        this.apiGatewayTokenGenerator = apiGatewayTokenGenerator;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        HttpHeaders headers = request.getHeaders();
        final List<String> authorisation = headers.remove(HttpHeaders.AUTHORIZATION);
        if (!CollectionUtils.isEmpty(authorisation)) {
            headers.add("elite-authorization", authorisation.get(0));
        }
        try {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + apiGatewayTokenGenerator.createGatewayToken());
        } catch (Exception e) {
            throw new IOException(e);
        }
        return execution.execute(request, body);
    }
}