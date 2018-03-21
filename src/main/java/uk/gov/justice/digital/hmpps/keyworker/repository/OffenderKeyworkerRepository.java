package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.util.Collection;
import java.util.List;

public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,Long> {

    List<OffenderKeyworker> findByOffenderNo(String offenderNo);

    OffenderKeyworker findByOffenderNoAndActive(String offenderNo, boolean active);

    List<OffenderKeyworker> findByStaffId(Long staffId);

    List<OffenderKeyworker> findByStaffIdAndAgencyIdAndActive(Long staffId, String agencyId, boolean active);

    boolean existsByAgencyId(String agencyId);

    List<OffenderKeyworker> findByActiveAndOffenderNoIn(boolean isActive, Collection<String> offenderNo);
    List<OffenderKeyworker> findByActiveAndOffenderNo(boolean isActive, String offenderNo);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndOffenderNoIn(boolean isActive, String agencyId, Collection<String> offenderNo);
    List<OffenderKeyworker> findByActiveAndAgencyId(boolean isActive, String agencyId);

    Integer countByStaffIdAndAgencyIdAndActive(Long staffId, String agencyId, boolean active);
}
