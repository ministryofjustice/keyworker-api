package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.String.format;
import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.*;

/**
 * Helper class that takes care of setting up rest template with base API url and request headers.
 */
// TODO DT-611 Consolidate and rename these methods

// TODO DT-611 Check if we need to put a timeout on all blocks
@Component
public class RestCallHelper {

    private final WebClient webClient;
    private final WebClient oauth2WebClient;

    @Autowired
    public RestCallHelper(@Qualifier(value = "webClient") final WebClient webClient,
                          @Qualifier(value = "oauth2WebClient") final WebClient oauth2WebClient) {
        this.webClient = webClient;
        this.oauth2WebClient = oauth2WebClient;
    }

    <T> ResponseEntity<T> getForListWithAuthentication(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final ParameterizedTypeReference<T> responseType) {
        return getWebClient(true)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    protected <T> T get(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final Class<T> responseType, final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T, E> List<T> post(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final E body, final ParameterizedTypeReference<List<T>> responseType, final boolean admin) {
        return getWebClient(admin)
                .post()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .bodyValue(body)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T, E> List<T> postWithLimit(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final E body, final ParameterizedTypeReference<List<T>> responseType, final int limit, final boolean admin) {
        return getWebClient(admin)
                .post()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .bodyValue(body)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HEADER_PAGE_LIMIT, String.valueOf(limit))
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T> ResponseEntity<T> getWithPagingAndSorting(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables,
                                                            final PagingAndSortingDto pagingAndSorting, final ParameterizedTypeReference<T> responseType, final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(withPagingAndSorting(pagingAndSorting))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    <T> ResponseEntity<T> getWithPaging(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final PagingAndSortingDto pagingAndSorting,
                                                  final ParameterizedTypeReference<T> responseType) {
        return getWebClient(false)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(withPaging(pagingAndSorting))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    <T> List<T> getAllWithSorting(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final String sortFields, final SortOrder sortOrder,
                                            final ParameterizedTypeReference<List<T>> responseType, final boolean admin) {
        final long initialPageSize = Integer.MAX_VALUE;

        final var pagingAndSorting = PagingAndSortingDto.builder()
                .sortFields(sortFields)
                .sortOrder(sortOrder)
                .pageOffset(0L)
                .pageLimit(initialPageSize)
                .build();

        final var response = getWithPagingAndSorting(path, queryParams, uriVariables, pagingAndSorting, responseType, admin);

        return response.getBody() != null ? new ArrayList<>(response.getBody()) : new ArrayList<>();
    }

    <T> T put(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final Class<T> responseType, final boolean admin) {
        return getWebClient(admin)
                .put()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T> ResponseEntity<T> getForList(final String path, final MultiValueMap<String, String> queryParams, final Map<String, String> uriVariables, final ParameterizedTypeReference<T> responseType, final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    private Consumer<HttpHeaders> withPagingAndSorting(PagingAndSortingDto pagingAndSorting) {
        return headers -> {
            headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
            headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());

            if (StringUtils.isNotBlank(pagingAndSorting.getSortFields())) {
                headers.add(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields());
                headers.add(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name());
            }
        };
    }

    private Consumer<HttpHeaders> withPaging(PagingAndSortingDto pagingAndSorting) {
        return headers -> {
            headers.add(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString());
            headers.add(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString());
        };
    }

    private WebClient getWebClient(final boolean admin) {
        return admin ? oauth2WebClient : webClient;
    }
}
