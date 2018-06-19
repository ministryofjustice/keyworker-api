package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDate;
import java.util.List;

public interface KeyworkerRepository extends CrudRepository<Keyworker,Long> {

    List<Keyworker> findByStatusAndActiveDateBefore(KeyworkerStatus status, LocalDate date);
}
