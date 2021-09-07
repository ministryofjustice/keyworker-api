package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.RestResponsePage;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.HEADER_PAGE_LIMIT;
import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.HEADER_PAGE_OFFSET;
import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.HEADER_SORT_FIELDS;
import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.HEADER_SORT_ORDER;

/**
 * Helper class that takes care of setting up rest template with base API url and request headers.
 */
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

    public <T> ResponseEntity<T> getEntity(final String path,
                                           final MultiValueMap<String, String> queryParams,
                                           final Map<String, String> uriVariables,
                                           final ParameterizedTypeReference<T> responseType,
                                           final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    <T> T getObject(final String path,
                              final MultiValueMap<String, String> queryParams,
                              final Map<String, String> uriVariables,
                              final Class<T> responseType,
                              final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T, E> T post(final String path,
                  final MultiValueMap<String, String> queryParams,
                  final Map<String, String> uriVariables,
                  final E body,
                  final ParameterizedTypeReference<T> responseType,
                  final boolean admin) {
        return getWebClient(admin)
                .post()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .bodyValue(body)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    <T, E> T postWithLimit(final String path,
                           final MultiValueMap<String, String> queryParams,
                           final Map<String, String> uriVariables,
                           final E body,
                           final ParameterizedTypeReference<T> responseType,
                           final int limit,
                           final boolean admin) {
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

    <T> ResponseEntity<T> getEntityWithPagingAndSorting(final String path,
                                                        final MultiValueMap<String, String> queryParams,
                                                        final Map<String, String> uriVariables,
                                                        final PagingAndSortingDto pagingAndSorting,
                                                        final ParameterizedTypeReference<T> responseType,
                                                        final boolean admin) {
        return getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(withPagingAndSorting(pagingAndSorting))
                .retrieve()
                .toEntity(responseType)
                .block();
    }

    <T> ResponseEntity<T> getEntityWithPaging(final String path,
                                              final MultiValueMap<String, String> queryParams,
                                              final Map<String, String> uriVariables,
                                              final PagingAndSortingDto pagingAndSorting,
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

    <T> RestResponsePage<T> getPageWithSorting(final String path,
                                         final MultiValueMap<String, String> queryParams,
                                         final ParameterizedTypeReference<RestResponsePage<T>> responseType,
                                         final boolean admin) {

        return Optional.ofNullable(getWebClient(admin)
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .toEntity(responseType)
                .block())
                .map(HttpEntity::getBody).orElse(new RestResponsePage<>());
    }

    public void delete(final String path,
                       final MultiValueMap<String, String> queryParams,
                       final Map<String, String> uriVariables,
                       final boolean admin) {
        getWebClient(admin)
                .delete()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .exchange()
                .block();
    }

    public <T> T put(final String path,
                     final MultiValueMap<String, String> queryParams,
                     final Map<String, String> uriVariables,
                     final Class<T> responseType,
                     final boolean admin) {
        return getWebClient(admin)
                .put()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(queryParams).build(uriVariables))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(responseType)
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
