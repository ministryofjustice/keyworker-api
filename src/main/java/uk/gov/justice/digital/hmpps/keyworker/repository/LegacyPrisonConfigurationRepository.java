package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyPrisonConfiguration;

import java.util.List;
import java.util.Optional;

public interface LegacyPrisonConfigurationRepository extends CrudRepository<LegacyPrisonConfiguration, String> {

    Optional<LegacyPrisonConfiguration> findByPrisonCode(String prisonCode);

    boolean existsByPrisonCode(String prisonId);

    List<LegacyPrisonConfiguration> findAllByEnabledEquals(boolean migrated);
}
