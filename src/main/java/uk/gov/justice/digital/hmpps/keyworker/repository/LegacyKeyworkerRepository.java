package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import java.time.LocalDate;
import java.util.List;

public interface LegacyKeyworkerRepository extends CrudRepository<LegacyKeyworker,Long> {

    List<LegacyKeyworker> findByStatusAndActiveDateBefore(KeyworkerStatus status, LocalDate date);
}
