package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class KeyworkerService {

    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> KEYWOKER_ALLOCATION_LIST = new ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>>() {};
    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST = new ParameterizedTypeReference<List<KeyworkerDto>>() {};
    private static final ParameterizedTypeReference<List<OffenderSummaryDto>> OFFENDER_SUMMARY_DTO_LIST = new ParameterizedTypeReference<List<OffenderSummaryDto>>() {};

    private static final HttpHeaders CONTENT_TYPE_APPLICATION_JSON = httpContentTypeHeaders(MediaType.APPLICATION_JSON);

//    @Autowired
//    private OffenderKeyworkerRepository repository;

    @Value("${elite2-api.endpoint.url}")
    private String elite2ApiEndpointUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public KeyworkerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static HttpHeaders httpContentTypeHeaders(MediaType contentType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    public List<KeyworkerDto> getAvailableKeyworkers(String agencyId) {

        ResponseEntity<List<KeyworkerDto>> responseEntity = restTemplate.exchange(
                "{endpointUri}/key-worker/{agencyId}/available",
                HttpMethod.GET,
                null,
                KEYWORKER_DTO_LIST,
                elite2ApiEndpointUrl,
                agencyId);

        return responseEntity.getBody();
    }

    public List<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        URI uri = new UriTemplate("{endpointUri}/key-worker/{agencyId}/allocations")
                .expand(elite2ApiEndpointUrl, allocationFilter.getAgencyId());

        RequestEntity requestEntity = withPagingAndSorting(
                pagingAndSorting,
                withAllocationFilterParameters(allocationFilter, uri));

        return restTemplate.exchange(requestEntity, KEYWOKER_ALLOCATION_LIST).getBody();
    }

    public List<OffenderSummaryDto> getUnallocatedOffenders(String agencyId, PagingAndSortingDto pagingAndSorting) {

        URI uri = new UriTemplate("{endpointUri}/key-worker/{agencyId}/offenders/unallocated")
                .expand(elite2ApiEndpointUrl, agencyId);

        RequestEntity requestEntity = withPagingAndSorting(pagingAndSorting, uri);

        return restTemplate.exchange(requestEntity, OFFENDER_SUMMARY_DTO_LIST).getBody();
    }

    public KeyworkerDto getKeyworkerDetails(String staffId) {

        return restTemplate.getForObject(
                "{endpointUri}/key-worker/{staffId}",
                KeyworkerDto.class,
                elite2ApiEndpointUrl,
                staffId);
    }

    public String startAutoAllocation(String agencyId) {

        return restTemplate
            .exchange(
                RequestEntity.post(
                        new UriTemplate("{endpointUri}/key-worker/{agencyId}/allocate/start")
                            .expand(elite2ApiEndpointUrl, agencyId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .build(),
                String.class)
            .getBody();
    }

    @PreAuthorize("#oauth2.hasScope('write')")
    public void allocate(KeyworkerAllocationDto keyworkerAllocation) {

        restTemplate.postForObject(
                "{endpointUri}/key-worker/allocate",
                new HttpEntity<>(keyworkerAllocation, CONTENT_TYPE_APPLICATION_JSON),
                Void.class,
                elite2ApiEndpointUrl);
   }

    private RequestEntity<Void> withPagingAndSorting(PagingAndSortingDto pagingAndSorting, URI uri) {

        return RequestEntity.get(uri)
            .header("Page-Offset", pagingAndSorting.getPageOffset().toString())
            .header("Page-Limit", pagingAndSorting.getPageLimit().toString())
            .header("Sort-Fields", pagingAndSorting.getSortFields())
            .header("Sort-Order", pagingAndSorting.getSortOrder().name())
        .build();
    }

    private URI withAllocationFilterParameters(AllocationsFilterDto allocationRequest, URI uri) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
        allocationRequest.getAllocationType().ifPresent(at -> builder.queryParam("allocationType", at.name()));
        allocationRequest.getFromDate().ifPresent(fd -> builder.queryParam("fromDate", fd.format(DateTimeFormatter.ISO_DATE)));
        builder.queryParam("toDate", allocationRequest.getToDate().format(DateTimeFormatter.ISO_DATE));

        return builder.build().toUri();
    }


/*    public List<OffenderKeyworkerDto> getOffendersForKeyworker(String staffUsername) {
        final List<OffenderKeyworker> offenderKeyworkers = repository.findAllByStaffUsername(staffUsername);

        List<OffenderKeyworkerDto> keyworkerDtos = new ArrayList<>();

        if (offenderKeyworkers != null) {
            offenderKeyworkers.forEach(offenderKeyworker -> {
                HttpHeaders headers = new HttpHeaders();
                String lastName = null, firstName = null, nomisId = null;
                try {
                    ResponseEntity<Map> offenderBooking = restTemplate.exchange(
                            elite2ApiEndpointUrl + "bookings/" + offenderKeyworker.getOffenderBookingId(),
                            HttpMethod.GET,
                            new HttpEntity<>(null, headers),
                            new ParameterizedTypeReference<Map>() {
                            });
                    lastName = offenderBooking.getBody().get("lastName").toString();
                    firstName = offenderBooking.getBody().get("firstName").toString();
                    nomisId = offenderBooking.getBody().get("offenderNo").toString();
                } catch (RestClientException e) {
                    // its fine
                }
                keyworkerDtos.add(OffenderKeyworkerDto.builder()
                        .offenderBookingId(offenderKeyworker.getOffenderBookingId())
                        .offenderKeyworkerId(offenderKeyworker.getOffenderKeyworkerId())
                        .staffUsername(offenderKeyworker.getStaffUsername())
                        .assignedDateTime(offenderKeyworker.getAssignedDateTime())
                        .offenderLastName(lastName)
                        .offenderFirstName(firstName)
                        .nomisId(nomisId)
                        .build());
            });

        }

        return keyworkerDtos;
    }*/
}
