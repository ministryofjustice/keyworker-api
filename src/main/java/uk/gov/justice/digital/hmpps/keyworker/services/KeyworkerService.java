package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.WordUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationHistoryDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationsFilterDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyWorkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatusBehaviour;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerUpdateDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistory;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistorySummary;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Page;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.LocalDate.now;
import static uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus.ACTIVE;

@Service
@Validated
@Slf4j
@AllArgsConstructor
public class KeyworkerService {
    public static final String KEYWORKER_ENTRY_SUB_TYPE = "KE";
    public static final String KEYWORKER_SESSION_SUB_TYPE = "KS";
    public static final String KEYWORKER_CASENOTE_TYPE = "KA";
    public static final String TRANSFER_CASENOTE_TYPE = "TRANSFER";

    private final AuthenticationFacade authenticationFacade;
    private final OffenderKeyworkerRepository repository;
    private final LegacyKeyworkerRepository keyworkerRepository;
    private final KeyworkerAllocationProcessor processor;
    private final PrisonSupportedService prisonSupportedService;
    private final NomisService nomisService;
    private final ComplexityOfNeed complexityOfNeedService;

    public List<KeyworkerDto> getAvailableKeyworkers(final String prisonId, final boolean activeOnly) {

        final var returnedList = nomisService.getAvailableKeyworkers(prisonId);

        final List<KeyworkerDto> availableKeyworkerList;

        if (prisonSupportedService.isMigrated(prisonId)) {
            final var prisonCapacityDefault = getPrisonCapacityDefault(prisonId);

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

    private static String getKeyWorkerFullName(final KeyworkerDto keyworkerDto) {
        return StringUtils.lowerCase(StringUtils.join(List.of(keyworkerDto.getLastName(), keyworkerDto.getFirstName()), " "));
    }

    public List<KeyworkerDto> getKeyworkersAvailableForAutoAllocation(final String prisonId) {
        final var availableKeyworkers = getAvailableKeyworkers(prisonId, false);
        return availableKeyworkers.stream().filter(KeyworkerDto::getAutoAllocationAllowed).collect(Collectors.toList());
    }

    public Page<KeyworkerAllocationDetailsDto> getAllocations(final AllocationsFilterDto allocationFilter, final PagingAndSortingDto pagingAndSorting) {

        final var prisonId = allocationFilter.getPrisonId();
        final var allocations =
            allocationFilter.getAllocationType().isPresent() ?
                repository.findByActiveAndPrisonIdAndAllocationType(true, prisonId, allocationFilter.getAllocationType().get())
                :
                repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL);
        final var allOffenders = nomisService.getOffendersAtLocation(prisonId, pagingAndSorting.getSortFields(), pagingAndSorting.getSortOrder(), false);

        final var results = processor.decorateAllocated(allocations, allOffenders);

        return new Page<>(results, allocations.size(), 0L, allocations.size());
    }


    public List<OffenderLocationDto> getUnallocatedOffenders(final String prisonId, final String sortFields, final SortOrder sortOrder) {
        prisonSupportedService.verifyPrisonMigrated(prisonId);

        final var offenders = removeOffendersWithHighComplexityOfNeed(
            prisonId,
            nomisService.getOffendersAtLocation(prisonId, sortFields, sortOrder, false)
        );

        return  processor.filterByUnallocated(offenders);
    }

    private List<OffenderLocationDto> removeOffendersWithHighComplexityOfNeed(final String prisonId, final List<OffenderLocationDto> unAllocated) {
        final var unAllocatedOffenderNos = unAllocated.stream()
            .map(OffenderLocationDto::getOffenderNo)
            .collect(Collectors.toSet());

        final var nonHighComplexOffenders = complexityOfNeedService.removeOffendersWithHighComplexityOfNeed(prisonId, unAllocatedOffenderNos);

        return unAllocated.stream()
            .filter(offenderLocation -> nonHighComplexOffenders.contains(offenderLocation.getOffenderNo()))
            .collect(Collectors.toList());
    }


    public List<OffenderKeyworkerDto> getOffenderKeyworkerDetailList(final String prisonId, final Collection<String> offenderNos) {
        List<OffenderKeyworker> results = new ArrayList<>();
        if (prisonSupportedService.isMigrated(prisonId)) {
            results = CollectionUtils.isEmpty(offenderNos)
                ? repository.findByActiveAndPrisonIdAndAllocationTypeIsNot(true, prisonId, AllocationType.PROVISIONAL)
                : repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, prisonId, offenderNos, AllocationType.PROVISIONAL);
        } else {
            if (offenderNos.size() > 0) {
                final var allocations = nomisService.getCurrentAllocationsByOffenderNos(new ArrayList<>(offenderNos), prisonId);
                results = allocations.stream().map(ConversionHelper.INSTANCE::getOffenderKeyworker).collect(Collectors.toList());
            }
        }
        return ConversionHelper.INSTANCE.convertOffenderKeyworkerModel2Dto(results);
    }

    public Optional<BasicKeyworkerDto> getCurrentKeyworkerForPrisoner(final String offenderNo) {
        final var activeOffenderKeyworkers = repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, AllocationType.PROVISIONAL);

        if (!activeOffenderKeyworkers.isEmpty()) {
            // this should be a single record - but can have duplicates - get latest
            final var latestKw = activeOffenderKeyworkers.stream().max(Comparator.comparing(OffenderKeyworker::getCreationDateTime)).orElseThrow();

            if (activeOffenderKeyworkers.size() > 1) {
                // de-allocate dups
                activeOffenderKeyworkers.stream()
                    .filter(kw -> !latestKw.getOffenderKeyworkerId().equals(kw.getOffenderKeyworkerId()))
                    .forEach(kw -> kw.deallocate(LocalDateTime.now(), DeallocationReason.DUP));
            }
            final var staffDetail = nomisService.getBasicKeyworkerDtoForStaffId(latestKw.getStaffId());
            if (staffDetail != null) {
                return Optional.of(BasicKeyworkerDto.builder()
                    .firstName(staffDetail.getFirstName())
                    .lastName(staffDetail.getLastName())
                    .staffId(staffDetail.getStaffId())
                    .email(staffDetail.getEmail())
                    .build());

            }
            return Optional.empty();
        }
        final var detail = nomisService.getPrisonerDetail(offenderNo, false);
        final boolean isMigrated = detail.map(PrisonerDetail::getLatestLocationId).map(prisonSupportedService::isMigrated).orElse(true);
        // we don't want to fallback to nomis for migrated prisons or if offender not found
        if (isMigrated) {
            return Optional.empty();
        }
        return Optional.of(nomisService.getBasicKeyworkerDtoForOffender(offenderNo));
    }

    public KeyworkerDto getKeyworkerDetails(final String prisonId, final Long staffId) {
        final var staffKeyWorker = nomisService.getStaffKeyWorkerForPrison(prisonId, staffId).orElseGet(() -> nomisService.getBasicKeyworkerDtoForStaffId(staffId));
        final var prisonCapacityDefault = getPrisonCapacityDefault(prisonId);
        final var keyworkerDto = ConversionHelper.INSTANCE.getKeyworkerDto(staffKeyWorker);
        if (prisonSupportedService.isMigrated(prisonId)) {
            decorateWithKeyworkerData(keyworkerDto, prisonCapacityDefault);
            decorateWithAllocationsCount(keyworkerDto);
        } else {
            decorateWithNomisKeyworkerData(keyworkerDto);
            populateWithAllocations(Collections.singletonList(keyworkerDto), prisonId);
        }
        return keyworkerDto;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void allocate(@Valid @NotNull final KeyworkerAllocationDto keyworkerAllocation) {
        prisonSupportedService.verifyPrisonMigrated(keyworkerAllocation.getPrisonId());
        doAllocateValidation(keyworkerAllocation);
        doAllocate(keyworkerAllocation);
    }

    private void doAllocateValidation(final KeyworkerAllocationDto keyworkerAllocation) {
        Validate.notBlank(keyworkerAllocation.getOffenderNo(), "Missing prisoner number.");
        Validate.notNull(keyworkerAllocation.getStaffId(), "Missing staff id.");

        final var offender = nomisService.getOffenderForPrison(keyworkerAllocation.getPrisonId(), keyworkerAllocation.getOffenderNo());

        Validate.isTrue(offender.isPresent(), format("Prisoner %s not found at agencyId %s",
            keyworkerAllocation.getOffenderNo(), keyworkerAllocation.getPrisonId()));

        final var keyworkerDetails = getKeyworkerDetails(keyworkerAllocation.getPrisonId(), keyworkerAllocation.getStaffId());
        Validate.notNull(keyworkerDetails, format("Keyworker %d not found at agencyId %s.",
            keyworkerAllocation.getStaffId(), keyworkerAllocation.getPrisonId()));
    }

    private void doAllocate(final KeyworkerAllocationDto newAllocation) {

        // Remove current allocation if any
        final var entities = repository.findByActiveAndOffenderNo(
            true, newAllocation.getOffenderNo());
        final var now = LocalDateTime.now();
        entities.forEach(e -> {
            e.setActive(false);
            e.setExpiryDateTime(now);
            e.setDeallocationReason(newAllocation.getDeallocationReason());
        });

        final var allocation = ConversionHelper.INSTANCE.getOffenderKeyworker(newAllocation, authenticationFacade.getCurrentUsername());

        allocate(allocation);
    }

    /**
     * Creates a new offender - Key worker allocation record.
     *
     * @param allocation allocation details.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void allocate(final OffenderKeyworker allocation) {
        Validate.notNull(allocation);

        // This service method creates a new allocation record, therefore it will apply certain defaults automatically.
        final var now = LocalDateTime.now();

        allocation.setActive(true);
        allocation.setAssignedDateTime(now);

        if (StringUtils.isBlank(allocation.getUserId())) {
            allocation.setUserId(authenticationFacade.getCurrentUsername());
        }

        repository.save(allocation);
    }


    public List<OffenderKeyworker> getAllocationHistoryForPrisoner(final String offenderNo) {
        return repository.findByOffenderNo(offenderNo);
    }

    public Optional<OffenderKeyWorkerHistory> getFullAllocationHistory(final String offenderNo) {
        final var keyworkers = repository.findByOffenderNo(offenderNo);

        final List<KeyWorkerAllocation> keyWorkerAllocations;

        // get distinct list of prisons that have been migrated for this offender
        final var prisonsMigrated = keyworkers.stream().map(OffenderKeyworker::getPrisonId).distinct().toList();

        // get the allocations that are in nomis for other prisons
        final var allocations =
            nomisService.getAllocationHistoryByOffenderNos(Collections.singletonList(offenderNo))
                .stream()
                .filter(a -> !prisonsMigrated.contains(a.getAgencyId()))
                .map(kw -> {
                        var staffKw = nomisService.getBasicKeyworkerDtoForStaffId(kw.getStaffId());

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
                            .build();
                    }
                )
                .collect(Collectors.toList());

        if (!keyworkers.isEmpty()) {
            allocations.addAll(keyworkers.stream()
                .filter(kw -> kw.getAllocationType() != AllocationType.PROVISIONAL)
                .map(
                    kw -> {
                        var staffKw = nomisService.getBasicKeyworkerDtoForStaffId(kw.getStaffId());

                        var deallocationReason = WordUtils.capitalizeFully(RegExUtils.replaceAll(kw.getDeallocationReason() != null ? kw.getDeallocationReason().getReasonCode() : null, "_", " "));
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
        final var prisonerDetail = nomisService.getPrisonerDetail(offenderNo, false).orElseThrow(EntityNotFoundException::new);

        final var offenderKeyWorkerHistory = OffenderKeyWorkerHistory.builder()
            .offender(prisonerDetail)
            .allocationHistory(keyWorkerAllocations)
            .build();

        return Optional.ofNullable(offenderKeyWorkerHistory);
    }

    public List<OffenderKeyWorkerHistorySummary> getAllocationHistorySummary(final List<String> offenderNos) {
        final var migratedOffenderNosWithHistory = repository.findByOffenderNoIn(offenderNos).stream()
            .filter(kw -> kw.getAllocationType() != AllocationType.PROVISIONAL)
            .map(OffenderKeyworker::getOffenderNo)
            .collect(Collectors.toSet());

        // get the allocations that are in nomis for non-migrated prisons
        final var nomisOffenderNosWithHistory =
            nomisService.getAllocationHistoryByOffenderNos(offenderNos).stream()
                .map(AllocationHistoryDto::getOffenderNo)
                .collect(Collectors.toSet());

        return offenderNos.stream().map(o -> OffenderKeyWorkerHistorySummary.builder()
            .offenderNo(o)
            .hasHistory(migratedOffenderNosWithHistory.contains(o) || nomisOffenderNosWithHistory.contains(o))
            .build()).collect(Collectors.toList());
    }

    public List<OffenderKeyworker> getAllocationsForKeyworker(final Long staffId) {
        return repository.findByStaffId(staffId);
    }

    public List<KeyworkerAllocationDetailsDto> getAllocationsForKeyworkerWithOffenderDetails(final String prisonId, final Long staffId, final boolean skipOffenderDetails) {
        final List<KeyworkerAllocationDetailsDto> detailsDtoList;
        if (prisonSupportedService.isMigrated(prisonId)) {
            final var allocations = repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, prisonId, true, AllocationType.PROVISIONAL);

            if (skipOffenderDetails) {
                detailsDtoList = allocations.stream()
                    .map(allocation -> KeyworkerAllocationDetailsDto.builder()
                        .offenderNo(allocation.getOffenderNo())
                        .staffId(allocation.getStaffId())
                        .agencyId(allocation.getPrisonId()) //TODO: remove
                        .prisonId(allocation.getPrisonId())
                        .assigned(allocation.getAssignedDateTime())
                        .allocationType(allocation.getAllocationType())
                        .build())
                    .collect(Collectors.toList());
            } else {

                final var offenderNos = allocations.stream().map(OffenderKeyworker::getOffenderNo).collect(Collectors.toList());
                final var prisonerDetailMap = nomisService.getPrisonerDetails(offenderNos, true).stream()
                    .collect(Collectors.toMap(PrisonerDetail::getOffenderNo, prisoner -> prisoner));

                detailsDtoList = allocations.stream()
                    .map(allocation -> decorateWithOffenderDetails(allocation, prisonerDetailMap.get(allocation.getOffenderNo())))
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

    private KeyworkerAllocationDetailsDto decorateWithOffenderDetails(final OffenderKeyworker allocation, final PrisonerDetail prisonerDetail) {
        if (prisonerDetail == null) {
            log.error(format("Allocation does not have associated booking, removing from keyworker allocation list:\noffender %s in agency %s not found using nomis service", allocation.getOffenderNo(), allocation.getPrisonId()));
            return KeyworkerAllocationDetailsDto.builder().build();
        }
        final var samePrison = allocation.getPrisonId().equals(prisonerDetail.getLatestLocationId());
        return KeyworkerAllocationDetailsDto.builder()
            .bookingId(prisonerDetail.getLatestBookingId())
            .offenderNo(allocation.getOffenderNo())
            .firstName(prisonerDetail.getFirstName())
            .middleNames(prisonerDetail.getMiddleNames())
            .lastName(prisonerDetail.getLastName())
            .staffId(allocation.getStaffId())
            .agencyId(prisonerDetail.getLatestLocationId()) //TODO: remove
            .prisonId(prisonerDetail.getLatestLocationId())
            .assigned(allocation.getAssignedDateTime())
            .allocationType(allocation.getAllocationType())
            .internalLocationDesc(samePrison ? stripAgencyId(prisonerDetail.getInternalLocation(), allocation.getPrisonId()) : prisonerDetail.getInternalLocation())
            .deallocOnly(!samePrison)
            .build();
    }

    public static String stripAgencyId(final String description, final String agencyId) {
        if (StringUtils.isBlank(agencyId)) {
            return description;
        }

        return RegExUtils.replaceFirst(description, StringUtils.trimToEmpty(agencyId) + "-", "");
    }

    public Page<KeyworkerDto> getKeyworkers(final String prisonId, final Optional<String> nameFilter, final Optional<KeyworkerStatus> statusFilter, final PagingAndSortingDto pagingAndSorting) {

        final var response = nomisService.getActiveStaffKeyWorkersForPrison(prisonId, nameFilter, pagingAndSorting, false);
        final var prisonCapacityDefault = getPrisonCapacityDefault(prisonId);

        final List<KeyworkerDto> convertedKeyworkerDtoList = new ArrayList<>();
        final var prisonDetail = prisonSupportedService.getPrisonDetail(prisonId);
        if (prisonDetail.isMigrated()) {
            convertedKeyworkerDtoList.addAll(Objects.requireNonNull(response.getBody()).stream().distinct()
                .map(ConversionHelper.INSTANCE::getKeyworkerDto)
                .peek(k -> decorateWithKeyworkerData(k, prisonCapacityDefault))
                .filter(t -> statusFilter.isEmpty() || t.getStatus() == statusFilter.get())
                .peek(this::decorateWithAllocationsCount)
                .collect(Collectors.toList()));
        } else {
            convertedKeyworkerDtoList.addAll(Objects.requireNonNull(response.getBody()).stream().distinct()
                .map(ConversionHelper.INSTANCE::getKeyworkerDto)
                .peek(this::decorateWithNomisKeyworkerData)
                .filter(t -> statusFilter.isEmpty() || t.getStatus() == statusFilter.get())
                .collect(Collectors.toList()));

            populateWithAllocations(convertedKeyworkerDtoList, prisonId);
        }
        populateWithCaseNoteCounts(convertedKeyworkerDtoList);

        final var keyworkers = convertedKeyworkerDtoList.stream()
            .sorted(Comparator
                .comparing(KeyworkerDto::getStatus)
                .thenComparing(KeyworkerDto::getNumberAllocated)
                .thenComparing(KeyworkerDto::getLastName)
                .thenComparing(KeyworkerDto::getFirstName))
            .collect(Collectors.toList());


        return new Page<>(keyworkers, response.getHeaders());
    }

    private void populateWithAllocations(final List<KeyworkerDto> convertedKeyworkerDtoList, final String prisonId) {
        final var staffIds = convertedKeyworkerDtoList.stream().map(KeyworkerDto::getStaffId).collect(Collectors.toList());

        if (staffIds.size() > 0) {
            final var allocations = nomisService.getCurrentAllocations(staffIds, prisonId);

            final var allocationMap = allocations.stream()
                .collect(Collectors.groupingBy(KeyworkerAllocationDetailsDto::getStaffId,
                    Collectors.counting()));

            convertedKeyworkerDtoList
                .forEach(kw -> {
                    final var numberAllocated = allocationMap.get(kw.getStaffId());
                    kw.setNumberAllocated(numberAllocated != null ? numberAllocated.intValue() : 0);
                });
        }
    }

    private void populateWithCaseNoteCounts(final List<KeyworkerDto> convertedKeyworkerDtoList) {
        final var staffIds = convertedKeyworkerDtoList.stream().map(KeyworkerDto::getStaffId).collect(Collectors.toList());

        if (staffIds.size() > 0) {
            final var kwStats = getCaseNoteUsageByStaffId(staffIds);

            convertedKeyworkerDtoList
                .forEach(kw -> {
                    final var numCaseNotes = kwStats.get(kw.getStaffId());
                    kw.setNumKeyWorkerSessions(numCaseNotes != null ? numCaseNotes : 0);
                });
        }
    }

    private Map<Long, Integer> getCaseNoteUsageByStaffId(final List<Long> activeStaffIds) {
        final var caseNoteUsage = nomisService.getCaseNoteUsage(
            activeStaffIds,
            KEYWORKER_CASENOTE_TYPE,
            KEYWORKER_SESSION_SUB_TYPE,
            now().minusMonths(1),
            now()
        );

        return caseNoteUsage.stream()
            .collect(Collectors.groupingBy(CaseNoteUsageDto::getStaffId,
                Collectors.summingInt(CaseNoteUsageDto::getNumCaseNotes)));
    }

    private int getPrisonCapacityDefault(final String prisonId) {
        final var prisonDetail = prisonSupportedService.getPrisonDetail(prisonId);
        return prisonDetail != null ? prisonDetail.getCapacityTier1() : 0;
    }

    private void decorateWithKeyworkerData(final KeyworkerDto keyworkerDto, final int capacityDefault) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            keyworkerRepository.findById(keyworkerDto.getStaffId())
                .ifPresentOrElse(
                    keyworker -> {
                        keyworkerDto.setCapacity(keyworker.getCapacity() != null ? keyworker.getCapacity() : capacityDefault);
                        keyworkerDto.setStatus(keyworker.getStatus());
                        keyworkerDto.setAgencyId(keyworkerDto.getAgencyId());
                        keyworkerDto.setAutoAllocationAllowed(keyworker.getAutoAllocationFlag());
                        keyworkerDto.setActiveDate(keyworker.getActiveDate());
                    },
                    () -> {
                        keyworkerDto.setCapacity(capacityDefault);
                        keyworkerDto.setStatus(KeyworkerStatus.ACTIVE);
                        keyworkerDto.setAgencyId(keyworkerDto.getAgencyId());
                        keyworkerDto.setAutoAllocationAllowed(true);
                    }
                );
        }
    }

    private void decorateWithNomisKeyworkerData(final KeyworkerDto keyworkerDto) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            keyworkerDto.setStatus(KeyworkerStatus.ACTIVE);
            keyworkerDto.setAutoAllocationAllowed(false);
            keyworkerDto.setNumberAllocated(0);
        }
    }

    private void decorateWithAllocationsCount(final KeyworkerDto keyworkerDto) {
        if (keyworkerDto != null && keyworkerDto.getAgencyId() != null) {
            final var allocationsCount = repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(keyworkerDto.getStaffId(), keyworkerDto.getAgencyId(), true, AllocationType.PROVISIONAL);
            keyworkerDto.setNumberAllocated(allocationsCount);
        }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public void addOrUpdate(final Long staffId, final String prisonId, final KeyworkerUpdateDto keyworkerUpdateDto) {

        prisonSupportedService.verifyPrisonMigrated(prisonId);
        Validate.notNull(staffId, "Missing staff id");

        keyworkerRepository.findById(staffId).ifPresentOrElse(keyworker -> {
                keyworker.setCapacity(keyworkerUpdateDto.getCapacity());
                keyworker.setStatus(keyworkerUpdateDto.getStatus());
                keyworker.setActiveDate(keyworkerUpdateDto.getActiveDate());
                if (keyworkerUpdateDto.getStatus() == KeyworkerStatus.ACTIVE) {
                    keyworker.setAutoAllocationFlag(true);
                }
            },
            () -> keyworkerRepository.save(LegacyKeyworker.builder()
                .staffId(staffId)
                .capacity(keyworkerUpdateDto.getCapacity())
                .status(keyworkerUpdateDto.getStatus())
                .autoAllocationFlag(true)
                .activeDate(keyworkerUpdateDto.getActiveDate())
                .build()));

        final var behaviour = keyworkerUpdateDto.getBehaviour();
        if (behaviour != null) applyStatusChangeBehaviour(staffId, prisonId, behaviour);
    }

    private void applyStatusChangeBehaviour(final Long staffId, final String prisonId, final KeyworkerStatusBehaviour behaviour) {

        if (behaviour.isRemoveAllocations()) {
            final var now = LocalDateTime.now();
            final var allocations = repository.findByStaffIdAndPrisonIdAndActive(staffId, prisonId, true);
            allocations.forEach(ok -> {
                ok.setDeallocationReason(DeallocationReason.KEYWORKER_STATUS_CHANGE);
                ok.setActive(false);
                ok.setExpiryDateTime(now);
            });
        }

        if (behaviour.isRemoveFromAutoAllocation()) {
            keyworkerRepository.findById(staffId).ifPresent(kw -> kw.setAutoAllocationFlag(false));
        }
    }

    @Transactional
    public void deallocate(final String offenderNo) {
        final var offenderKeyworkers = repository.findByActiveAndOffenderNo(true, offenderNo);

        log.info("Found {} matching active offender key worker records", offenderKeyworkers.size());

        if (offenderKeyworkers.isEmpty()) {
            throw new EntityNotFoundException(String.format("Offender No %s not allocated or does not exist", offenderNo));
        }

        // There shouldnt ever be more than 1, but just in case
        final var now = LocalDateTime.now();
        offenderKeyworkers.forEach(offenderKeyworker -> {
            offenderKeyworker.deallocate(now, DeallocationReason.MANUAL);
            log.info("De-allocated offender {} from KW {} at {}", offenderNo, offenderKeyworker.getStaffId(), offenderKeyworker.getPrisonId());
        });
    }
}
