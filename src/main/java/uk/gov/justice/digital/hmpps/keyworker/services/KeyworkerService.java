package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.CreateUpdate;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeyworkerService extends Elite2ApiSource {
    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> KEYWORKER_ALLOCATION_LIST =
            new ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>>() {
            };

    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<List<KeyworkerDto>>() {
            };

    private static final ParameterizedTypeReference<List<OffenderSummaryDto>> OFFENDER_SUMMARY_DTO_LIST =
            new ParameterizedTypeReference<List<OffenderSummaryDto>>() {
            };

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<StaffLocationRoleDto>>() {
            };

    private static final HttpHeaders CONTENT_TYPE_APPLICATION_JSON = httpContentTypeHeaders(MediaType.APPLICATION_JSON);

    private static HttpHeaders httpContentTypeHeaders(MediaType contentType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    private final AuthenticationFacade authenticationFacade;
    private final OffenderKeyworkerRepository repository;
    private final KeyworkerRepository keyworkerRepository;

    @Value("${svc.kw.supported.agencies}")
    private Set<String> supportedAgencies;

    @Value("${svc.kw.allocation.capacity.default}")
    private int capacityDefault;

    public KeyworkerService(AuthenticationFacade authenticationFacade, OffenderKeyworkerRepository repository, KeyworkerRepository keyworkerRepository) {
        this.authenticationFacade = authenticationFacade;
        this.repository = repository;
        this.keyworkerRepository = keyworkerRepository;
    }


    public void verifyAgencySupport(String agencyId) {
        if (!supportedAgencies.contains(agencyId)) {
            throw AgencyNotSupportedException.withId(agencyId);
        }
    }

    public List<KeyworkerDto> getAvailableKeyworkers(String agencyId) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/available").expand(agencyId);

        ResponseEntity<List<KeyworkerDto>> responseEntity = getForList(uri, KEYWORKER_DTO_LIST);

        return responseEntity.getBody();
    }

    public Page<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("/key-worker/{agencyId}/allocations");

        allocationFilter.getAllocationType().ifPresent(at -> builder.queryParam("allocationType", at.getTypeCode()));
        allocationFilter.getFromDate().ifPresent(fd -> builder.queryParam("fromDate", fd.format(DateTimeFormatter.ISO_DATE)));

        builder.queryParam("toDate", allocationFilter.getToDate().format(DateTimeFormatter.ISO_DATE));

        URI uri = builder.buildAndExpand(allocationFilter.getAgencyId()).toUri();

        ResponseEntity<List<KeyworkerAllocationDetailsDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, KEYWORKER_ALLOCATION_LIST);

        return new Page<>(response.getBody(), response.getHeaders());
    }

    public Page<OffenderSummaryDto> getUnallocatedOffenders(String agencyId, PagingAndSortingDto pagingAndSorting) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/offenders/unallocated").expand(agencyId);

        ResponseEntity<List<OffenderSummaryDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, OFFENDER_SUMMARY_DTO_LIST);

        return new Page<>(response.getBody(), response.getHeaders());
    }

    public KeyworkerDto getKeyworkerDetails(Long staffId) {

        URI uri = new UriTemplate("/key-worker/{staffId}").expand(staffId);

        return restTemplate.getForObject(uri.toString(), KeyworkerDto.class);
    }

    @PreAuthorize("#oauth2.hasScope('write')")
    public String startAutoAllocation(String agencyId) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/allocate/start").expand(agencyId);

        return restTemplate.exchange(
                uri.toString(),
                HttpMethod.POST,
                new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON),
                String.class).getBody();
    }

    @PreAuthorize("#oauth2.hasScope('write')")
    public void allocate(KeyworkerAllocationDto keyworkerAllocation) {

        restTemplate.postForObject(
                "/key-worker/allocate",
                new HttpEntity<>(keyworkerAllocation, CONTENT_TYPE_APPLICATION_JSON),
                Void.class);
    }

    /**
     * Creates a new offender - Key worker allocation record.
     *
     * @param allocation allocation details.
     */
    @PreAuthorize("#oauth2.hasScope('write')")
    public void allocate(OffenderKeyworker allocation) {
        Validate.notNull(allocation);

        // This service method creates a new allocation record, therefore it will apply certain defaults automatically.
        LocalDateTime now = LocalDateTime.now();
        String currentUser = authenticationFacade.getCurrentUsername();

        CreateUpdate createUpdate = CreateUpdate.builder().creationDateTime(now).createUserId(currentUser).build();

        allocation.setActive(true);
        allocation.setAssignedDateTime(now);
        allocation.setCreateUpdate(createUpdate);

        if (StringUtils.isBlank(allocation.getUserId())) {
            allocation.setUserId(authenticationFacade.getCurrentUsername());
        }

        repository.save(allocation);
    }

    public List<OffenderKeyworker> getAllocationHistoryForPrisoner(String offenderNo) {
        return repository.findByOffenderNo(offenderNo);
    }

    public List<OffenderKeyworker> getAllocationsForKeyworker(Long staffId) {
        return repository.findByStaffId(staffId);
    }

    public Page<KeyworkerDto> getKeyworkers(String agencyId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting) {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/staff/roles/{agencyId}/role/KW");
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        URI uri = uriBuilder.buildAndExpand(agencyId).toUri();

        ResponseEntity<List<StaffLocationRoleDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST);

        final List<KeyworkerDto> convertedKeyworkerDtoList = response.getBody().stream().map(dto -> convertToKeyworkerDto(dto)
        ).collect(Collectors.toList());

        return new Page<>(convertedKeyworkerDtoList, response.getHeaders());
    }

    private KeyworkerDto convertToKeyworkerDto(StaffLocationRoleDto dto) {
        final Keyworker keyworker = keyworkerRepository.findOne(dto.getStaffId());

        return KeyworkerDto.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .staffId(dto.getStaffId())
                .thumbnailId(dto.getThumbnailId())
                .capacity(determineCapacity(dto, keyworker, dto.getStaffId()))
                .scheduleType(dto.getScheduleTypeDescription())
                .agencyDescription(dto.getAgencyDescription())
                .agencyId(dto.getAgencyId())
                .status(keyworker != null ? keyworker.getStatus() : KeyworkerStatus.ACTIVE)
                .build();
    }

    private int determineCapacity(StaffLocationRoleDto dto, Keyworker keyworker, Long staffId) {
        int capacity = capacityDefault;
        if (keyworker != null) {
            capacity = keyworker.getCapacity();
        } else {
            if (dto.getHoursPerWeek() != null) {
                capacity = dto.getHoursPerWeek().intValue();
            } else {
                log.debug("No capacity set for key worker {}, using capacity default of {}", staffId, capacityDefault);
            }
        }
        return capacity;
    }

}
