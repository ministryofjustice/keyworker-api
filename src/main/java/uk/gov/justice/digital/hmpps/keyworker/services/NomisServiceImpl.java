package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationHistoryDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.BookingIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersRequest;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageRequest;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderBooking;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonContactDetailDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffUser;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.queryParamsOf;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.uriVariablesOf;

@Component
@Slf4j
public class NomisServiceImpl implements NomisService {

    private static final ParameterizedTypeReference<List<OffenderKeyworkerDto>> PARAM_TYPE_REF_OFFENDER_KEY_WORKER =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<OffenderLocationDto>> OFFENDER_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<PrisonerDetail>> PRISONER_DETAIL_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<CaseNoteUsageDto>> CASE_NOTE_USAGE_DTO_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<CaseNoteUsagePrisonersDto>> CASE_NOTE_USAGE_PRISONERS_DTO_LIST =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> LEGACY_KEYWORKER_ALLOCATIONS =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<AllocationHistoryDto>> ALLOCATION_HISTORY =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<PrisonContactDetailDto>> PRISON_LIST = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<PrisonerIdentifier>> PRISONER_ID_LIST = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<BookingIdentifier>> BOOKING_IDENTIFIER_LIST = new ParameterizedTypeReference<>() {};

    private final RestCallHelper restCallHelper;

    public NomisServiceImpl(final RestCallHelper restCallHelper) {
        this.restCallHelper = restCallHelper;
    }

    @Override
    public Optional<OffenderLocationDto> getOffenderForPrison(final String prisonId, final String offenderNo) {
        log.info("Getting offender in prison {} offender No {}", prisonId, offenderNo);
        final var queryParams = queryParamsOf("query", format("agencyId:eq:'%s'", prisonId), "offenderNo", offenderNo, "iepLevel", "true");
        final var offenders = restCallHelper.getEntity(URI_ACTIVE_OFFENDERS_BY_AGENCY, queryParams, uriVariablesOf(), OFFENDER_LOCATION_DTO_LIST, false).getBody();
        return Optional.ofNullable(offenders.size() > 0 ? offenders.get(0) : null);
    }

    @Override
    public Optional<PrisonerDetail> getPrisonerDetail(final String offenderNo, final boolean admin) {
        log.debug("Getting prisoner details for NOMIS No {}", offenderNo);
        final var uriVariables = uriVariablesOf("offenderNo", offenderNo);
        final var prisonerDetail = restCallHelper.getEntity(URI_PRISONER_LOOKUP + "/{offenderNo}", queryParamsOf(), uriVariables, PRISONER_DETAIL_LIST, admin).getBody();
        return Optional.ofNullable(prisonerDetail.size() > 0 ? prisonerDetail.get(0) : null);
    }

    @Override
    public List<PrisonerDetail> getPrisonerDetails(final List<String> offenderNos, final boolean admin) {
        final var payload = new PrisonerDetailLookup(offenderNos);
        return restCallHelper.postWithLimit(URI_PRISONER_LOOKUP, queryParamsOf(), uriVariablesOf(), payload, PRISONER_DETAIL_LIST, offenderNos.size(), admin);
    }

    @Data
    @AllArgsConstructor
    private static class PrisonerDetailLookup {
        private final List<String> offenderNos;
    }

    @Override
    public ResponseEntity<List<StaffLocationRoleDto>> getActiveStaffKeyWorkersForPrison(final String prisonId, final Optional<String> nameFilter, final PagingAndSortingDto pagingAndSorting, final boolean admin) {
        log.info("Getting KW Staff in prison {}", prisonId);

        final var queryParams = queryParamsOf();
        nameFilter.ifPresent(filter -> queryParams.add("nameFilter", filter));
        final var uriVariables = uriVariablesOf("agencyId", prisonId);

        return restCallHelper.getEntityWithPagingAndSorting(GET_STAFF_IN_SPECIFIC_PRISON, queryParams, uriVariables, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST, admin);
    }

    @Override
    public Optional<StaffLocationRoleDto> getStaffKeyWorkerForPrison(final String prisonId, final Long staffId) {
        log.info("Getting staff in prison {} staff Id {}", prisonId, staffId);

        final var uri = GET_STAFF_IN_SPECIFIC_PRISON;
        log.debug("About to retrieve keyworker from Elite2api using uri {}", uri);
        final var uriVariables = uriVariablesOf("agencyId", prisonId);
        final var queryParams = queryParamsOf("staffId", String.valueOf(staffId), "activeOnly", "false");
        final var staff = restCallHelper.getEntity(uri, queryParams, uriVariables, ELITE_STAFF_LOCATION_DTO_LIST, false).getBody();
        final var staffLocationRoleDto = Optional.ofNullable(staff.size() > 0 ? staff.get(0) : null);
        log.debug("Result: {}", staffLocationRoleDto);
        return staffLocationRoleDto;
    }

    @Override
    public BasicKeyworkerDto getBasicKeyworkerDtoForOffender(final String offenderNo) {
        log.info("Getting KW for offender {}", offenderNo);

        final var uriVariables = uriVariablesOf("offenderNo", offenderNo);
        return restCallHelper.getObject(GET_KEY_WORKER, queryParamsOf(), uriVariables, BasicKeyworkerDto.class, false);
    }

    @Override
    public List<KeyworkerDto> getAvailableKeyworkers(final String prisonId) {
        log.info("Getting available KW in prison {}", prisonId);
        final var uriVariables = uriVariablesOf("agencyId", prisonId);
        return restCallHelper.getEntity(URI_AVAILABLE_KEYWORKERS, queryParamsOf(), uriVariables, KEYWORKER_DTO_LIST, false).getBody();
    }

    @Override
    public List<OffenderLocationDto> getOffendersAtLocation(final String prisonId, final String sortFields, final SortOrder sortOrder, final boolean admin) {
        log.info("Getting offenders in prison {}", prisonId);
        final var queryParams = queryParamsOf("query", format("agencyId:eq:'%s'", prisonId));
        return restCallHelper.getObjectListWithSorting(
                URI_ACTIVE_OFFENDERS_BY_AGENCY, queryParams, uriVariablesOf(), sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                }, admin);
    }

    @Override
    @Cacheable("getBasicKeyworkerDtoForStaffId")
    public StaffLocationRoleDto getBasicKeyworkerDtoForStaffId(final Long staffId) {
        log.debug("Getting basic keyworker details for staffId {} from Elite2api using uri {}", staffId, URI_STAFF);
        final var uriVariables = uriVariablesOf("staffId", String.valueOf(staffId));
        return restCallHelper.getObject(URI_STAFF, queryParamsOf(), uriVariables, StaffLocationRoleDto.class, false);
    }

    @Override
    public List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(final String prisonId, final long offset, final long limit) {
        log.info("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", prisonId, offset, limit);

        final var uriVariables = uriVariablesOf("agencyId", prisonId);
        final var pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return restCallHelper.getEntityWithPaging(URI_KEY_WORKER_GET_ALLOCATION_HISTORY, queryParamsOf(), uriVariables, pagingAndSorting, PARAM_TYPE_REF_OFFENDER_KEY_WORKER).getBody();
    }

    @Override
    @Cacheable("getStaffDetailByUserId")
    public StaffUser getStaffDetailByUserId(final String userId) {
        log.info("Getting staff details for user Id {}", userId);
        final var uri = GET_USER_DETAILS;
        final var uriVariables = uriVariablesOf("username", userId);
        log.debug("About to retrieve staff details from Elite2api using uri {}", uri);

        try {
            final var staffUser = restCallHelper.getObject(uri, queryParamsOf(), uriVariables, StaffUser.class, false);
            log.debug("Result: {}", staffUser);
            return staffUser;
        } catch (final WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                return StaffUser.builder().firstName("User").lastName(userId).username(userId).build();
            }
        }
        return null;
    }

    @Override
    public List<CaseNoteUsageDto> getCaseNoteUsage(final List<Long> staffIds, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final Integer numMonths) {
        log.info("Getting case note details of type {} sub type {}, from {}, to {} for {} months", caseNoteType, caseNoteSubType, fromDate, toDate, numMonths);

        final var body = CaseNoteUsageRequest.builder()
                .staffIds(staffIds)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .numMonths(numMonths)
                .build();

        return restCallHelper.post(CASE_NOTE_USAGE, queryParamsOf(), uriVariablesOf(), body, CASE_NOTE_USAGE_DTO_LIST, false);
    }

    @Override
    public List<CaseNoteUsagePrisonersDto> getCaseNoteUsageForPrisoners(final List<String> offenderNos, final Long staffId, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final boolean admin) {
        log.info("Getting case note details for prisoner list of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);

        final var body = CaseNoteUsagePrisonersRequest.builder()
                .offenderNos(offenderNos)
                .staffId(staffId)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return restCallHelper.post(CASE_NOTE_USAGE_FOR_PRISONERS, queryParamsOf(), uriVariablesOf(), body, CASE_NOTE_USAGE_PRISONERS_DTO_LIST, admin);
    }

    @Override
    public List<CaseNoteUsagePrisonersDto> getCaseNoteUsageByPrison(final String prisonId, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final boolean admin) {
        log.info("Getting case note details for prisoner list of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);

        final var body = CaseNoteUsagePrisonersRequest.builder()
                .agencyId(prisonId)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return restCallHelper.post(CASE_NOTE_USAGE_FOR_PRISONERS, queryParamsOf(), uriVariablesOf(), body, CASE_NOTE_USAGE_PRISONERS_DTO_LIST, admin);
    }

    @Override
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocations(final List<Long> staffIds, final String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by staff IDs", agencyId);

        final var uriVariables = uriVariablesOf("agencyId", agencyId);
        return restCallHelper.post(URI_CURRENT_ALLOCATIONS, queryParamsOf(), uriVariables, staffIds, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocationsByOffenderNos(final List<String> offenderNos, final String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by offender Nos", agencyId);

        final var uriVariables = uriVariablesOf("agencyId", agencyId);
        return restCallHelper.post(URI_CURRENT_ALLOCATIONS_BY_OFFENDERS, queryParamsOf(), uriVariables, offenderNos, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<AllocationHistoryDto> getAllocationHistoryByOffenderNos(final List<String> offenderNos) {
        log.info("Getting Key worker allocations for offender Nos {}", offenderNos);

        return restCallHelper.post(URI_OFFENDERS_ALLOCATION_HISTORY, queryParamsOf(), uriVariablesOf(), offenderNos, ALLOCATION_HISTORY, false);
    }

    @Override
    public List<Prison> getAllPrisons() {
        log.info("Getting all prisons");

        final var prisonListResponse = restCallHelper.getEntity(URI_GET_ALL_PRISONS, queryParamsOf(), uriVariablesOf(), PRISON_LIST, true);
        return prisonListResponse.getBody() != null ?
                prisonListResponse.getBody().stream()
                        .map(p -> Prison.builder().prisonId(p.getAgencyId()).build())
                        .collect(Collectors.toList()) : Collections.emptyList();
    }

    @Override
    public boolean isPrison(final String prisonId) {
        final var uriVariables = uriVariablesOf("agencyId", prisonId);
        final var queryParams = queryParamsOf("activeOnly", "false", "agencyType", "INST");
        final var isAPrison = new AtomicBoolean(false);
        try {
            final var result = restCallHelper.getObject(URI_GET_AGENCY, queryParams, uriVariables, Map.class, true);
            isAPrison.set(result.get("agencyId") != null);
        } catch (final WebClientResponseException e) {
           isAPrison.set(false);
        }
        return isAPrison.get();

    }

    @Override
    public CaseloadUpdate enableNewNomisForCaseload(final String caseload) {
        final var uriVariables = uriVariablesOf("caseload", caseload);
        return restCallHelper.put(URI_ENABLE_USERS_WITH_CASELOAD, queryParamsOf(), uriVariables, CaseloadUpdate.class, true);
    }

    @Override
    public List<PrisonerIdentifier> getIdentifierByTypeAndValue(final String type, final String value) {
        final var uriVariables = uriVariablesOf("type", type, "value", value);
        return restCallHelper.getEntity(URI_IDENTIFIERS, queryParamsOf(), uriVariables, PRISONER_ID_LIST, true).getBody();
    }

    @Override
    public List<BookingIdentifier> getIdentifiersByBookingId(final Long bookingId) {
        final var uriVariables = uriVariablesOf("bookingId", String.valueOf(bookingId));
        return restCallHelper.getEntity(BOOKING_IDENTIFIERS, queryParamsOf(), uriVariables, BOOKING_IDENTIFIER_LIST, true).getBody();
    }

    @Override
    public Optional<OffenderBooking> getBooking(final Long bookingId) {
        final var uriVariables = uriVariablesOf("bookingId", String.valueOf(bookingId));
        final var queryParams = queryParamsOf("basicInfo", "true");
        final var booking = new AtomicReference<Optional<OffenderBooking>>();
        try {
            booking.set(Optional.ofNullable(restCallHelper.getObject(BOOKING_DETAILS, queryParams, uriVariables, OffenderBooking.class, true)));
        } catch (final WebClientResponseException e) {
            booking.set(Optional.empty());
        }
        return booking.get();
    }
}
