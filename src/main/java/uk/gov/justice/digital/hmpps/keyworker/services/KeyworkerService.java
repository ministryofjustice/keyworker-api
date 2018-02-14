package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KeyworkerService {

    @Autowired
    private OffenderKeyworkerRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${elite2-api.endpoint.url:http://localhost:8080/api/}")
    private String elite2ApiEndpointUrl;

    public List<OffenderKeyworkerDto> getOffendersForKeyworker(String staffUsername) {
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
    }
}
