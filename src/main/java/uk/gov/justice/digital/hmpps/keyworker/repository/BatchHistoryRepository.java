package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.BatchHistory;

public interface BatchHistoryRepository extends CrudRepository<BatchHistory, Long> {

    BatchHistory findByName(String name);
}
