package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,Long> {

    List<OffenderKeyworker> findByOffenderNo(String offenderNo);

    @Modifying
    @Query("update OffenderKeyworker set" +
            "  active = 'N'," +
            "  expiryDateTime = :expiryDate," +
            "  deallocationReason = :deallocationReason" +
            " where offenderNo = :offenderNo and active = 'Y'")
    int deactivate(@Param("offenderNo") String offenderNo, @Param("deallocationReason")DeallocationReason deallocationReason, @Param("expiryDate")LocalDateTime expiryDate);

    List<OffenderKeyworker> findByStaffId(Long staffId);

    List<OffenderKeyworker> findByStaffIdAndAgencyIdAndActive(Long staffId, String agencyId, boolean active);

    boolean existsByAgencyId(String agencyId);

    List<OffenderKeyworker> findByActiveAndOffenderNoIn(boolean isActive, Collection<String> offenderNo);

    Integer countByStaffIdAndAgencyIdAndActive(Long staffId, String agencyId, boolean active);
}
