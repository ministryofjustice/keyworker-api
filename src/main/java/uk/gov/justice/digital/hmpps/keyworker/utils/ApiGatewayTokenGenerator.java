package uk.gov.justice.digital.hmpps.keyworker.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pdi.jwt.Jwt;
import pdi.jwt.JwtAlgorithm;

import java.time.Instant;

@Component
@Slf4j
public class ApiGatewayTokenGenerator {

    private final String apiGatewayToken;
    private final String apiGatewayPrivateKey;

    public ApiGatewayTokenGenerator(@Value("${api.gateway.token}") final String apiGatewayToken,
                                     @Value("${api.gateway.private.key}") final String apiGatewayPrivateKey) {
        this.apiGatewayToken = apiGatewayToken;
        this.apiGatewayPrivateKey = apiGatewayPrivateKey;
    }

    public String createGatewayToken() {

        final String payloadToken = String.format("{ \"iat\": %d, \"token\": \"%s\" }",
                Instant.now().getEpochSecond(),
                apiGatewayToken);

            return Jwt.encode(payloadToken, apiGatewayPrivateKey, JwtAlgorithm.ES256$.MODULE$);

    }
}
