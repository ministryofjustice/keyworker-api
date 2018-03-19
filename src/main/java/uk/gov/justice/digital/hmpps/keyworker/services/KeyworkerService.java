package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
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
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Validated
@Slf4j
public class KeyworkerService extends Elite2ApiSource {
    public static final String URI_ACTIVE_OFFENDERS_BY_AGENCY = "/locations/description/{agencyId}/inmates";
    public static final String URI_ACTIVE_OFFENDER_BY_AGENCY = URI_ACTIVE_OFFENDERS_BY_AGENCY + "?keywords={offenderNo}";

    private static final ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>> KEYWORKER_ALLOCATION_LIST =
            new ParameterizedTypeReference<List<KeyworkerAllocationDetailsDto>>() {
            };

    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<List<KeyworkerDto>>() {
            };

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<StaffLocationRoleDto>>() {
            };

    private static final ParameterizedTypeReference<List<OffenderSummaryDto>> OFFENDER_SUMMARY_DTO_LIST =
            new ParameterizedTypeReference<List<OffenderSummaryDto>>() {
            };

    private static final HttpHeaders CONTENT_TYPE_APPLICATION_JSON = httpContentTypeHeaders(MediaType.APPLICATION_JSON);

    private static HttpHeaders httpContentTypeHeaders(MediaType contentType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    private final KeyworkerMigrationService migrationService;
    private final AuthenticationFacade authenticationFacade;
    private final OffenderKeyworkerRepository repository;
    private final KeyworkerRepository keyworkerRepository;
    private final KeyworkerAllocationProcessor processor;

    @Value("${svc.kw.supported.agencies}")
    private Set<String> supportedAgencies;

    @Value("${svc.kw.allocation.capacity.default}")
    private int capacityDefault;

    public KeyworkerService(KeyworkerMigrationService migrationService, AuthenticationFacade authenticationFacade,
                            OffenderKeyworkerRepository repository, KeyworkerRepository keyworkerRepository,
                            KeyworkerAllocationProcessor processor) {
        this.migrationService = migrationService;
        this.authenticationFacade = authenticationFacade;
        this.repository = repository;
        this.keyworkerRepository = keyworkerRepository;
        this.processor = processor;
    }

    public void verifyAgencySupport(String agencyId) {
        Validate.notBlank(agencyId);

        if (!supportedAgencies.contains(agencyId)) {
            throw AgencyNotSupportedException.withId(agencyId);
        }
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<KeyworkerDto> getAvailableKeyworkers(String agencyId) {

        URI uri = new UriTemplate("/key-worker/{agencyId}/available").expand(agencyId);

        ResponseEntity<List<KeyworkerDto>> responseEntity = getForList(uri, KEYWORKER_DTO_LIST);

        return responseEntity.getBody();
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public Page<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("/key-worker/{agencyId}/allocations");

        allocationFilter.getAllocationType().ifPresent(at -> builder.queryParam("allocationType", at.getTypeCode()));
        allocationFilter.getFromDate().ifPresent(fd -> builder.queryParam("fromDate", fd.format(DateTimeFormatter.ISO_DATE)));

        builder.queryParam("toDate", allocationFilter.getToDate().format(DateTimeFormatter.ISO_DATE));

        URI uri = builder.buildAndExpand(allocationFilter.getAgencyId()).toUri();

        ResponseEntity<List<KeyworkerAllocationDetailsDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, KEYWORKER_ALLOCATION_LIST);

        return new Page<>(response.getBody(), response.getHeaders());
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderSummaryDto> getUnallocatedOffenders(String agencyId, String sortFields, SortOrder sortOrder) {

        migrationService.checkAndMigrateOffenderKeyWorker(agencyId);

        URI uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(agencyId);

        List<OffenderSummaryDto> allOffenders = getAllWithSorting(
                uri, sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderSummaryDto>>() {
                });

        return processor.filterByUnallocated(allOffenders);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderKeyworkerDto> getOffenders(String agencyId, Collection<String> offenderNos) {
        verifyAgencySupport(agencyId);
        migrationService.checkAndMigrateOffenderKeyWorker(agencyId);
        final List<OffenderKeyworker> results =
                CollectionUtils.isEmpty(offenderNos)
                        ? repository.findByActiveAndAgencyId(true, agencyId)
                        : repository.findByActiveAndAgencyIdAndOffenderNoIn(true, agencyId, offenderNos);
        return ConversionHelper.convertOffenderKeyworkerModel2Dto(results);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public KeyworkerDto getKeyworkerDetails(String agencyId, Long staffId) {

        URI uri = new UriTemplate("/staff/roles/{agencyId}/role/KW?staffId={staffId}").expand(agencyId, staffId);

        // We only expect one row. Allow 2 to detect unexpected extras
        PagingAndSortingDto paging = PagingAndSortingDto.builder().pageLimit(2L).pageOffset(0L).build();
        ResponseEntity<List<StaffLocationRoleDto>> response = getWithPaging(uri, paging, ELITE_STAFF_LOCATION_DTO_LIST);

        if (response.getBody().isEmpty()) {
            return null;
        }
        Assert.isTrue(response.getBody().size() <= 1, String.format("Multiple rows found for role of staffId %d at agencyId %s", staffId, agencyId));
        final StaffLocationRoleDto dto = response.getBody().get(0);
        return convertToKeyworkerDto(dto);
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
    public void allocate(@Valid @NotNull KeyworkerAllocationDto keyworkerAllocation) {

        doAllocateValidation(keyworkerAllocation);
        doAllocate(keyworkerAllocation);
    }

    private void doAllocateValidation(KeyworkerAllocationDto keyworkerAllocation) {
        verifyAgencySupport(keyworkerAllocation.getAgencyId());
        Validate.notBlank(keyworkerAllocation.getOffenderNo(), "Missing prisoner number.");
        Validate.notNull(keyworkerAllocation.getStaffId(), "Missing staff id.");

        final URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(keyworkerAllocation.getAgencyId(), keyworkerAllocation.getOffenderNo());
        final List<OffenderSummaryDto> list = getForList(uri, OFFENDER_SUMMARY_DTO_LIST).getBody();
        Validate.notEmpty(list, String.format("Prisoner %s not found at agencyId %s using endpoint %s.",
                keyworkerAllocation.getOffenderNo(), keyworkerAllocation.getAgencyId(), uri));

        KeyworkerDto keyworkerDetails = getKeyworkerDetails(keyworkerAllocation.getAgencyId(), keyworkerAllocation.getStaffId());
        Validate.notNull(keyworkerDetails, String.format("Keyworker %d not found at agencyId %s.",
                keyworkerAllocation.getStaffId(), keyworkerAllocation.getAgencyId()));
    }

    private void doAllocate(KeyworkerAllocationDto newAllocation) {

        // Remove current allocation if any
        final List<OffenderKeyworker> entities = repository.findByActiveAndOffenderNo(
                true, newAllocation.getOffenderNo());
        final LocalDateTime now = LocalDateTime.now();
        entities.forEach(e -> {
            e.setActive(false);
            e.setExpiryDateTime(now);
            e.setDeallocationReason(newAllocation.getDeallocationReason());
        });

        OffenderKeyworker allocation = ConversionHelper.getOffenderKeyworker(newAllocation, authenticationFacade.getCurrentUsername());

        allocate(allocation);
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

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderKeyworker> getAllocationHistoryForPrisoner(String offenderNo) {
        return repository.findByOffenderNo(offenderNo);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderKeyworker> getAllocationsForKeyworker(Long staffId) {
        return repository.findByStaffId(staffId);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<KeyworkerAllocationDetailsDto> getAllocationsForKeyworkerWithOffenderDetails(String agencyId, Long staffId) {

        migrationService.checkAndMigrateOffenderKeyWorker(agencyId);

        final List<OffenderKeyworker> allocations = repository.findByStaffIdAndAgencyIdAndActive(staffId, agencyId, true);

        final List<KeyworkerAllocationDetailsDto> detailsDtoList = allocations.stream()
                .map(allocation -> decorateWithOffenderDetails(agencyId, allocation))
                //remove allocations from returned list that do not have associated booking records
                .filter(dto -> dto.getBookingId()!=null)
                .sorted(Comparator.comparing(KeyworkerAllocationDetailsDto::getLastName))
                .collect(Collectors.toList());

        log.debug("Retrieved allocations for keyworker {}:\n{}", staffId, detailsDtoList);

        return detailsDtoList;
    }

    private KeyworkerAllocationDetailsDto decorateWithOffenderDetails(String agencyId, OffenderKeyworker allocation) {
        KeyworkerAllocationDetailsDto dto;
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(agencyId, allocation.getOffenderNo());

        final ResponseEntity<List<OffenderSummaryDto>> listOfOne = getForList(uri, OFFENDER_SUMMARY_DTO_LIST);

        if (listOfOne.getBody().size() > 0) {
            final OffenderSummaryDto offenderSummaryDto = listOfOne.getBody().get(0);
            dto =  KeyworkerAllocationDetailsDto.builder()
                    .bookingId(offenderSummaryDto.getBookingId())
                    .offenderNo(allocation.getOffenderNo())
                    .firstName(offenderSummaryDto.getFirstName())
                    .middleNames(offenderSummaryDto.getMiddleNames())
                    .lastName(offenderSummaryDto.getLastName())
                    .staffId(allocation.getStaffId())
                    .agencyId(allocation.getAgencyId())
                    .assigned(allocation.getAssignedDateTime())
                    .allocationType(allocation.getAllocationType())
                    .internalLocationDesc(offenderSummaryDto.getInternalLocationDesc())
                    .build();
        } else {
            log.error(String.format("Allocation does not have associated booking, removing from keyworker allocation list:\noffender %s in agency %s not found using elite endpoint %s", allocation.getOffenderNo(), agencyId, uri));
            dto =  KeyworkerAllocationDetailsDto.builder().build();
        }
        return dto;
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public Page<KeyworkerDto> getKeyworkers(String agencyId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting) {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/staff/roles/{agencyId}/role/KW");
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        URI uri = uriBuilder.buildAndExpand(agencyId).toUri();

        ResponseEntity<List<StaffLocationRoleDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST);

        final List<KeyworkerDto> convertedKeyworkerDtoList = response.getBody().stream().map(this::convertToKeyworkerDto
        ).collect(Collectors.toList());

        return new Page<>(convertedKeyworkerDtoList, response.getHeaders());
    }

    private KeyworkerDto convertToKeyworkerDto(StaffLocationRoleDto dto) {
        final Keyworker keyworker = keyworkerRepository.findOne(dto.getStaffId());
        final Integer allocationsCount = repository.countByStaffIdAndAgencyIdAndActive(dto.getStaffId(), dto.getAgencyId(), true);

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
                .numberAllocated(allocationsCount)
                .build();
    }

    private Integer determineCapacity(StaffLocationRoleDto dto, Keyworker keyworker, Long staffId) {
        Integer capacity = capacityDefault;
        if (keyworker != null && keyworker.getCapacity() != null) {
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
