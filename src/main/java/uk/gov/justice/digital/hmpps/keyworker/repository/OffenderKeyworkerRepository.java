package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,Long> {
    int deleteByOffenderNo(String offenderNo);

    List<OffenderKeyworker> findByOffenderNo(String offenderNo);
    List<OffenderKeyworker> findByOffenderNoIn(List<String> offenderNos);

    List<OffenderKeyworker> findByOffenderNoAndActiveAndAllocationTypeIsNot(String offenderNo, boolean active, AllocationType type);

    List<OffenderKeyworker> findByStaffId(Long staffId);

    List<OffenderKeyworker> findByStaffIdAndPrisonIdAndActive(Long staffId, String prisonId, boolean active);
    List<OffenderKeyworker> findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(Long staffId, String prisonId, boolean active, AllocationType type);
    List<OffenderKeyworker> findByActiveAndOffenderNoIn(boolean isActive, Collection<String> offenderNo);
    List<OffenderKeyworker> findByActiveAndOffenderNo(boolean isActive, String offenderNo);
    List<OffenderKeyworker> findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(boolean isActive, String prisonId, Collection<String> offenderNo, AllocationType type);
    List<OffenderKeyworker> findByActiveAndPrisonIdAndAllocationTypeIsNot(boolean isActive, String prisonId, AllocationType type);
    List<OffenderKeyworker> findByActiveAndPrisonIdAndAllocationType(boolean isActive, String prisonId, AllocationType type);
    List<OffenderKeyworker> findByActiveAndPrisonId(boolean isActive, String prisonId);
    List<OffenderKeyworker> findByStaffIdAndPrisonId(Long staffId, String prisonId);
    List<OffenderKeyworker> findByPrisonIdAndAssignedDateTimeBetween(String prisonId, LocalDateTime start, LocalDateTime end);

    List<OffenderKeyworker> findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(String prisonId, LocalDateTime beforeTime, Collection<String> offenderNos, AllocationType type);

    Integer countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(Long staffId, String prisonId, boolean active, AllocationType allocationType);

    @Modifying
    @Query("delete from OffenderKeyworker ok where ok.prisonId = :prisonId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer deleteExistingProvisionals(@Param("prisonId") String prisonId);

    @Modifying
    @Query("update OffenderKeyworker ok set ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.AUTO where ok.prisonId = :prisonId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer confirmProvisionals(@Param("prisonId") String prisonId);

}
