package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayTokenGenerator;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContextInterceptor;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.*;

/**
 * Abstract base class that takes care of setting up rest template with base API url and request headers.
 */
@Component
public abstract class Elite2ApiSource {
    protected RestTemplate restTemplate;

    @Value("${elite2.api.uri.root:http://localhost:8080/api}")
    private String apiRootUri;

    @Value("${use.api.gateway.auth}")
    private boolean useApiGateway;

    @Autowired
    private ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    @Autowired
    RestTemplateBuilder restTemplateBuilder;

    @PostConstruct
    public void init() {
        List<ClientHttpRequestInterceptor> additionalInterceptors = new ArrayList<>();

        additionalInterceptors.add(new UserContextInterceptor());

        if (useApiGateway) {
            additionalInterceptors.add(new ApiGatewayInterceptor(apiGatewayTokenGenerator));
        } else {
            additionalInterceptors.add(new JwtAuthInterceptor());

        }

        restTemplate = restTemplateBuilder
                .rootUri(apiRootUri)
                .additionalInterceptors(additionalInterceptors)
                .build();
    }

    protected <T> ResponseEntity<T> getWithPagingAndSorting(URI uri, PagingAndSortingDto pagingAndSorting,
                                                            ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                withPagingAndSorting(pagingAndSorting),
                responseType);
    }

    protected <T> ResponseEntity<T> getWithPaging(URI uri, PagingAndSortingDto pagingAndSorting,
                                                  ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                withPaging(pagingAndSorting),
                responseType);
    }

    protected <T> ResponseEntity<T> getWithSorting(URI uri, PagingAndSortingDto pagingAndSorting,
                                                  ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                withSorting(pagingAndSorting),
                responseType);
    }

    protected <T> ResponseEntity<T> getForList(URI uri, ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                null,
                responseType);
    }

    protected HttpEntity<?> withPagingAndSorting(PagingAndSortingDto pagingAndSorting) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());
        headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
        headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());

        return new HttpEntity<>(null, headers);
    }

    protected HttpEntity<?> withPaging(PagingAndSortingDto pagingAndSorting) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

        return new HttpEntity<>(null, headers);
    }

    protected HttpEntity<?> withSorting(PagingAndSortingDto pagingAndSorting) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
        headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());

        return new HttpEntity<>(null, headers);
    }
}
