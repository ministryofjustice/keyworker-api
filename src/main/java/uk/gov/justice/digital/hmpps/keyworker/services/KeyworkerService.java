package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;

import java.util.Collections;
import java.util.List;

@Service
public class KeyworkerService {
    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST = new ParameterizedTypeReference<List<KeyworkerDto>>() {
    };
    private static final ParameterizedTypeReference<List<KeyworkerAllocationDto>> KEYWOKER_ALLOCATION_LIST = new ParameterizedTypeReference<List<KeyworkerAllocationDto>>() {
    };

//    @Autowired
//    private OffenderKeyworkerRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${elite2-api.endpoint.url}")
    private String elite2ApiEndpointUrl;


    public List<OffenderKeyworkerDto> getOffendersForKeyworker(String staffUsername) {
        return Collections.emptyList();
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

    public List<KeyworkerAllocationDto> getKeyworkerAllocations(AllocationsRequestDto allocationRequest, PageDto page) {

        ResponseEntity<List<KeyworkerAllocationDto>> responseEntity = restTemplate.exchange(
                "{endpointUri}/key-worker/{agencyId}/allocations",
                HttpMethod.GET,
                null,
                KEYWOKER_ALLOCATION_LIST,
                elite2ApiEndpointUrl,
                allocationRequest.getAgencyId()
        );

        return responseEntity.getBody();
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
