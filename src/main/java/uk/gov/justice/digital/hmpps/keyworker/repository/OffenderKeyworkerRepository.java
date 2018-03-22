package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
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
    //List<OffenderKeyworker> findByAllocationTypeAndOffenderNoAndStaffId(AllocationType type, String offenderNo, Long staffId);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndOffenderNoIn(boolean isActive, String agencyId, Collection<String> offenderNo);
    List<OffenderKeyworker> findByActiveAndAgencyId(boolean isActive, String agencyId);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndAllocationType(boolean isActive, String agencyId, AllocationType type);

    Integer countByStaffIdAndAgencyIdAndActive(Long staffId, String agencyId, boolean active);

    @Modifying
    @Query("delete from OffenderKeyworker ok where ok.agencyId = :agencyId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer deleteExistingProvisionals(@Param("agencyId") String agencyId);

    @Modifying
    @Query("update OffenderKeyworker ok set ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.AUTO where ok.agencyId = :agencyId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer confirmProvisionals(@Param("agencyId") String agencyId);
}
