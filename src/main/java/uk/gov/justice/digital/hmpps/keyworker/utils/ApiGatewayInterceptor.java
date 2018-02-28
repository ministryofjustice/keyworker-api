package uk.gov.justice.digital.hmpps.keyworker.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Slf4j
public class ApiGatewayInterceptor implements ClientHttpRequestInterceptor {

    private final ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    public ApiGatewayInterceptor(ApiGatewayTokenGenerator apiGatewayTokenGenerator) {
        this.apiGatewayTokenGenerator = apiGatewayTokenGenerator;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        HttpHeaders headers = request.getHeaders();
        headers.add("elite-authorization", UserContext.getAuthToken());
        try {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer "+ apiGatewayTokenGenerator.createGatewayToken());
        } catch (Exception e) {
            throw new IOException(e);
        }

        return execution.execute(request, body);
    }
}