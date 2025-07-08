package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LegacyKeyworkerAllocationRepository extends JpaRepository<LegacyKeyworkerAllocation, UUID> {
    List<LegacyKeyworkerAllocation> findByPersonIdentifier(String offenderNo);
    List<LegacyKeyworkerAllocation> findByPersonIdentifierIn(List<String> offenderNos);

    List<LegacyKeyworkerAllocation> findByPersonIdentifierAndActiveAndAllocationTypeIsNot(String offenderNo, boolean active, AllocationType type);

    List<LegacyKeyworkerAllocation> findByStaffId(Long staffId);

    List<LegacyKeyworkerAllocation> findByStaffIdAndPrisonCodeAndActive(Long staffId, String prisonId, boolean active);
    List<LegacyKeyworkerAllocation> findByStaffIdAndPrisonCodeAndActiveAndAllocationTypeIsNot(Long staffId, String prisonId, boolean active, AllocationType type);
    List<LegacyKeyworkerAllocation> findByActiveAndPersonIdentifierIn(boolean isActive, Collection<String> offenderNo);
    List<LegacyKeyworkerAllocation> findByActiveAndPersonIdentifier(boolean isActive, String offenderNo);
    List<LegacyKeyworkerAllocation> findByActiveAndPrisonCodeAndPersonIdentifierInAndAllocationTypeIsNot(boolean isActive, String prisonId, Collection<String> offenderNo, AllocationType type);
    List<LegacyKeyworkerAllocation> findByActiveAndPrisonCodeAndAllocationTypeIsNot(boolean isActive, String prisonId, AllocationType type);
    List<LegacyKeyworkerAllocation> findByActiveAndPrisonCodeAndAllocationType(boolean isActive, String prisonId, AllocationType type);
    List<LegacyKeyworkerAllocation> findByActiveAndPrisonCode(boolean isActive, String prisonId);
    List<LegacyKeyworkerAllocation> findByStaffIdAndPrisonCode(Long staffId, String prisonId);

    Integer countByStaffIdAndPrisonCodeAndActiveAndAllocationTypeIsNot(Long staffId, String prisonId, boolean active, AllocationType allocationType);

    @Modifying
    @Query("delete from LegacyKeyworkerAllocation ok where ok.prisonCode = :prisonId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer deleteExistingProvisionals(@Param("prisonId") String prisonId);

    @Modifying
    @Query("update LegacyKeyworkerAllocation ok set ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.AUTO where ok.prisonCode = :prisonId and ok.allocationType = uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL")
    Integer confirmProvisionals(@Param("prisonId") String prisonId);

    Integer countByActiveAndPersonIdentifier(boolean isActive, String prisonNumber);

}
