package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.UUID;

@Service
public class KeyworkerService {

    @Autowired
    private OffenderKeyworkerRepository repository;

    public OffenderKeyworker getOffenderKeyworker(String offenderKeyworkerId) {
        return repository.findByOffenderKeyworkerId(offenderKeyworkerId);
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
