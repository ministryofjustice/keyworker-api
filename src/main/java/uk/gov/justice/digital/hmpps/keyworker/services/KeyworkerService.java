package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class KeyworkerService extends Elite2ApiSource {

    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> KEYWORKER_ALLOCATION_LIST = new ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>>() {};
    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST = new ParameterizedTypeReference<List<KeyworkerDto>>() {};
    private static final ParameterizedTypeReference<List<OffenderSummaryDto>> OFFENDER_SUMMARY_DTO_LIST = new ParameterizedTypeReference<List<OffenderSummaryDto>>() {};

    private static final HttpHeaders CONTENT_TYPE_APPLICATION_JSON = httpContentTypeHeaders(MediaType.APPLICATION_JSON);

    private static HttpHeaders httpContentTypeHeaders(MediaType contentType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    public List<KeyworkerDto> getAvailableKeyworkers(String agencyId) {

        ResponseEntity<List<KeyworkerDto>> responseEntity = restTemplate.exchange(
                "/key-worker/{agencyId}/available",
                HttpMethod.GET,
                null,
                KEYWORKER_DTO_LIST,
                agencyId);

        return responseEntity.getBody();
    }

    public List<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/allocations")
                .expand(allocationFilter.getAgencyId());

        RequestEntity requestEntity = withPagingAndSorting(
                pagingAndSorting,
                withAllocationFilterParameters(allocationFilter, uri));

        return restTemplate.exchange(requestEntity, KEYWORKER_ALLOCATION_LIST).getBody();
    }

    public List<OffenderSummaryDto> getUnallocatedOffenders(String agencyId, PagingAndSortingDto pagingAndSorting) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/offenders/unallocated")
                .expand(agencyId);

        RequestEntity requestEntity = withPagingAndSorting(pagingAndSorting, uri);

        return restTemplate.exchange(requestEntity, OFFENDER_SUMMARY_DTO_LIST).getBody();
    }

    public KeyworkerDto getKeyworkerDetails(String staffId) {

        return restTemplate.getForObject(
                "/key-worker/{staffId}",
                KeyworkerDto.class,
                staffId);
    }

    public String startAutoAllocation(String agencyId) {

        return restTemplate
            .exchange(
                RequestEntity.post(
                        new UriTemplate("/key-worker/{agencyId}/allocate/start")
                            .expand(agencyId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .build(),
                String.class)
            .getBody();
    }

    @PreAuthorize("#oauth2.hasScope('write')")
    public void allocate(KeyworkerAllocationDto keyworkerAllocation) {

        restTemplate.postForObject(
                "/key-worker/allocate",
                new HttpEntity<>(keyworkerAllocation, CONTENT_TYPE_APPLICATION_JSON),
                Void.class);
    }

    private URI withAllocationFilterParameters(AllocationsFilterDto allocationRequest, URI uri) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
        allocationRequest.getAllocationType().ifPresent(at -> builder.queryParam("allocationType", at.name()));
        allocationRequest.getFromDate().ifPresent(fd -> builder.queryParam("fromDate", fd.format(DateTimeFormatter.ISO_DATE)));
        builder.queryParam("toDate", allocationRequest.getToDate().format(DateTimeFormatter.ISO_DATE));

        return builder.build().toUri();
    }
}
