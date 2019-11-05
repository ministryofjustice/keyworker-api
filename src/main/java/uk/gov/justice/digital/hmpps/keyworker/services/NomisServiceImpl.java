package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


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

    private static final ParameterizedTypeReference<List<PrisonerCustodyStatusDto>> PRISONER_STATUS_DTO_LIST =
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
    public List<PrisonerCustodyStatusDto> getPrisonerStatuses(final LocalDateTime threshold, final LocalDate movementDate) {
        final var uri = new UriTemplate(URI_MOVEMENTS).expand(threshold, movementDate);

        return restCallHelper.getForListWithAuthentication(uri, PRISONER_STATUS_DTO_LIST).getBody();
    }

    @Override
    public Optional<OffenderLocationDto> getOffenderForPrison(final String prisonId, final String offenderNo) {
        log.info("Getting offender in prison {} offender No {}", prisonId, offenderNo);
        final var uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(prisonId, offenderNo);

        final var offenders = restCallHelper.getForList(uri, OFFENDER_LOCATION_DTO_LIST, false).getBody();
        return Optional.ofNullable(offenders.size() > 0 ? offenders.get(0) : null);
    }

    @Override
    public Optional<PrisonerDetail> getPrisonerDetail(final String offenderNo, boolean admin) {
        log.info("Getting prisoner details for NOMIS No {}", offenderNo);
        final var uri = new UriTemplate(URI_PRISONER_LOOKUP).expand(offenderNo);

        final var prisonerDetail = restCallHelper.getForList(uri, PRISONER_DETAIL_LIST, admin).getBody();
        return Optional.ofNullable(prisonerDetail.size() > 0 ? prisonerDetail.get(0) : null);
    }

    @Override
    public ResponseEntity<List<StaffLocationRoleDto>> getActiveStaffKeyWorkersForPrison(final String prisonId, final Optional<String> nameFilter, final PagingAndSortingDto pagingAndSorting, final boolean admin) {
        log.info("Getting KW Staff in prison {}", prisonId);

        final var uriBuilder = UriComponentsBuilder.fromUriString(GET_STAFF_IN_SPECIFIC_PRISON);
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        final var uri = uriBuilder.buildAndExpand(prisonId).toUri();

        return restCallHelper.getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST, admin);
    }

    @Override
    public Optional<StaffLocationRoleDto> getStaffKeyWorkerForPrison(final String prisonId, final Long staffId) {
        log.info("Getting staff in prison {} staff Id {}", prisonId, staffId);

        final var uri = new UriTemplate(GET_STAFF_IN_SPECIFIC_PRISON + "?staffId={staffId}&activeOnly=false").expand(prisonId, staffId);
        log.debug("About to retrieve keyworker from Elite2api using uri {}", uri.toString());

        final var staff = restCallHelper.getForList(uri, ELITE_STAFF_LOCATION_DTO_LIST, false).getBody();
        final var staffLocationRoleDto = Optional.ofNullable(staff.size() > 0 ? staff.get(0) : null);
        log.debug("Result: {}", staffLocationRoleDto);
        return staffLocationRoleDto;
    }

    @Override
    public BasicKeyworkerDto getBasicKeyworkerDtoForOffender(final String offenderNo) {
        log.info("Getting KW for offender", offenderNo);

        final var uri = new UriTemplate(GET_KEY_WORKER).expand(offenderNo);
        return restCallHelper.get(uri, BasicKeyworkerDto.class, false);
    }

    @Override
    public List<KeyworkerDto> getAvailableKeyworkers(final String prisonId) {
        log.info("Getting available KW in prison {}", prisonId);
        final var uri = new UriTemplate(URI_AVAILABLE_KEYWORKERS).expand(prisonId);
        return restCallHelper.getForList(uri, KEYWORKER_DTO_LIST, false).getBody();
    }

    @Override
    public List<OffenderLocationDto> getOffendersAtLocation(final String prisonId, final String sortFields, final SortOrder sortOrder, final boolean admin) {
        log.info("Getting offenders in prison {}", prisonId);
        final var uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId);

        return restCallHelper.getAllWithSorting(
                uri, sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                }, admin);
    }

    @Override
    @Cacheable("getBasicKeyworkerDtoForStaffId")
    public StaffLocationRoleDto getBasicKeyworkerDtoForStaffId(final Long staffId) {
        final var uri = new UriTemplate(URI_STAFF).expand(staffId);
        log.debug("Getting basic keyworker details for staffId {} from Elite2api using uri {}", staffId, uri.toString());
        return restCallHelper.get(uri, StaffLocationRoleDto.class, false);
    }

    @Override
    public List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(final String prisonId, final long offset, final long limit) {
        log.info("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", prisonId, offset, limit);

        final var uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId);
        final var pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return restCallHelper.getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFFENDER_KEY_WORKER).getBody();
    }

    @Override
    @Cacheable("getStaffDetailByUserId")
    public StaffUser getStaffDetailByUserId(final String userId) {
        log.info("Getting staff details for user Id {}", userId);
        final var uri = new UriTemplate(GET_USER_DETAILS).expand(userId);
        log.debug("About to retrieve staff details from Elite2api using uri {}", uri.toString());

        try {
            final var staffUser = restCallHelper.get(uri, StaffUser.class, false);
            log.debug("Result: {}", staffUser);
            return staffUser;
        } catch (final HttpClientErrorException e) {
            if (e.getStatusCode().is4xxClientError()) {
                return StaffUser.builder().firstName("User").lastName(userId).username(userId).build();
            }
        }
        return null;
    }

    @Override
    public List<CaseNoteUsageDto> getCaseNoteUsage(final List<Long> staffIds, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final Integer numMonths) {
        log.info("Getting case note details of type {} sub type {}, from {}, to {} for {} months", caseNoteType, caseNoteSubType, fromDate, toDate);
        final var uri = new UriTemplate(CASE_NOTE_USAGE).expand();

        final var body = CaseNoteUsageRequest.builder()
                .staffIds(staffIds)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .numMonths(numMonths)
                .build();

        return restCallHelper.post(uri, body, CASE_NOTE_USAGE_DTO_LIST, false);
    }

    @Override
    public List<CaseNoteUsagePrisonersDto> getCaseNoteUsageForPrisoners(final List<String> offenderNos, final Long staffId, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final boolean admin) {
        log.info("Getting case note details for prisoner list of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);
        final var uri = new UriTemplate(CASE_NOTE_USAGE_FOR_PRISONERS).expand();

        final var body = CaseNoteUsagePrisonersRequest.builder()
                .offenderNos(offenderNos)
                .staffId(staffId)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return restCallHelper.post(uri, body, CASE_NOTE_USAGE_PRISONERS_DTO_LIST, admin);
    }

    @Override
    public List<CaseNoteUsagePrisonersDto> getCaseNoteUsageByPrison(final String prisonId, final String caseNoteType, final String caseNoteSubType, final LocalDate fromDate, final LocalDate toDate, final boolean admin) {
        log.info("Getting case note details for prisoner list of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);
        final var uri = new UriTemplate(CASE_NOTE_USAGE_FOR_PRISONERS).expand();

        final var body = CaseNoteUsagePrisonersRequest.builder()
                .agencyId(prisonId)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return restCallHelper.post(uri, body, CASE_NOTE_USAGE_PRISONERS_DTO_LIST, admin);
    }

    @Override
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocations(final List<Long> staffIds, final String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by staff IDs", agencyId);
        final var uri = new UriTemplate(URI_CURRENT_ALLOCATIONS).expand(agencyId);

        return restCallHelper.post(uri, staffIds, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocationsByOffenderNos(final List<String> offenderNos, final String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by offender Nos", agencyId);
        final var uri = new UriTemplate(URI_CURRENT_ALLOCATIONS_BY_OFFENDERS).expand(agencyId);

        return restCallHelper.post(uri, offenderNos, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<AllocationHistoryDto> getAllocationHistoryByOffenderNos(final List<String> offenderNos) {
        log.info("Getting Key worker allocations for offender Nos {}", offenderNos);
        final var uri = new UriTemplate(URI_OFFENDERS_ALLOCATION_HISTORY).expand();

        return restCallHelper.post(uri, offenderNos, ALLOCATION_HISTORY, false);
    }

    @Override
    public List<Prison> getAllPrisons() {
        log.info("Getting all prisons");
        final var uri = new UriTemplate(URI_GET_ALL_PRISONS).expand();

        final var prisonListResponse = restCallHelper.getForListWithAuthentication(uri, PRISON_LIST);
        return prisonListResponse.getBody() != null ?
                prisonListResponse.getBody().stream()
                        .map(p -> Prison.builder().prisonId(p.getAgencyId()).build())
                        .collect(Collectors.toList()) : Collections.emptyList();
    }

    @Override
    public boolean isPrison(final String prisonId) {
        final var uri = new UriTemplate(URI_GET_AGENCY).expand(prisonId, "INST");
        final var result = restCallHelper.get(uri, Map.class, true);
        return result.get("agencyId") != null;
    }

    @Override
    public CaseloadUpdate enableNewNomisForCaseload(final String caseload) {
        final var uri = new UriTemplate(URI_ENABLE_USERS_WITH_CASELOAD).expand(caseload);
        return restCallHelper.put(uri, CaseloadUpdate.class, true);
    }

    @Override
    public List<PrisonerIdentifier> getIdentifierByTypeAndValue(final String type, final String value) {
        final var uri = new UriTemplate(URI_IDENTIFIERS).expand(type, value);

        return restCallHelper.getForListWithAuthentication(uri, PRISONER_ID_LIST).getBody();
    }

    @Override
    public Optional<Movement> getMovement(final Long bookingId, final Long movementSeq) {
        final var uri = new UriTemplate(BOOKING_MOVEMENT).expand(bookingId, movementSeq);
        return Optional.ofNullable(restCallHelper.get(uri, Movement.class, true));
    }

    @Override
    public List<BookingIdentifier> getIdentifiersByBookingId(final Long bookingId) {
        final var uri = new UriTemplate(BOOKING_IDENTIFIERS).expand(bookingId);
        return restCallHelper.getForListWithAuthentication(uri, BOOKING_IDENTIFIER_LIST).getBody();
    }

    @Override
    public Optional<OffenderBooking> getBooking(final Long bookingId) {
        final var uri = new UriTemplate(BOOKING_DETAILS).expand(bookingId);
        return Optional.ofNullable(restCallHelper.get(uri, OffenderBooking.class, true));
    }
}
