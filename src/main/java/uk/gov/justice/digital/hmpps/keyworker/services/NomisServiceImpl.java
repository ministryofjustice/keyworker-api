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

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Component
@Slf4j
public class NomisServiceImpl implements NomisService {

    private static final String GET_KEY_WORKER = "/bookings/offenderNo/{offenderNo}/key-worker";

    private static final ParameterizedTypeReference<List<OffenderKeyworkerDto>> PARAM_TYPE_REF_OFFENDER_KEY_WORKER =
            new ParameterizedTypeReference<List<OffenderKeyworkerDto>>() {};

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<StaffLocationRoleDto>>() {};

    private static final ParameterizedTypeReference<List<OffenderLocationDto>> OFFENDER_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<OffenderLocationDto>>() {};

    private static final ParameterizedTypeReference<List<PrisonerDetail>> PRISONER_DETAIL_LIST =
            new ParameterizedTypeReference<List<PrisonerDetail>>() {};


    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<List<KeyworkerDto>>() {};

    private static final ParameterizedTypeReference<List<PrisonerCustodyStatusDto>> PRISONER_STATUS_DTO_LIST =
            new ParameterizedTypeReference<List<PrisonerCustodyStatusDto>>() {};

    private static final ParameterizedTypeReference<List<CaseNoteUsageDto>> CASE_NOTE_USAGE_DTO_LIST =
            new ParameterizedTypeReference<List<CaseNoteUsageDto>>() {};

    private static final ParameterizedTypeReference<List<CaseNoteUsagePrisonersDto>> CASE_NOTE_USAGE_PRISONERS_DTO_LIST =
            new ParameterizedTypeReference<List<CaseNoteUsagePrisonersDto>>() {};

    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> LEGACY_KEYWORKER_ALLOCATIONS =
            new ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>>() {};

    private static final ParameterizedTypeReference<List<AllocationHistoryDto>> ALLOCATION_HISTORY =
            new ParameterizedTypeReference<List<AllocationHistoryDto>>() {};

    private final RestCallHelper restCallHelper;

    public NomisServiceImpl(RestCallHelper restCallHelper) {
        this.restCallHelper = restCallHelper;
    }

    @Override
    public List<PrisonerCustodyStatusDto> getPrisonerStatuses(LocalDateTime threshold, LocalDate movementDate) {
        URI uri = new UriTemplate(URI_MOVEMENTS).expand(threshold, movementDate);

        return restCallHelper.getForListWithAuthentication(uri, PRISONER_STATUS_DTO_LIST).getBody();
    }

    @Override
    public Optional<OffenderLocationDto> getOffenderForPrison(String prisonId, String offenderNo) {
        log.info("Getting offender in prison {} offender No {}", prisonId, offenderNo);
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(prisonId, offenderNo);

        List<OffenderLocationDto> offenders = restCallHelper.getForList(uri, OFFENDER_LOCATION_DTO_LIST).getBody();
        return Optional.ofNullable(offenders.size() > 0 ? offenders.get(0) : null);
    }

    @Override
    public Optional<PrisonerDetail> getPrisonerDetail(String offenderNo) {
        log.info("Getting prisoner details for NOMIS No {}", offenderNo);
        URI uri = new UriTemplate(URI_PRISONER_LOOKUP).expand(offenderNo);

        List<PrisonerDetail> prisonerDetail = restCallHelper.getForList(uri, PRISONER_DETAIL_LIST).getBody();
        return Optional.ofNullable(prisonerDetail.size() > 0 ? prisonerDetail.get(0) : null);
    }

    @Override
    public ResponseEntity<List<StaffLocationRoleDto>> getActiveStaffKeyWorkersForPrison(String prisonId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting, boolean admin) {
        log.info("Getting KW Staff in prison {}", prisonId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(GET_STAFF_IN_SPECIFIC_PRISON);
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        URI uri = uriBuilder.buildAndExpand(prisonId).toUri();

        return restCallHelper.getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST, admin);
    }

    @Override
    public Optional<StaffLocationRoleDto> getStaffKeyWorkerForPrison(String prisonId, Long staffId) {
        log.info("Getting staff in prison {} staff Id {}", prisonId, staffId);

        URI uri = new UriTemplate(GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}&activeOnly=false").expand(prisonId, staffId);
        log.debug("About to retrieve keyworker from Elite2api using uri {}", uri.toString());

        List<StaffLocationRoleDto> staff = restCallHelper.getForList(uri, ELITE_STAFF_LOCATION_DTO_LIST).getBody();
        final Optional<StaffLocationRoleDto> staffLocationRoleDto = Optional.ofNullable(staff.size() > 0 ? staff.get(0) : null);
        log.debug("Result: {}", staffLocationRoleDto);
        return staffLocationRoleDto;
    }

    @Override
    public BasicKeyworkerDto getBasicKeyworkerDtoForOffender(String offenderNo) {
        log.info("Getting KW for offender", offenderNo);

        URI uri = new UriTemplate(GET_KEY_WORKER).expand(offenderNo);
        return restCallHelper.get(uri, BasicKeyworkerDto.class);
    }

    @Override
    public List<KeyworkerDto> getAvailableKeyworkers(String prisonId) {
        log.info("Getting available KW in prison {}", prisonId);
        URI uri = new UriTemplate(URI_AVAILABLE_KEYWORKERS).expand(prisonId);
        return restCallHelper.getForList(uri, KEYWORKER_DTO_LIST).getBody();
    }

    @Override
    public List<OffenderLocationDto> getOffendersAtLocation(String prisonId, String sortFields, SortOrder sortOrder, boolean admin) {
        log.info("Getting offenders in prison {}", prisonId);
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId);

        return restCallHelper.getAllWithSorting(
                uri, sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                }, admin);
    }

    @Override
    @Cacheable("getBasicKeyworkerDtoForStaffId")
    public StaffLocationRoleDto getBasicKeyworkerDtoForStaffId(Long staffId) {
        URI uri = new UriTemplate(URI_STAFF).expand(staffId);
        log.debug("Getting basic keyworker details for staffId {} from Elite2api using uri {}", staffId, uri.toString());
        return restCallHelper.get(uri, StaffLocationRoleDto.class);
    }

    @Override
    public List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String prisonId, long offset, long limit) {
        log.info("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", prisonId, offset, limit);

        URI uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId);
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return restCallHelper.getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFFENDER_KEY_WORKER).getBody();
    }

    @Override
    @Cacheable("getStaffDetailByUserId")
    public StaffUser getStaffDetailByUserId(String userId) {
        log.info("Getting staff details for user Id {}", userId);
        URI uri = new UriTemplate(GET_USER_DETAILS).expand(userId);
        log.debug("About to retrieve staff details from Elite2api using uri {}", uri.toString());

        try {
            StaffUser staffUser = restCallHelper.get(uri, StaffUser.class);
            log.debug("Result: {}", staffUser);
            return staffUser;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().is4xxClientError()) {
                return StaffUser.builder().firstName("User").lastName(userId).username(userId).build();
            }
        }
        return null;
    }

    @Override
    public List<CaseNoteUsageDto> getCaseNoteUsage(List<Long> staffIds, String caseNoteType, String caseNoteSubType, LocalDate fromDate, LocalDate toDate) {
        log.info("Getting case note details of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);
        URI uri = new UriTemplate(CASE_NOTE_USAGE).expand();

        CaseNoteUsageRequest body = CaseNoteUsageRequest.builder()
                .staffIds(staffIds)
                .type(caseNoteType)
                .subType(caseNoteSubType)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return restCallHelper.post(uri, body, CASE_NOTE_USAGE_DTO_LIST, false);
    }

    @Override
    public List<CaseNoteUsagePrisonersDto> getCaseNoteUsageForPrisoners(List<String> offenderNos, Long staffId, String caseNoteType, String caseNoteSubType, LocalDate fromDate, LocalDate toDate, boolean admin) {
        log.info("Getting case note details for prisoner list of type {} sub type {}, from {}, to {}", caseNoteType, caseNoteSubType, fromDate, toDate);
        URI uri = new UriTemplate(CASE_NOTE_USAGE_BY_PRISONER).expand();

        CaseNoteUsagePrisonersRequest body = CaseNoteUsagePrisonersRequest.builder()
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
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocations(List<Long> staffIds, String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by staff IDs", agencyId);
        URI uri = new UriTemplate(URI_CURRENT_ALLOCATIONS).expand(agencyId);

        return restCallHelper.post(uri, staffIds, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<KeyworkerAllocationDetailsDto> getCurrentAllocationsByOffenderNos(List<String> offenderNos, String agencyId) {
        log.info("Getting Legacy Key worker allocations for {} agencyId by offender Nos", agencyId);
        URI uri = new UriTemplate(URI_CURRENT_ALLOCATIONS_BY_OFFENDERS).expand(agencyId);

        return restCallHelper.post(uri, offenderNos, LEGACY_KEYWORKER_ALLOCATIONS, false);
    }

    @Override
    public List<AllocationHistoryDto> getAllocationHistoryByOffenderNos(List<String> offenderNos) {
        log.info("Getting Key worker allocations for offender Nos {}", offenderNos);
        URI uri = new UriTemplate(URI_OFFENDERS_ALLOCATION_HISTORY).expand();

        return restCallHelper.post(uri, offenderNos, ALLOCATION_HISTORY, false);
    }
}
