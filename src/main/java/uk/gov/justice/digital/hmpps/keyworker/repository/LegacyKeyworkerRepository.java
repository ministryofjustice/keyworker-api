package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerConfig;

import java.time.LocalDate;
import java.util.List;

public interface LegacyKeyworkerRepository extends CrudRepository<LegacyKeyworkerConfig,Long> {

    List<LegacyKeyworkerConfig> findByStatusKeyCodeAndReactivateOnBefore(String statusCode, LocalDate date);
}
