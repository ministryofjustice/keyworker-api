package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
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
    private final OAuth2RestTemplate elite2SystemRestTemplate;

    @Autowired
    public RestCallHelper(@Qualifier(value = "elite2ApiRestTemplate") final RestTemplate restTemplate,
                          final OAuth2RestTemplate elite2SystemRestTemplate) {
        this.restTemplate = restTemplate;
        this.elite2SystemRestTemplate = elite2SystemRestTemplate;
    }

    protected <T> ResponseEntity<T> getForListWithAuthentication(final URI uri, final ParameterizedTypeReference<T> responseType) {
        return getRestTemplate(true).exchange(
                uri.toString(),
                HttpMethod.GET,
                null,
                responseType);
    }

    protected <T> T get(final URI uri, final Class<T> responseType, boolean admin) {
        final var exchange = getRestTemplate(admin).exchange(
                uri.toString(),
                HttpMethod.GET,
                new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON),
                responseType);
        return exchange.getBody();
    }

    protected <T, E> List<T> post(final URI uri, final E body, final ParameterizedTypeReference<List<T>> responseType, final boolean admin) {
        final var exchange = getRestTemplate(admin).exchange(uri.toString(),
                HttpMethod.POST,
                new HttpEntity<E>(body, CONTENT_TYPE_APPLICATION_JSON),
                responseType);
        return exchange.getBody();
    }

    protected <T> ResponseEntity<T> getWithPagingAndSorting(final URI uri, final PagingAndSortingDto pagingAndSorting,
                                                            final ParameterizedTypeReference<T> responseType, final boolean admin) {
        return getRestTemplate(admin).exchange(
                uri.toString(),
                HttpMethod.GET,
                withPagingAndSorting(pagingAndSorting),
                responseType);
    }

    protected <T> ResponseEntity<T> getWithPaging(final URI uri, final PagingAndSortingDto pagingAndSorting,
                                                  final ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                withPaging(pagingAndSorting),
                responseType);
    }

    protected <T> List<T> getAllWithSorting(final URI uri, final String sortFields, final SortOrder sortOrder,
                                            final ParameterizedTypeReference<List<T>> responseType, final boolean admin) {
        final long initialPageSize = Integer.MAX_VALUE;

        final var pagingAndSorting = PagingAndSortingDto.builder()
                .sortFields(sortFields)
                .sortOrder(sortOrder)
                .pageOffset(0L)
                .pageLimit(initialPageSize)
                .build();

        final var response = getWithPagingAndSorting(uri, pagingAndSorting, responseType, admin);

        return response.getBody() != null ? new ArrayList<>(response.getBody()) : new ArrayList<>();
    }

    <T> T put(final URI uri, final Class<T> responseType, final boolean admin) {
        final var exchange = getRestTemplate(admin).exchange(uri.toString(), HttpMethod.PUT, new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON), responseType);
        return exchange.getBody();
    }

    protected <T> ResponseEntity<T> getForList(final URI uri, final ParameterizedTypeReference<T> responseType, boolean admin) {
        return getRestTemplate(admin).exchange(
                uri.toString(),
                HttpMethod.GET,
                null,
                responseType);
    }

    private HttpEntity<?> withPagingAndSorting(final PagingAndSortingDto pagingAndSorting) {
        final var headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

        if (StringUtils.isNotBlank(pagingAndSorting.getSortFields())) {
            headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
            headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());
        }

        return new HttpEntity<>(null, headers);
    }

    private HttpEntity<?> withPaging(final PagingAndSortingDto pagingAndSorting) {
        final var headers = new HttpHeaders();

        headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
        headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

        return new HttpEntity<>(null, headers);
    }

    private static HttpHeaders httpContentTypeHeaders(final MediaType contentType) {
        final var httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    private RestTemplate getRestTemplate(final boolean admin) {
        return admin ? elite2SystemRestTemplate : restTemplate;
    }
}
