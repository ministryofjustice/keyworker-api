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
    List<OffenderKeyworker> findByStaffIdAndAgencyIdAndActiveAndAllocationTypeIsNot(Long staffId, String agencyId, boolean active, AllocationType type);

    boolean existsByAgencyId(String agencyId);

    List<OffenderKeyworker> findByActiveAndOffenderNoIn(boolean isActive, Collection<String> offenderNo);
    List<OffenderKeyworker> findByActiveAndOffenderNo(boolean isActive, String offenderNo);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndOffenderNoInAndAllocationTypeIsNot(boolean isActive, String agencyId, Collection<String> offenderNo, AllocationType type);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndAllocationTypeIsNot(boolean isActive, String agencyId, AllocationType type);
    List<OffenderKeyworker> findByActiveAndAgencyIdAndAllocationType(boolean isActive, String agencyId, AllocationType type);

    Integer countByStaffIdAndAgencyIdAndActiveAndAllocationTypeIsNot(Long staffId, String agencyId, boolean active, AllocationType allocationType);

    @Modifying
    @Query("delete from OffenderKeyworker ok where ok.agencyId = :agencyId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer deleteExistingProvisionals(@Param("agencyId") String agencyId);

    @Modifying
    @Query("update OffenderKeyworker ok set ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.AUTO where ok.agencyId = :agencyId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer confirmProvisionals(@Param("agencyId") String agencyId);
}
