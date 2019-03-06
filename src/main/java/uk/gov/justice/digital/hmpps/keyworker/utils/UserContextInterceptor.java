package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class UserContextInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
            throws IOException {

        final var headers = request.getHeaders();
        headers.add(MdcUtility.CORRELATION_ID_HEADER, MDC.get(MdcUtility.CORRELATION_ID_HEADER));

        return execution.execute(request, body);
    }
}
