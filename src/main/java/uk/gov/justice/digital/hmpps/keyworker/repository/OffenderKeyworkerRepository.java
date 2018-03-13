package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.util.List;

public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,Long> {

    List<OffenderKeyworker> findByOffenderNo(String offenderNo);

    List<OffenderKeyworker> findByStaffId(Long staffId);

    boolean existsByAgencyId(String agencyId);

    Integer countByStaffId(Long staffId);
}
