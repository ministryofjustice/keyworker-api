package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
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
import uk.gov.justice.digital.hmpps.keyworker.dto.Page;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
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

    protected <T> List<T> getAllWithSorting(URI uri, String sortFields, SortOrder sortOrder,
                                            ParameterizedTypeReference<List<T>> responseType) {
        final long initialPageSize = 50;

        // Initial request (for up to 50 items).
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder()
                .sortFields(sortFields)
                .sortOrder(sortOrder)
                .pageOffset(0L)
                .pageLimit(initialPageSize)
                .build();

        ResponseEntity<List<T>> response = getWithPagingAndSorting(uri, pagingAndSorting, responseType);

        final List<T> items = new ArrayList<>(response.getBody());

        // Inspect response pagination headers to determine total item count
        long totalRecords = extractTotalRecords(response.getHeaders());
        long totalRemaining = totalRecords - items.size();

        // Get remaining items, if necessary
        if (totalRemaining > 0) {
            pagingAndSorting.setPageOffset(initialPageSize);
            pagingAndSorting.setPageLimit(totalRemaining);

            response = getWithPagingAndSorting(uri, pagingAndSorting, responseType);

            items.addAll(response.getBody());
        }

        return items;
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

        if (StringUtils.isNotBlank(pagingAndSorting.getSortFields())) {
            headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
            headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());
        }

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

    private long extractTotalRecords(HttpHeaders headers) {
        long totalRecords = 0;

        String trHeaderValue = headers.getFirst(Page.HEADER_TOTAL_RECORDS);

        if (StringUtils.isNotBlank(trHeaderValue)) {
            totalRecords = Long.parseLong(trHeaderValue);
        }

        return totalRecords;
    }
}
