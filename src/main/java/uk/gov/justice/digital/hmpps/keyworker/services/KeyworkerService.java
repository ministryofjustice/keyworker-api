package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class KeyworkerService {

    @Autowired
    private OffenderKeyworkerRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${elite2-api.endpoint.url:http://localhost:8080/api/}")
    private String elite2ApiEndpointUrl;

    public OffenderKeyworkerDto getOffenderKeyworker(String offenderKeyworkerId) {
        final OffenderKeyworker byOffenderKeyworkerId = repository.findByOffenderKeyworkerId(offenderKeyworkerId);

        OffenderKeyworkerDto keyworkerDto = null;

        if (byOffenderKeyworkerId != null) {
            HttpHeaders headers = new HttpHeaders();

            ResponseEntity<Map> offenderBooking = restTemplate.exchange(
                    elite2ApiEndpointUrl + "bookings/" + byOffenderKeyworkerId.getOffenderBookingId(),
                    HttpMethod.GET,
                    new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<Map>() {
                    });
            keyworkerDto = OffenderKeyworkerDto.builder()
                    .offenderBookingId(byOffenderKeyworkerId.getOffenderBookingId())
                    .offenderKeyworkerId(byOffenderKeyworkerId.getOffenderKeyworkerId())
                    .officerId(byOffenderKeyworkerId.getOfficerId())
                    .assignedDateTime(byOffenderKeyworkerId.getAssignedDateTime())
                    .lastName(offenderBooking.getBody().get("lastName").toString())
                    .build();

        }

        return keyworkerDto;
    }

    public void saveOffenderKeyworker(OffenderKeyworker offenderKeyworker){
        offenderKeyworker.setOffenderKeyworkerId( UUID.randomUUID().toString());

        repository.save(offenderKeyworker);

    }

    public void updateOffenderKeyworker(OffenderKeyworker offenderKeyworker){
        repository.save(offenderKeyworker);
    }

    public void deleteOffenderKeyworker(OffenderKeyworker offenderKeyworker){
        repository.delete( offenderKeyworker.getOffenderKeyworkerId());
    }
}
