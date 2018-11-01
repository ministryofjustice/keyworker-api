package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.WordUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.*;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus.ACTIVE;

@Service
@Transactional
@Validated
@Slf4j
public class KeyworkerService  {
    public static final String KEYWORKER_ENTRY_SUB_TYPE = "KE";
    public static final String KEYWORKER_SESSION_SUB_TYPE = "KS";
    public static final String KEYWORKER_CASENOTE_TYPE = "KA";

    private final AuthenticationFacade authenticationFacade;
    private final OffenderKeyworkerRepository repository;
    private final KeyworkerRepository keyworkerRepository;
    private final KeyworkerAllocationProcessor processor;
    private final PrisonSupportedService prisonSupportedService;
    private final NomisService nomisService;

    public KeyworkerService(AuthenticationFacade authenticationFacade,
                            OffenderKeyworkerRepository repository,
                            KeyworkerRepository keyworkerRepository,
                            KeyworkerAllocationProcessor processor,
                            PrisonSupportedService prisonSupportedService,
                            NomisService nomisService) {
        this.authenticationFacade = authenticationFacade;
        this.repository = repository;
        this.keyworkerRepository = keyworkerRepository;
        this.processor = processor;
        this.prisonSupportedService = prisonSupportedService;
        this.nomisService = nomisService;
    }

    public List<KeyworkerDto> getAvailableKeyworkers(String prisonId, boolean activeOnly) {

        final List<KeyworkerDto> returnedList = nomisService.getAvailableKeyworkers(prisonId);

        List<KeyworkerDto> availableKeyworkerList;

        if (prisonSupportedService.isMigrated(prisonId)) {
            final int prisonCapacityDefault = getPrisonCapacityDefault(prisonId);

            availableKeyworkerList = returnedList.stream()
                    .peek(k -> k.setAgencyId(prisonId))
                    .peek(k -> decorateWithKeyworkerData(k, prisonCapacityDefault))
                    .filter(k -> !activeOnly || k.getStatus() == ACTIVE)
                    .peek(this::decorateWithAllocationsCount)
                    .collect(Collectors.toList());
        } else {
            availableKeyworkerList = returnedList.stream()
                    .peek(k -> k.setAgencyId(prisonId))
                    .peek(this::decorateWithNomisKeyworkerData)
              .collect(Collectors.toList());
            populateWithAllocations(availableKeyworkerList, prisonId);
        }

        return availableKeyworkerList.stream()
                .sorted(Comparator.comparing(KeyworkerDto::getNumberAllocated)
                        .thenComparing(KeyworkerService::getKeyWorkerFullName))
                .collect(Collectors.toList());
    }

    private static String getKeyWorkerFullName(KeyworkerDto keyworkerDto) {
        return StringUtils.lowerCase(StringUtils.join(Arrays.asList(keyworkerDto.getLastName(), keyworkerDto.getFirstName()), " "));
    }

    public List<KeyworkerDto> getKeyworkersAvailableForAutoAllocation(String prisonId) {
        final List<KeyworkerDto> availableKeyworkers = getAvailableKeyworkers(prisonId, false);
        return availableKeyworkers.stream().filter(KeyworkerDto::getAutoAllocationAllowed).collect(Collectors.toList());
    }

    public Page<KeyworkerAllocationDetailsDto> getAllocations(AllocationsFilterDto allocationFilter, PagingAndSortingDto pagingAndSorting) {

        final String prisonId = allocationFilter.getPrisonId();
        final List<OffenderKeyworker> allocations =
                allocationFilter.getAllocationType().isPresent() ?
                        repository.findByActiveAndPrisonIdAndAllocationType(true, prisonId, allocationFilter.getAllocationType().get())
                        :
                        repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL);
        List<OffenderLocationDto> allOffenders = nomisService.getOffendersAtLocation(prisonId, pagingAndSorting.getSortFields(), pagingAndSorting.getSortOrder(), false);

        final List<KeyworkerAllocationDetailsDto> results = processor.decorateAllocated(allocations, allOffenders);

        return new Page<>(results, (long) allocations.size(), 0L, (long) allocations.size());
    }


    public List<OffenderLocationDto> getUnallocatedOffenders(String prisonId, String sortFields, SortOrder sortOrder) {

        prisonSupportedService.verifyPrisonMigrated(prisonId);
        List<OffenderLocationDto> allOffenders = nomisService.getOffendersAtLocation(prisonId, sortFields, sortOrder, false);
        return processor.filterByUnallocated(allOffenders);
    }


    public List<OffenderKeyworkerDto> getOffenderKeyworkerDetailList(String prisonId, Collection<String> offenderNos) {
        List<OffenderKeyworker> results = new ArrayList<>();
        if (prisonSupportedService.isMigrated(prisonId)) {
            results = CollectionUtils.isEmpty(offenderNos)
                            ? repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL)
                            : repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, prisonId, offenderNos, AllocationType.PROVISIONAL);
        } else {
            if (offenderNos.size() > 0) {
                final List<KeyworkerAllocationDetailsDto> allocations = nomisService.getCurrentAllocationsByOffenderNos(new ArrayList<>(offenderNos), prisonId);
                results = allocations.stream().map(ConversionHelper::getOffenderKeyworker).collect(Collectors.toList());
            }
        }
        return ConversionHelper.convertOffenderKeyworkerModel2Dto(results);
    }

    public Optional<BasicKeyworkerDto> getCurrentKeyworkerForPrisoner(String prisonId, String offenderNo) {
         BasicKeyworkerDto currentKeyworker = null;
        if (prisonSupportedService.isMigrated(prisonId)) {
            OffenderKeyworker activeOffenderKeyworker = repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, AllocationType.PROVISIONAL);
            if (activeOffenderKeyworker != null) {
                StaffLocationRoleDto staffDetail = nomisService.getBasicKeyworkerDtoForStaffId(activeOffenderKeyworker.getStaffId());
                if (staffDetail != null) {
                    currentKeyworker = BasicKeyworkerDto.builder()
                            .firstName(staffDetail.getFirstName())
                            .lastName(staffDetail.getLastName())
                            .staffId(staffDetail.getStaffId())
                            .email(staffDetail.getEmail())
                            .build();
                }
            }
        } else {
            currentKeyworker = nomisService.getBasicKeyworkerDtoForOffender(offenderNo);
        }
        return Optional.ofNullable(currentKeyworker);
    }

    public KeyworkerDto getKeyworkerDetails(String prisonId, Long staffId) {
        StaffLocationRoleDto staffKeyWorker = nomisService.getStaffKeyWorkerForPrison(prisonId, staffId).orElseGet(() -> nomisService.getBasicKeyworkerDtoForStaffId(staffId));
        final int prisonCapacityDefault = getPrisonCapacityDefault(prisonId);
        final KeyworkerDto keyworkerDto = ConversionHelper.getKeyworkerDto(staffKeyWorker);
        if (prisonSupportedService.isMigrated(prisonId)) {
            decorateWithKeyworkerData(keyworkerDto, prisonCapacityDefault);
            decorateWithAllocationsCount(keyworkerDto);
        } else {
            decorateWithNomisKeyworkerData(keyworkerDto);
            populateWithAllocations(Collections.singletonList(keyworkerDto), prisonId);
        }
        return keyworkerDto;
    }

    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void allocate(@Valid @NotNull KeyworkerAllocationDto keyworkerAllocation) {
        prisonSupportedService.verifyPrisonMigrated(keyworkerAllocation.getPrisonId());
        doAllocateValidation(keyworkerAllocation);
        doAllocate(keyworkerAllocation);
    }

    private void doAllocateValidation(KeyworkerAllocationDto keyworkerAllocation) {
        Validate.notBlank(keyworkerAllocation.getOffenderNo(), "Missing prisoner number.");
        Validate.notNull(keyworkerAllocation.getStaffId(), "Missing staff id.");

        Optional<OffenderLocationDto> offender = nomisService.getOffenderForPrison(keyworkerAllocation.getPrisonId(), keyworkerAllocation.getOffenderNo());

        Validate.isTrue(offender.isPresent(), format("Prisoner %s not found at agencyId %s",
                keyworkerAllocation.getOffenderNo(), keyworkerAllocation.getPrisonId()));

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
    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
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


    public List<OffenderKeyworker> getAllocationHistoryForPrisoner(String offenderNo) {
        return repository.findByOffenderNo(offenderNo);
    }

    public Optional<OffenderKeyWorkerHistory> getFullAllocationHistory(String offenderNo) {
        final List<OffenderKeyworker> keyworkers = repository.findByOffenderNo(offenderNo);

        List<KeyWorkerAllocation> keyWorkerAllocations;

        // get distinct list of prisons that have been migrated for this offender
        final List<String> prisonsMigrated = keyworkers.stream().map(OffenderKeyworker::getPrisonId).distinct().collect(Collectors.toList());

        // get the allocations that are in nomis for other prisons
        List<KeyWorkerAllocation> allocations =
                nomisService.getAllocationHistoryByOffenderNos(Collections.singletonList(offenderNo))
                .stream()
                .filter(a -> !prisonsMigrated.contains(a.getAgencyId()))
                .map(kw -> {
                    StaffLocationRoleDto staffKw = nomisService.getBasicKeyworkerDtoForStaffId(kw.getStaffId());

                    return KeyWorkerAllocation.builder()
                                .firstName(staffKw.getFirstName())
                                .lastName(staffKw.getLastName())
                                .staffId(kw.getStaffId())
                                .active(kw.getActive().equals("Y"))
                                .allocationType(AllocationType.MANUAL)
                                .allocationReason(WordUtils.capitalizeFully(AllocationReason.MANUAL.getReasonCode()))
                                .assigned(kw.getAssigned())
                                .expired(kw.getExpired())
                                .prisonId(kw.getAgencyId())
                                .userId(nomisService.getStaffDetailByUserId(kw.getUserId()))
                                .createdByUser(nomisService.getStaffDetailByUserId(kw.getCreatedBy()))
                                .creationDateTime(kw.getCreated())
                                .lastModifiedByUser(nomisService.getStaffDetailByUserId(kw.getModifiedBy()))
                                .modifyDateTime(kw.getModified())
                                .build(); }
                                )
                .collect(Collectors.toList());

        if (!keyworkers.isEmpty()) {
            allocations.addAll(keyworkers.stream()
                    .filter(kw -> kw.getAllocationType() != AllocationType.PROVISIONAL)
                    .map(
                            kw -> {
                                StaffLocationRoleDto staffKw = nomisService.getBasicKeyworkerDtoForStaffId(kw.getStaffId());

                                String deallocationReason = WordUtils.capitalizeFully(StringUtils.replaceAll(kw.getDeallocationReason() != null ? kw.getDeallocationReason().getReasonCode() : null, "_", " "));
                                return KeyWorkerAllocation.builder()
                                        .offenderKeyworkerId(kw.getOffenderKeyworkerId())
                                        .firstName(staffKw.getFirstName())
                                        .lastName(staffKw.getLastName())
                                        .staffId(kw.getStaffId())
                                        .active(kw.isActive())
                                        .allocationType(kw.getAllocationType())
                                        .allocationReason(WordUtils.capitalizeFully(kw.getAllocationReason().getReasonCode()))
                                        .assigned(kw.getAssignedDateTime())
                                        .expired(kw.getExpiryDateTime())
                                        .deallocationReason(deallocationReason)
                                        .prisonId(kw.getPrisonId())
                                        .userId(nomisService.getStaffDetailByUserId(kw.getUserId()))
                                        .createdByUser(nomisService.getStaffDetailByUserId(kw.getCreateUserId()))
                                        .creationDateTime(kw.getCreationDateTime())
                                        .lastModifiedByUser(nomisService.getStaffDetailByUserId(kw.getModifyUserId()))
                                        .modifyDateTime(kw.getModifyDateTime())
                                        .build();
                            }

                    ).collect(Collectors.toList()));
        }

        keyWorkerAllocations = allocations.stream()
                .sorted(Comparator
                .comparing(KeyWorkerAllocation::getAssigned).reversed())
                .collect(Collectors.toList());
        // use prison for most recent allocation
        PrisonerDetail prisonerDetail = nomisService.getPrisonerDetail(offenderNo).orElseThrow(EntityNotFoundException::new);

        OffenderKeyWorkerHistory offenderKeyWorkerHistory = OffenderKeyWorkerHistory.builder()
                .offender(prisonerDetail)
                .allocationHistory(keyWorkerAllocations)
                .build();

        return Optional.ofNullable(offenderKeyWorkerHistory);
    }

    public List<OffenderKeyworker> getAllocationsForKeyworker(Long staffId) {
        return repository.findByStaffId(staffId);
    }

    public List<KeyworkerAllocationDetailsDto> getAllocationsForKeyworkerWithOffenderDetails(String prisonId, Long staffId, boolean skipOffenderDetails) {
        final List<KeyworkerAllocationDetailsDto> detailsDtoList;
        if (prisonSupportedService.isMigrated(prisonId)) {
            final List<OffenderKeyworker> allocations = repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, prisonId, true, AllocationType.PROVISIONAL);

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
                        //remove allocations from returned list that do not have associated booking records - these are movements or merges
                        .filter(dto -> dto.getBookingId() != null)
                        .sorted(Comparator
                                .comparing(KeyworkerAllocationDetailsDto::getLastName)
                                .thenComparing(KeyworkerAllocationDetailsDto::getFirstName))
                        .collect(Collectors.toList());
            }
        } else {
            detailsDtoList = nomisService.getCurrentAllocations(Collections.singletonList(staffId), prisonId);

        }

        log.debug("Retrieved allocations for keyworker {}:\n{}", staffId, detailsDtoList);

        return detailsDtoList;
    }

    private KeyworkerAllocationDetailsDto decorateWithOffenderDetails(String prisonId, OffenderKeyworker allocation) {
        KeyworkerAllocationDetailsDto dto;

        Optional<PrisonerDetail> prisoner = nomisService.getPrisonerDetail(allocation.getOffenderNo());

        if (prisoner.isPresent()) {
            final PrisonerDetail offenderSummaryDto = prisoner.get();
            boolean samePrison = allocation.getPrisonId().equals(offenderSummaryDto.getLatestLocationId());
            dto = KeyworkerAllocationDetailsDto.builder()
                    .bookingId(offenderSummaryDto.getLatestBookingId())
                    .offenderNo(allocation.getOffenderNo())
                    .firstName(offenderSummaryDto.getFirstName())
                    .middleNames(offenderSummaryDto.getMiddleNames())
                    .lastName(offenderSummaryDto.getLastName())
                    .staffId(allocation.getStaffId())
                    .agencyId(offenderSummaryDto.getLatestLocationId()) //TODO: remove
                    .prisonId(offenderSummaryDto.getLatestLocationId())
                    .assigned(allocation.getAssignedDateTime())
                    .allocationType(allocation.getAllocationType())
                    .internalLocationDesc(samePrison ? stripAgencyId(offenderSummaryDto.getInternalLocation(), allocation.getPrisonId()) : offenderSummaryDto.getInternalLocation())
                    .deallocOnly(!samePrison)
                    .build();
        } else {
            log.error(format("Allocation does not have associated booking, removing from keyworker allocation list:\noffender %s in agency %s not found using nomis service", allocation.getOffenderNo(), prisonId));
            dto =  KeyworkerAllocationDetailsDto.builder().build();
        }
        return dto;
    }

    public static String stripAgencyId(String description, String agencyId) {
        if (StringUtils.isBlank(agencyId)) {
            return description;
        }

        return StringUtils.replaceFirst(description,StringUtils.trimToEmpty(agencyId) + "-", "");
    }

    public Page<KeyworkerDto> getKeyworkers(String prisonId, Optional<String> nameFilter, Optional<KeyworkerStatus> statusFilter, PagingAndSortingDto pagingAndSorting) {

        ResponseEntity<List<StaffLocationRoleDto>> response = nomisService.getActiveStaffKeyWorkersForPrison(prisonId, nameFilter, pagingAndSorting, false);
        final int prisonCapacityDefault = getPrisonCapacityDefault(prisonId);

        final List<KeyworkerDto> convertedKeyworkerDtoList = new ArrayList<>();
        Prison prisonDetail = prisonSupportedService.getPrisonDetail(prisonId);
        if (prisonDetail.isMigrated()) {
            convertedKeyworkerDtoList.addAll(response.getBody().stream().distinct()
                    .map(ConversionHelper::getKeyworkerDto)
                    .peek(k -> decorateWithKeyworkerData(k, prisonCapacityDefault))
                    .filter(t -> !statusFilter.isPresent() || t.getStatus() == statusFilter.get())
                    .peek(this::decorateWithAllocationsCount)
                    .collect(Collectors.toList()));
        } else {
            convertedKeyworkerDtoList.addAll(response.getBody().stream().distinct()
                    .map(ConversionHelper::getKeyworkerDto)
                    .peek(this::decorateWithNomisKeyworkerData)
                    .filter(t -> !statusFilter.isPresent() || t.getStatus() == statusFilter.get())
                    .collect(Collectors.toList()));

           populateWithAllocations(convertedKeyworkerDtoList, prisonId);
        }
        populateWithCaseNoteCounts(convertedKeyworkerDtoList);

        List<KeyworkerDto> keyworkers = convertedKeyworkerDtoList.stream()
                .sorted(Comparator
                        .comparing(KeyworkerDto::getStatus)
                        .thenComparing(KeyworkerDto::getNumberAllocated)
                        .thenComparing(KeyworkerDto::getLastName)
                        .thenComparing(KeyworkerDto::getFirstName))
                .collect(Collectors.toList());


        return new Page<>(keyworkers, response.getHeaders());
    }

    private void populateWithAllocations(List<KeyworkerDto> convertedKeyworkerDtoList, String prisonId) {
        List<Long> staffIds = convertedKeyworkerDtoList.stream().map(KeyworkerDto::getStaffId).collect(Collectors.toList());

        if (staffIds.size() > 0) {
            List<KeyworkerAllocationDetailsDto> allocations = nomisService.getCurrentAllocations(staffIds, prisonId);

            Map<Long, Long> allocationMap = allocations.stream()
                    .collect(Collectors.groupingBy(KeyworkerAllocationDetailsDto::getStaffId,
                            Collectors.counting()));

            convertedKeyworkerDtoList
                    .forEach(kw -> {
                        Long numberAllocated = allocationMap.get(kw.getStaffId());
                        kw.setNumberAllocated(numberAllocated != null ? numberAllocated.intValue() : 0);
                    });
        }
    }

    private void populateWithCaseNoteCounts(List<KeyworkerDto> convertedKeyworkerDtoList) {
        List<Long> staffIds = convertedKeyworkerDtoList.stream().map(KeyworkerDto::getStaffId).collect(Collectors.toList());

        if (staffIds.size() > 0) {
            final Map<Long, Integer> kwStats = getCaseNoteUsageByStaffId(staffIds);

            convertedKeyworkerDtoList
                    .forEach(kw -> {
                        Integer numCaseNotes = kwStats.get(kw.getStaffId());
                        kw.setNumKeyWorkerSessions(numCaseNotes != null ? numCaseNotes : 0);
            });
        }
    }

    private Map<Long, Integer> getCaseNoteUsageByStaffId(List<Long> activeStaffIds) {
        List<CaseNoteUsageDto> caseNoteUsage = nomisService.getCaseNoteUsage(activeStaffIds, KEYWORKER_CASENOTE_TYPE, KEYWORKER_SESSION_SUB_TYPE, null, null, 1);

        return caseNoteUsage.stream()
                .collect(Collectors.groupingBy(CaseNoteUsageDto::getStaffId,
                        Collectors.summingInt(CaseNoteUsageDto::getNumCaseNotes)));
    }

    private int getPrisonCapacityDefault(String prisonId) {
        Prison prisonDetail = prisonSupportedService.getPrisonDetail(prisonId);
        return prisonDetail != null ? prisonDetail.getCapacityTier1() : 0;
    }

    private void decorateWithKeyworkerData(KeyworkerDto keyworkerDto, int capacityDefault) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            final Keyworker keyworker = keyworkerRepository.findOne(keyworkerDto.getStaffId());

            keyworkerDto.setCapacity((keyworker != null && keyworker.getCapacity() != null) ? keyworker.getCapacity() : capacityDefault);
            keyworkerDto.setStatus(keyworker != null ? keyworker.getStatus() : KeyworkerStatus.ACTIVE);
            keyworkerDto.setAgencyId(keyworkerDto.getAgencyId());
            keyworkerDto.setAutoAllocationAllowed(keyworker != null ? keyworker.getAutoAllocationFlag() : true);
            if (keyworker != null) {
                keyworkerDto.setActiveDate(keyworker.getActiveDate());
            }
        }
    }

    private void decorateWithNomisKeyworkerData(KeyworkerDto keyworkerDto) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            keyworkerDto.setStatus(KeyworkerStatus.ACTIVE);
            keyworkerDto.setAutoAllocationAllowed(false);
            keyworkerDto.setNumberAllocated(0);
        }
    }

    private void decorateWithAllocationsCount(KeyworkerDto keyworkerDto) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            final Integer allocationsCount = repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(keyworkerDto.getStaffId(), keyworkerDto.getAgencyId(), true, AllocationType.PROVISIONAL);
            keyworkerDto.setNumberAllocated(allocationsCount);
        }
    }

    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void addOrUpdate(Long staffId, String prisonId, KeyworkerUpdateDto keyworkerUpdateDto) {

        prisonSupportedService.verifyPrisonMigrated(prisonId);
        Validate.notNull(staffId, "Missing staff id");
        Keyworker keyworker = keyworkerRepository.findOne(staffId);

        if (keyworker == null) {

            keyworkerRepository.save(Keyworker.builder()
                    .staffId(staffId)
                    .capacity(keyworkerUpdateDto.getCapacity())
                    .status(keyworkerUpdateDto.getStatus())
                    .autoAllocationFlag(true)
                    .activeDate(keyworkerUpdateDto.getActiveDate())
                    .build());

        } else {
            keyworker.setCapacity(keyworkerUpdateDto.getCapacity());
            keyworker.setStatus(keyworkerUpdateDto.getStatus());
            keyworker.setActiveDate(keyworkerUpdateDto.getActiveDate());
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
                ok.setDeallocationReason(DeallocationReason.KEYWORKER_STATUS_CHANGE);
                ok.setActive(false);
                ok.setExpiryDateTime(now);
            });
        }

        if (behaviour.isRemoveFromAutoAllocation()) {
            keyworkerRepository.findOne(staffId).setAutoAllocationFlag(false);
        }
    }

    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void deallocate(String offenderNo) {
        final List<OffenderKeyworker> offenderKeyworkers = repository.findByActiveAndOffenderNo(true, offenderNo);

        if (offenderKeyworkers.isEmpty()) {
            throw new EntityNotFoundException(String.format("Offender No %s not allocated or does not exist", offenderNo));
        }

        // There shouldnt ever be more than 1, but just in case
        final LocalDateTime now = LocalDateTime.now();
        offenderKeyworkers.forEach(offenderKeyworker -> {
            offenderKeyworker.deallocate(now, DeallocationReason.MANUAL);
            log.info("De-allocated offender {} from KW {} at {}", offenderNo, offenderKeyworker.getStaffId(), offenderKeyworker.getPrisonId());
        });
    }

}
