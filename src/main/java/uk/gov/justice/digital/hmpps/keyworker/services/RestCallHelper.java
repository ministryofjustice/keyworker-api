package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.*;

/**
 * Helper class that takes care of setting up rest template with base API url and request headers.
 */
@Component
public class RestCallHelper {

    private static final HttpHeaders CONTENT_TYPE_APPLICATION_JSON = httpContentTypeHeaders(MediaType.APPLICATION_JSON);

    private final RestTemplate restTemplate;

    @Autowired
    public RestCallHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    protected <T> T get(URI uri, Class<T> responseType) {
        ResponseEntity<T> exchange = restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON),
                responseType);
        return exchange.getBody();
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

    protected <T> List<T> getAllWithSorting(URI uri, String sortFields, SortOrder sortOrder,
                                            ParameterizedTypeReference<List<T>> responseType) {
        final long initialPageSize = Integer.MAX_VALUE;

        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder()
                .sortFields(sortFields)
                .sortOrder(sortOrder)
                .pageOffset(0L)
                .pageLimit(initialPageSize)
                .build();

        ResponseEntity<List<T>> response = getWithPagingAndSorting(uri, pagingAndSorting, responseType);

        return new ArrayList<>(response.getBody());
    }

    protected <T> ResponseEntity<T> getForList(URI uri, ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                null,
                responseType);
    }

    private HttpEntity<?> withPagingAndSorting(PagingAndSortingDto pagingAndSorting) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

        if (StringUtils.isNotBlank(pagingAndSorting.getSortFields())) {
            headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
            headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());
        }

        return new HttpEntity<>(null, headers);
    }

    private HttpEntity<?> withPaging(PagingAndSortingDto pagingAndSorting) {
        HttpHeaders headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

        return new HttpEntity<>(null, headers);
    }

    private static HttpHeaders httpContentTypeHeaders(MediaType contentType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }
}
