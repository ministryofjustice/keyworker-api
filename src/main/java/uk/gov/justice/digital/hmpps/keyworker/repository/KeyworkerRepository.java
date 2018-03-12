package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;

public interface KeyworkerRepository extends CrudRepository<Keyworker,Long> {
}
