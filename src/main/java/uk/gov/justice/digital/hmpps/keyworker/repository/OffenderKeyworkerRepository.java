package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.util.List;

@Repository
public interface OffenderKeyworkerRepository extends CrudRepository<OffenderKeyworker,String> {
    List<OffenderKeyworker> findAllByStaffUsername(String staffUsername);

    OffenderKeyworker findByOffenderBookingId(Long bookingId);

}
