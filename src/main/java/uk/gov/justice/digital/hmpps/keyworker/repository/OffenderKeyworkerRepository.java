package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

@Repository
public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,String> {
    OffenderKeyworker findByOffenderKeyworkerId(String offenderKeyworkerId);
}
