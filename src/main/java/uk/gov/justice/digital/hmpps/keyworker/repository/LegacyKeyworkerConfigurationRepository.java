package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerConfiguration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LegacyKeyworkerConfigurationRepository extends CrudRepository<LegacyKeyworkerConfiguration,Long> {

    List<LegacyKeyworkerConfiguration> findByStatusKeyCodeAndReactivateOnBefore(String statusCode, LocalDate date);
    Optional<LegacyKeyworkerConfiguration> findByStaffId(Long staffId);
}
