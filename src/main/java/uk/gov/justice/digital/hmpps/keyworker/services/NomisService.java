package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.http.ResponseEntity;
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationHistoryDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.BookingIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderBooking;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NomisService {
    String URI_ACTIVE_OFFENDERS_BY_PRISON = "/bookings/v2";
    String URI_STAFF = "/staff/{staffId}";
    String GET_USER_DETAILS = "/users/{username}";
    String URI_AVAILABLE_KEYWORKERS = "/key-worker/{agencyId}/available";
    String URI_KEY_WORKER_GET_ALLOCATION_HISTORY = "/key-worker/{agencyId}/allocationHistory";
    String GET_STAFF_IN_SPECIFIC_PRISON = "/staff/roles/{agencyId}/role/KW";
    String CASE_NOTE_USAGE = "/case-notes/staff-usage";
    String CASE_NOTE_USAGE_FOR_PRISONERS = "/case-notes/usage";
    String URI_PRISONER_LOOKUP = "/prisoners";
    String URI_CURRENT_ALLOCATIONS = "/key-worker/{agencyId}/current-allocations";
    String URI_CURRENT_ALLOCATIONS_BY_OFFENDERS = "/key-worker/{agencyId}/current-allocations/offenders";
    String URI_OFFENDERS_ALLOCATION_HISTORY = "/key-worker/offenders/allocationHistory";
    String URI_GET_ALL_PRISONS = "/agencies/prison";
    String URI_GET_AGENCY = "/agencies/{agencyId}";
    String URI_ENABLE_USERS_WITH_CASELOAD = "/users/add/default/{caseload}";
    String URI_IDENTIFIERS = "/identifiers/{type}/{value}";
    String BOOKING_DETAILS = "/bookings/{bookingId}";
    String BOOKING_IDENTIFIERS = "/bookings/{bookingId}/identifiers";
    String GET_KEY_WORKER = "/bookings/offenderNo/{offenderNo}/key-worker";

    Optional<OffenderLocationDto> getOffenderForPrison(String prisonId, String offenderNo);

    Optional<PrisonerDetail> getPrisonerDetail(String offenderNo, boolean admin);

    List<PrisonerDetail> getPrisonerDetails(List<String> offenderNos, boolean admin);

    ResponseEntity<List<StaffLocationRoleDto>> getActiveStaffKeyWorkersForPrison(String prisonId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting, boolean admin);

    Optional<StaffLocationRoleDto> getStaffKeyWorkerForPrison(String prisonId, Long staffId);

    BasicKeyworkerDto getBasicKeyworkerDtoForOffender(String offenderNo);

    List<KeyworkerDto> getAvailableKeyworkers(String prisonId);

    List<OffenderLocationDto> getOffendersAtLocation(String prisonId, String sortFields, SortOrder sortOrder, boolean admin);

    StaffLocationRoleDto getBasicKeyworkerDtoForStaffId(Long staffId);

    List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String prisonId, long offset, long limit);

    StaffUser getStaffDetailByUserId(String userId);

    List<CaseNoteUsageDto> getCaseNoteUsage(List<Long> staffIds, String caseNoteType, String caseNoteSubType, LocalDate fromDate, LocalDate toDate, Integer numMonths);

    List<CaseNoteUsagePrisonersDto> getCaseNoteUsageForPrisoners(List<String> offenderNos, Long staffId, String caseNoteType, String caseNoteSubType, LocalDate fromDate, LocalDate toDate);

    List<CaseNoteUsagePrisonersDto> getCaseNoteUsageByPrison(final String prisonId, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate);

    List<KeyworkerAllocationDetailsDto> getCurrentAllocations(List<Long> staffIds, String agencyId);

    List<KeyworkerAllocationDetailsDto> getCurrentAllocationsByOffenderNos(List<String> offenderNos, String agencyId);

    List<AllocationHistoryDto> getAllocationHistoryByOffenderNos(List<String> offenderNos);

    List<Prison> getAllPrisons();

    boolean isPrison(String prisonId);

    CaseloadUpdate enableNewNomisForCaseload(String caseload);

    List<PrisonerIdentifier> getIdentifierByTypeAndValue(String type, String value);

    List<BookingIdentifier> getIdentifiersByBookingId(Long bookingId);

    Optional<OffenderBooking> getBooking(Long bookingId);
}
