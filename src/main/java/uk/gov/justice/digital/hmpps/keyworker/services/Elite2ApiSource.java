package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.RequestEntity;
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

    protected RequestEntity<Void> withPagingAndSorting(PagingAndSortingDto pagingAndSorting, URI uri) {
        return RequestEntity.get(uri)
                .header(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString())
                .header(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString())
                .header(HEADER_SORT_FIELDS, pagingAndSorting.getSortFields())
                .header(HEADER_SORT_ORDER, pagingAndSorting.getSortOrder().name())
                .build();
    }

    protected RequestEntity<Void> withPaging(PagingAndSortingDto pagingAndSorting, URI uri) {
        return RequestEntity.get(uri)
                .header(HEADER_PAGE_OFFSET, pagingAndSorting.getPageOffset().toString())
                .header(HEADER_PAGE_LIMIT, pagingAndSorting.getPageLimit().toString())
                .build();
    }
}
