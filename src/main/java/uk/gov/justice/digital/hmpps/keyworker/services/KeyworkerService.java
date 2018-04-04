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
import uk.gov.justice.digital.hmpps.keyworker.model.*;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
@Transactional
@Validated
@Slf4j
public class KeyworkerService extends Elite2ApiSource {
    public static final String URI_ACTIVE_OFFENDERS_BY_AGENCY = "/locations/description/{agencyId}/inmates";
    public static final String URI_ACTIVE_OFFENDER_BY_AGENCY = URI_ACTIVE_OFFENDERS_BY_AGENCY + "?keywords={offenderNo}";
    public static final String URI_AVAILABLE_KEYWORKERS = "/key-worker/{agencyId}/available";
    public static final String URI_STAFF = "/staff/{staffId}";


    private static final ParameterizedTypeReference<List<KeyworkerDto>> KEYWORKER_DTO_LIST =
            new ParameterizedTypeReference<List<KeyworkerDto>>() {
            };

    private static final ParameterizedTypeReference<List<StaffLocationRoleDto>> ELITE_STAFF_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<StaffLocationRoleDto>>() {
            };

    private static final ParameterizedTypeReference<List<OffenderLocationDto>> OFFENDER_LOCATION_DTO_LIST =
            new ParameterizedTypeReference<List<OffenderLocationDto>>() {
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
    private final KeyworkerAllocationProcessor processor;
    private final PrisonSupportedService prisonSupportedService;

    @Value("${svc.kw.allocation.capacity.default}")
    private int capacityDefault;

    public KeyworkerService(AuthenticationFacade authenticationFacade,
                            OffenderKeyworkerRepository repository, KeyworkerRepository keyworkerRepository,
                            KeyworkerAllocationProcessor processor, PrisonSupportedService prisonSupportedService) {
        this.authenticationFacade = authenticationFacade;
        this.repository = repository;
        this.keyworkerRepository = keyworkerRepository;
        this.processor = processor;
        this.prisonSupportedService = prisonSupportedService;
    }


    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<KeyworkerDto> getAvailableKeyworkers(String prisonId) {

        ResponseEntity<List<KeyworkerDto>> responseEntity = doAvailableKeyworkersRequest(prisonId);

        final List<KeyworkerDto> returnedList = responseEntity.getBody();

        returnedList.forEach(keyworkerDto -> keyworkerDto.setAgencyId(prisonId));

        final List<KeyworkerDto> decoratedList = returnedList.stream().map(this::decorateWithKeyworkerData)
                .sorted(Comparator.comparing(KeyworkerDto::getNumberAllocated)
                ).collect(Collectors.toList());

        return decoratedList;
    }

    private ResponseEntity<List<KeyworkerDto>> doAvailableKeyworkersRequest(String prisonId) {
        URI uri = new UriTemplate(URI_AVAILABLE_KEYWORKERS).expand(prisonId);

        return getForList(uri, KEYWORKER_DTO_LIST);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<KeyworkerDto> getKeyworkersAvailableforAutoAllocation(String prisonId) {
        final List<KeyworkerDto> availableKeyworkers = getAvailableKeyworkers(prisonId);
        return availableKeyworkers.stream().filter(KeyworkerDto::getAutoAllocationAllowed).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public Page<KeyworkerAllocationDetailsDto> getAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        final String prisonId = allocationFilter.getPrisonId();
        final List<OffenderKeyworker> allocations =
                allocationFilter.getAllocationType().isPresent() ?
                        repository.findByActiveAndPrisonIdAndAllocationType(true, prisonId, allocationFilter.getAllocationType().get())
                        :
                        repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL);
// TODO implement date filters

        // Add offender names and locations
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId);

        List<OffenderLocationDto> allOffenders = getAllWithSorting(
                uri, pagingAndSorting.getSortFields(), pagingAndSorting.getSortOrder(),
                new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                });

        final List<KeyworkerAllocationDetailsDto> results = processor.decorateAllocated(allocations, allOffenders);

        return new Page<>(results, (long) allocations.size(), 0L, (long) allocations.size());
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderLocationDto> getUnallocatedOffenders(String prisonId, String sortFields, SortOrder sortOrder) {

        prisonSupportedService.verifyPrisonMigrated(prisonId);

        URI uri = new UriTemplate(URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId);

        List<OffenderLocationDto> allOffenders = getAllWithSorting(
                uri, sortFields, sortOrder, new ParameterizedTypeReference<List<OffenderLocationDto>>() {
                });

        return processor.filterByUnallocated(allOffenders);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public List<OffenderKeyworkerDto> getOffenders(String prisonId, Collection<String> offenderNos) {
        final List<OffenderKeyworker> results =
                CollectionUtils.isEmpty(offenderNos)
                        ? repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL)
                        : repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, prisonId, offenderNos, AllocationType.PROVISIONAL);
        return ConversionHelper.convertOffenderKeyworkerModel2Dto(results);
    }

    public Optional<BasicKeyworkerDto> getCurrentKeyworkerForPrisoner(String prisonId, String offenderNo) {
         BasicKeyworkerDto currentKeyworker = null;
        if (prisonSupportedService.isMigrated(prisonId)) {
            OffenderKeyworker activeOffenderKeyworker = repository.findByOffenderNoAndActive(offenderNo, true);
            if (activeOffenderKeyworker != null) {
                KeyworkerDto basicKeyworkerDto = getBasicKeyworkerDto(activeOffenderKeyworker.getStaffId());
                if (basicKeyworkerDto != null) {
                    currentKeyworker = BasicKeyworkerDto.builder()
                            .firstName(basicKeyworkerDto.getFirstName())
                            .lastName(basicKeyworkerDto.getLastName())
                            .staffId(basicKeyworkerDto.getStaffId())
                            .email(basicKeyworkerDto.getEmail())
                            .build();
                }
            }
        } else {
            URI uri = new UriTemplate("/bookings/offenderNo/{offenderNo}/key-worker").expand(offenderNo);
            currentKeyworker = restTemplate.exchange(
                    uri.toString(),
                    HttpMethod.GET,
                    new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON),
                    BasicKeyworkerDto.class).getBody();
        }
        return Optional.ofNullable(currentKeyworker);
    }

    public KeyworkerDto getKeyworkerDetails(String prisonId, Long staffId) {

        URI uri = new UriTemplate("/staff/roles/{agencyId}/role/KW?staffId={staffId}").expand(prisonId, staffId);

        // We only expect one row. Allow 2 to detect unexpected extras
        PagingAndSortingDto paging = PagingAndSortingDto.builder().pageLimit(2L).pageOffset(0L).build();
        ResponseEntity<List<StaffLocationRoleDto>> response = getWithPaging(uri, paging, ELITE_STAFF_LOCATION_DTO_LIST);

        if (response.getBody().isEmpty()) {
            return getBasicKeyworkerDto(staffId);
        }
        Assert.isTrue(response.getBody().size() <= 1, format("Multiple rows found for role of staffId %d at agencyId %s", staffId, prisonId));
        final StaffLocationRoleDto dto = response.getBody().get(0);
        return decorateWithKeyworkerData(ConversionHelper.getKeyworkerDto(dto));
    }

    /**
     * As a fallback, just get basic staff details (i.e. name)
     * @param staffId staff ID
     * @return KeyworkerDto Basic Key-worker Details
     */
    private KeyworkerDto getBasicKeyworkerDto(Long staffId) {
        URI uri = new UriTemplate("/staff/{staffId}").expand(staffId);
        StaffLocationRoleDto basicStaffDetails = restTemplate.exchange(
                uri.toString(),
                HttpMethod.GET,
                new HttpEntity<>(null, CONTENT_TYPE_APPLICATION_JSON),
                StaffLocationRoleDto.class).getBody();
        return basicStaffDetails == null ? null : ConversionHelper.getKeyworkerDto(basicStaffDetails);
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public void allocate(@Valid @NotNull KeyworkerAllocationDto keyworkerAllocation) {
        prisonSupportedService.verifyPrisonMigrated(keyworkerAllocation.getPrisonId());
        doAllocateValidation(keyworkerAllocation);
        doAllocate(keyworkerAllocation);
    }

    private void doAllocateValidation(KeyworkerAllocationDto keyworkerAllocation) {
        Validate.notBlank(keyworkerAllocation.getOffenderNo(), "Missing prisoner number.");
        Validate.notNull(keyworkerAllocation.getStaffId(), "Missing staff id.");

        final URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(keyworkerAllocation.getPrisonId(), keyworkerAllocation.getOffenderNo());
        final List<OffenderLocationDto> list = getForList(uri, OFFENDER_LOCATION_DTO_LIST).getBody();
        Validate.notEmpty(list, format("Prisoner %s not found at agencyId %s using endpoint %s.",
                keyworkerAllocation.getOffenderNo(), keyworkerAllocation.getPrisonId(), uri));

        KeyworkerDto keyworkerDetails = getKeyworkerDetails(keyworkerAllocation.getPrisonId(), keyworkerAllocation.getStaffId());
        Validate.notNull(keyworkerDetails, format("Keyworker %d not found at agencyId %s.",
                keyworkerAllocation.getStaffId(), keyworkerAllocation.getPrisonId()));
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
    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public void allocate(OffenderKeyworker allocation) {
        Validate.notNull(allocation);

        // This service method creates a new allocation record, therefore it will apply certain defaults automatically.
        LocalDateTime now = LocalDateTime.now();

        allocation.setActive(true);
        allocation.setAssignedDateTime(now);

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
    public List<KeyworkerAllocationDetailsDto> getAllocationsForKeyworkerWithOffenderDetails(String prisonId, Long staffId, boolean skipOffenderDetails) {

        prisonSupportedService.verifyPrisonMigrated(prisonId);

        final List<OffenderKeyworker> allocations = repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, prisonId, true, AllocationType.PROVISIONAL);

        final List<KeyworkerAllocationDetailsDto> detailsDtoList;
        if (skipOffenderDetails) {
            detailsDtoList = allocations.stream()
                    .map(allocation ->  KeyworkerAllocationDetailsDto.builder()
                            .offenderNo(allocation.getOffenderNo())
                            .staffId(allocation.getStaffId())
                            .agencyId(allocation.getPrisonId()) //TODO: remove
                            .prisonId(allocation.getPrisonId())
                            .assigned(allocation.getAssignedDateTime())
                            .allocationType(allocation.getAllocationType())
                            .build())
                    .collect(Collectors.toList());
        } else {
            detailsDtoList = allocations.stream()
                    .map(allocation -> decorateWithOffenderDetails(prisonId, allocation))
                    //remove allocations from returned list that do not have associated booking records
                    .filter(dto -> dto.getBookingId() != null)
                    .sorted(Comparator.comparing(KeyworkerAllocationDetailsDto::getLastName))
                    .collect(Collectors.toList());
        }

        log.debug("Retrieved allocations for keyworker {}:\n{}", staffId, detailsDtoList);

        return detailsDtoList;
    }

    private KeyworkerAllocationDetailsDto decorateWithOffenderDetails(String prisonId, OffenderKeyworker allocation) {
        KeyworkerAllocationDetailsDto dto;
        URI uri = new UriTemplate(URI_ACTIVE_OFFENDER_BY_AGENCY).expand(prisonId, allocation.getOffenderNo());

        final ResponseEntity<List<OffenderLocationDto>> listOfOne = getForList(uri, OFFENDER_LOCATION_DTO_LIST);

        if (listOfOne.getBody().size() > 0) {
            final OffenderLocationDto offenderSummaryDto = listOfOne.getBody().get(0);
            dto = KeyworkerAllocationDetailsDto.builder()
                    .bookingId(offenderSummaryDto.getBookingId())
                    .offenderNo(allocation.getOffenderNo())
                    .firstName(offenderSummaryDto.getFirstName())
                    .middleNames(offenderSummaryDto.getMiddleName())
                    .lastName(offenderSummaryDto.getLastName())
                    .staffId(allocation.getStaffId())
                    .agencyId(allocation.getPrisonId()) //TODO: remove
                    .prisonId(allocation.getPrisonId())
                    .assigned(allocation.getAssignedDateTime())
                    .allocationType(allocation.getAllocationType())
                    .internalLocationDesc(offenderSummaryDto.getAssignedLivingUnitDesc())
                    .build();
        } else {
            log.error(format("Allocation does not have associated booking, removing from keyworker allocation list:\noffender %s in agency %s not found using elite endpoint %s", allocation.getOffenderNo(), prisonId, uri));
            dto =  KeyworkerAllocationDetailsDto.builder().build();
        }
        return dto;
    }

    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public Page<KeyworkerDto> getKeyworkers(String prisonId, Optional<String> nameFilter, PagingAndSortingDto pagingAndSorting) {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/staff/roles/{agencyId}/role/KW");
        nameFilter.ifPresent(filter -> uriBuilder.queryParam("nameFilter", filter));
        URI uri = uriBuilder.buildAndExpand(prisonId).toUri();

        ResponseEntity<List<StaffLocationRoleDto>> response = getWithPagingAndSorting(uri, pagingAndSorting, ELITE_STAFF_LOCATION_DTO_LIST);

        final List<KeyworkerDto> convertedKeyworkerDtoList = response.getBody().stream()
                .map(dto -> decorateWithKeyworkerData(ConversionHelper.getKeyworkerDto(dto))).collect(Collectors.toList());
        return new Page<>(convertedKeyworkerDtoList, response.getHeaders());
    }

    private KeyworkerDto decorateWithKeyworkerData(KeyworkerDto keyworkerDto) {
        final Keyworker keyworker = keyworkerRepository.findOne(keyworkerDto.getStaffId());
        final Integer allocationsCount = repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(keyworkerDto.getStaffId(), keyworkerDto.getAgencyId(), true, AllocationType.PROVISIONAL);

        keyworkerDto.setCapacity((keyworker != null && keyworker.getCapacity() != null) ? keyworker.getCapacity() : capacityDefault);
        keyworkerDto.setStatus(keyworker != null ? keyworker.getStatus() : KeyworkerStatus.ACTIVE);
        keyworkerDto.setNumberAllocated(allocationsCount);
        keyworkerDto.setAgencyId(keyworkerDto.getAgencyId());
        keyworkerDto.setAutoAllocationAllowed(keyworker != null ? keyworker.getAutoAllocationFlag() : true);
        return keyworkerDto;
    }


    @PreAuthorize("hasRole('ROLE_KW_ADMIN')")
    public void addOrUpdate(Long staffId, String prisonId, KeyworkerUpdateDto keyworkerUpdateDto) {

        Validate.notNull(staffId, "Missing staff id");
        Keyworker keyworker = keyworkerRepository.findOne(staffId);

        if (keyworker == null) {

            keyworkerRepository.save(Keyworker.builder()
                    .staffId(staffId)
                    .capacity(keyworkerUpdateDto.getCapacity())
                    .status(keyworkerUpdateDto.getStatus())
                    .autoAllocationFlag(true)
                    .build());

        } else {
            keyworker.setCapacity(keyworkerUpdateDto.getCapacity());
            keyworker.setStatus(keyworkerUpdateDto.getStatus());
            if (keyworkerUpdateDto.getStatus() == KeyworkerStatus.ACTIVE){
                keyworker.setAutoAllocationFlag(true);
            }
        }

        final KeyworkerStatusBehaviour behaviour = keyworkerUpdateDto.getBehaviour();
        if (behaviour != null) applyStatusChangeBehaviour(staffId, prisonId, behaviour);
    }

    private void applyStatusChangeBehaviour(Long staffId, String prisonId, KeyworkerStatusBehaviour behaviour) {

        if (behaviour.isRemoveAllocations()) {
            final LocalDateTime now = LocalDateTime.now();
            final List<OffenderKeyworker> allocations = repository.findByStaffIdAndPrisonIdAndActive(staffId, prisonId, true);
            allocations.forEach(ok -> {
                ok.setDeallocationReason(DeallocationReason.RELEASED);
                ok.setActive(false);
                ok.setExpiryDateTime(now);
            });
        }

        if (behaviour.isRemoveFromAutoAllocation()) {
            keyworkerRepository.findOne(staffId).setAutoAllocationFlag(false);
        }
    }
}
