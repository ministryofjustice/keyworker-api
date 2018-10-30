package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;

import java.util.List;

public interface PrisonSupportedRepository extends CrudRepository<PrisonSupported, String> {

    boolean existsByPrisonId(String prisonId);

    List<PrisonSupported> findAllByMigratedEquals(boolean migrated);
}
