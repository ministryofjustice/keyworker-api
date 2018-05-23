package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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
public class NomisService {

    public static final String URI_CUSTODY_STATUSES = "/custody-statuses?fromDateTime={fromDateTime}&movementDate={movementDate}";
    public static final String URI_STAFF = "/staff/{staffId}";
    private static final String URI_ACTIVE_OFFENDERS_BY_AGENCY = "/bookings?query=agencyId:eq:'{prisonId}'";
    private static final String URI_ACTIVE_OFFENDER_BY_AGENCY = URI_ACTIVE_OFFENDERS_BY_AGENCY + "&offenderNo={offenderNo}&iepLevel=true";
    public static final String URI_AVAILABLE_KEYWORKERS = "/key-worker/{agencyId}/available";
    public static final String URI_KEY_WORKER_GET_ALLOCATION_HISTORY = "/key-worker/{agencyId}/allocationHistory";
    public static final String GET_STAFF_IN_SPECIFIC_PRISON = "/staff/roles/{agencyId}/role/KW";
    private static final String GET_KEY_WORKER = "/bookings/offenderNo/{offenderNo}/key-worker";
    private static final String GET_PRISONER = "/prisoners?offenderNo={offenderNo}";

    private static final ParameterizedTypeReference<List<OffenderKeyworkerDto>> PARAM_TYPE_REF_OFFENDER_KEY_WORKER =
            new ParameterizedTypeReference<List<OffenderKeyworkerDto>>() {};

    private static final ParameterizedTypeReference<List<PrisonerDetailDto>> PRISONER_DTO_LIST =
            new ParameterizedTypeReference<List<PrisonerDetailDto>>() {};

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<StaffLocationRoleDto>>() {};

    private static final ParameterizedTypeReference<List<OffenderLocationDto>> OFFENDER_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<OffenderLocationDto>>() {};

    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<List<KeyworkerDto>>() {};

    private static final ParameterizedTypeReference<List<PrisonerCustodyStatusDto>> PRISONER_STATUS_DTO_LIST =
            new ParameterizedTypeReference<List<PrisonerCustodyStatusDto>>() {};

    private final RestCallHelper restCallHelper;

    public NomisService(RestCallHelper restCallHelper) {
        this.restCallHelper = restCallHelper;
    }

    public List<PrisonerCustodyStatusDto> getPrisonerStatuses(LocalDateTime threshold, LocalDate movementDate) {
        URI uri = new UriTemplate(URI_CUSTODY_STATUSES).expand(threshold, movementDate);

        return restCallHelper.getForListWithAuthentication(uri, PRISONER_STATUS_DTO_LIST).getBody();
    }

    public Optional<OffenderLocationDto> getOffenderForPrison(String prisonId, String offenderNo) {
        log.info("Getting offender in prison {} offender No {}", prisonId, offenderNo);
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(prisonId, offenderNo);

        List<OffenderLocationDto> offenders = restCallHelper.getForList(uri, OFFENDER_LOCATION_DTO_LIST).getBody();
        return Optional.ofNullable(offenders.size() > 0 ? offenders.get(0) : null);
    }

    public List<PrisonerDetailDto> getOffender(String offenderNo) {
        log.info("Getting bookings for offender No {}", offenderNo);
        URI uri = new UriTemplate(GET_PRISONER).expand(offenderNo);
        return restCallHelper.getForList(uri, PRISONER_DTO_LIST).getBody();
    }

    public ResponseEntity<List<StaffLocationRoleDto>> getActiveStaffKeyWorkersForPrison(String prisonId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting) {
        log.info("Getting KW Staff in prison {}", prisonId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(GET_STAFF_IN_SPECIFIC_PRISON);
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        URI uri = uriBuilder.buildAndExpand(prisonId).toUri();

        return restCallHelper.getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST);
    }

    public Optional<StaffLocationRoleDto> getStaffKeyWorkerForPrison(String prisonId, Long staffId) {
        log.info("Getting staff in prison {} staff Id {}", prisonId, staffId);

        URI uri = new UriTemplate(GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}&activeOnly=false").expand(prisonId, staffId);
        log.debug("About to retrieve keyworker from Elite2api using uri {}", uri.toString());

        List<StaffLocationRoleDto> staff = restCallHelper.getForList(uri, ELITE_STAFF_LOCATION_DTO_LIST).getBody();
        final Optional<StaffLocationRoleDto> staffLocationRoleDto = Optional.ofNullable(staff.size() > 0 ? staff.get(0) : null);
        log.debug("Result: {}", staffLocationRoleDto);
        return staffLocationRoleDto;
    }

    public BasicKeyworkerDto getBasicKeyworkerDtoForOffender(String offenderNo) {
        log.info("Getting KW for offender", offenderNo);

        URI uri = new UriTemplate(GET_KEY_WORKER).expand(offenderNo);
        return restCallHelper.get(uri, BasicKeyworkerDto.class);
    }

    public ResponseEntity<List<KeyworkerDto>> getAvailableKeyworkers(String prisonId) {
        log.info("Getting available KW in prison {}", prisonId);
        URI uri = new UriTemplate(URI_AVAILABLE_KEYWORKERS).expand(prisonId);
        return restCallHelper.getForList(uri, KEYWORKER_DTO_LIST);
    }

    public List<OffenderLocationDto> getOffendersAtLocation(String prisonId, String sortFields, SortOrder sortOrder) {
        log.info("Getting offenders in prison {}", prisonId);
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId);

        return restCallHelper.getAllWithSorting(
                uri, sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                });
    }

    public StaffLocationRoleDto getBasicKeyworkerDtoForStaffId(Long staffId) {
        URI uri = new UriTemplate(URI_STAFF).expand(staffId);
        log.debug("Getting basic keyworker details for staffId {} from Elite2api using uri {}", staffId, uri.toString());
        return restCallHelper.get(uri, StaffLocationRoleDto.class);
    }

    public List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String prisonId, long offset, long limit) {
        log.info("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", prisonId, offset, limit);

        URI uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId);
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return restCallHelper.getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFFENDER_KEY_WORKER).getBody();
    }
}
