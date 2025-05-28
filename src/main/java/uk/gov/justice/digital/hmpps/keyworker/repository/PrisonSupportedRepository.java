package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;

import java.util.List;
import java.util.Optional;

public interface PrisonSupportedRepository extends CrudRepository<PrisonSupported, String> {

    Optional<PrisonSupported> findByPrisonCode(String prisonCode);

    boolean existsByPrisonCode(String prisonId);

    List<PrisonSupported> findAllByEnabledEquals(boolean migrated);
}
