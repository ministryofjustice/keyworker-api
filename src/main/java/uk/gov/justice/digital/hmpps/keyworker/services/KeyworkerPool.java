package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.util.ObjectUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetailDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a collection of Key workers that are available for allocation and encapsulates the implementation of
 * allocation rules which govern which Key worker is next in line for allocation at any particular moment.
 *
 * NB: KeyworkerPool is not thread safe. The pool is designed for short-lived, single-threaded operation. As a result
 * an instance of KeyworkerPool will not support multiple auto-allocation processes. Each auto-allocation process
 * should instantiate and use its own KeyworkerPool.
 */
@Slf4j
public class KeyworkerPool {
    public static final String OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY = "All available Key workers are at full capacity.";

    private final SortedSet<KeyworkerDto> keyworkerPool;
    private final Map<Long,List<OffenderKeyworker>> keyworkerAllocations;
    private final SortedSet<Integer> capacityTiers;
    private final Set<Long> keyworkerStaffIds;
    private final Map<Long,Integer> keyworkerRecentlyDeallocatedNumber;

    private KeyworkerService keyworkerService;
    private OffenderKeyworkerRepository offenderKeyworkerRepository;
    private NomisService nomisService;

    /**
     * Constructor.
     *
     * @param keyworkers set of Key workers in the pool.
     * @param capacityTiers optional set of capacity tier levels.
     */
    KeyworkerPool(KeyworkerService keyworkerService, OffenderKeyworkerRepository offenderKeyworkerRepository, NomisService nomisService,

                  Collection<KeyworkerDto> keyworkers, Collection<Integer> capacityTiers, int deallocationBufferHours) {
        Validate.notEmpty(keyworkers, "Key worker pool must contain at least one Key worker.");

        this.keyworkerService = keyworkerService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.nomisService = nomisService;

        // Initialise capacity tiers
        if (ObjectUtils.isEmpty(capacityTiers)) {
            this.capacityTiers = new TreeSet<>(Collections.singleton(Integer.MAX_VALUE));
        } else {
            this.capacityTiers = new TreeSet<>(capacityTiers);
        }

        // Initialise key worker pool
        keyworkerStaffIds = new HashSet<>();
        keyworkerAllocations = new HashMap<>();
        keyworkerRecentlyDeallocatedNumber = new HashMap<>();
        final Duration lookBackDuration = Duration.ofHours(deallocationBufferHours);

        keyworkers.forEach(kw -> {
            keyworkerStaffIds.add(kw.getStaffId());
            keyworkerAllocations.put(kw.getStaffId(), null);
            keyworkerRecentlyDeallocatedNumber.put(kw.getStaffId(), getRecentDeallocations(kw.getStaffId(), kw.getAgencyId(), lookBackDuration));
        });

        keyworkerPool = new TreeSet<>(buildKeyworkerComparator());
        keyworkerPool.addAll(keyworkers);

        log.debug("Key worker pool initialised with {} members.", keyworkers.size());
    }

    // Constructs Key worker comparator which, effectively, implements Key worker allocation prioritisation algorithm.
    // Comparator function ensures that highest priority Key worker is at head of Key worker pool.
    private Comparator<KeyworkerDto> buildKeyworkerComparator() {

        // If a KW is full, kick it to last place
        Comparator<KeyworkerDto> isFullComparator = (kw1, kw2) -> {
            int enhancedCapacityKw1 = calculateEnhancedCapacity(kw1);
            int enhancedCapacityKw2 = calculateEnhancedCapacity(kw2);
            final Integer numberAllocated1 = totalAllocations(kw1);
            final Integer numberAllocated2 = totalAllocations(kw2);
            if (numberAllocated1 >= enhancedCapacityKw1) {
                if (numberAllocated2 >= enhancedCapacityKw2) {
                    return 0;
                }
                return 1;
            }
            if (numberAllocated2 >= enhancedCapacityKw2) {
                return -1;
            }
            return 0;
        };

        final Comparator<KeyworkerDto> numberAllocatedComparator = (kw1, kw2) -> {
            final Integer numberAllocated1 = totalAllocations(kw1);
            final Integer numberAllocated2 = totalAllocations(kw2);
            return numberAllocated1.compareTo(numberAllocated2);
        };

        final Comparator<Long> staffIdComparator = (id1, id2) -> {
            Comparator<OffenderKeyworker> keyWorkerAllocationComparator = Comparator.comparing(OffenderKeyworker::getAssignedDateTime);

            SortedSet<OffenderKeyworker> id1Allocations = new TreeSet<>(keyWorkerAllocationComparator);
            SortedSet<OffenderKeyworker> id2Allocations = new TreeSet<>(keyWorkerAllocationComparator);

            Optional.ofNullable(keyworkerAllocations.get(id1)).ifPresent(id1Allocations::addAll);
            Optional.ofNullable(keyworkerAllocations.get(id2)).ifPresent(id2Allocations::addAll);

            int result;

            // If neither Key worker has any auto-allocations, or both have auto-allocations and an identical
            // assigned datetime for most recent allocation, arbitrarily sort by staffId (to ensure uniqueness).
            if (id1Allocations.isEmpty()) {
                result = id2Allocations.isEmpty() ? (id1.compareTo(id2)) : -1;
            } else if (id2Allocations.isEmpty()) {
                result = 1;
            } else {
                result = id1Allocations.last().getAssignedDateTime().compareTo(id2Allocations.last().getAssignedDateTime());

                if (result == 0) {
                    result = id1.compareTo(id2);
                }
            }
            return result;
        };

        Comparator<KeyworkerDto> comparator = isFullComparator
                .thenComparing(numberAllocatedComparator)
                .thenComparing(KeyworkerDto::getStaffId, staffIdComparator);

        return (kw1, kw2) -> {
            if (kw1 == kw2) {
                return 0;
            }
            return comparator.compare(kw1, kw2);
        };
    }

    private int totalAllocations(KeyworkerDto kw1) {
        return kw1.getNumberAllocated() + keyworkerRecentlyDeallocatedNumber.get(kw1.getStaffId());
    }

    private int calculateEnhancedCapacity(KeyworkerDto kw1) {
        final Integer capacity = kw1.getCapacity();
        if (capacityTiers.size() == 1) {
            return capacity;
        }
        final Iterator<Integer> iterator = capacityTiers.iterator();
        Integer first = iterator.next();
        Integer second = iterator.next();

        return capacity * second / first;
    }

    /**
     * Identifies and returns Key worker to whom offender should be allocated, as determined by allocation rules. Note
     * that if the returned Key worker is used for allocation of an offender, the pool must be updated with refreshed
     * Key worker details (via {@link #incrementAndRefreshKeyworker(KeyworkerDto)}).
     *
     * @param offenderNo offender number of offender for whom key worker allocation is required.
     * @return the priority {@code Keyworker}.
     */
    public KeyworkerDto getKeyworker(String offenderNo) {
        log.debug("Prioritising Key worker for allocation of offender with offenderNo [{}]", offenderNo);

        // Retrieve any previous Key worker allocations for offender.
        List<OffenderKeyworker> previousAllocations = keyworkerService.getAllocationHistoryForPrisoner(offenderNo);

        // First, determine if offender was previously allocated to any Key workers in the pool
        Optional<KeyworkerDto> previousKeyworker = findPreviousAllocation(offenderNo, previousAllocations);

        KeyworkerDto priorityKeyworker;

        if (previousKeyworker.isPresent()) {
            priorityKeyworker = previousKeyworker.get();

            log.debug("Previous allocation detected between Key worker with staffId [{}] and offender with offenderNo [{}].",
                    priorityKeyworker.getStaffId(), offenderNo);
        } else {
            log.debug("No previous Key worker allocations detected for offender with offenderNo [{}].", offenderNo);

            prioritiseKeyworkers();

            // Check allocation level for Key worker at head of pool list - if at or beyond maximum capacity, then
            // error as no Key worker currently in pool can accept any further allocations.
            checkMaxCapacity();

            priorityKeyworker = keyworkerPool.first();
        }

        log.debug("Key worker with staffId [{}] selected for allocation of offender with offenderNo [{}].",
                priorityKeyworker.getStaffId(), offenderNo);

        return priorityKeyworker;
    }

    /**
     * Increments allocation count of provided Key worker and Refreshes pool (to recalculate its position in pool).
     * If provided Key worker does not already exist in the pool, an
     * {@code IllegalStateException} is thrown.
     *
     * @param keyworker Key worker to process.
     * @throws IllegalStateException if Key worker is not already present in the pool.
     */
    public void incrementAndRefreshKeyworker(KeyworkerDto keyworker) {
        Validate.notNull(keyworker, "Key worker to refresh must be specified.");
        final List<OffenderKeyworker> savedOldAllocationsList = keyworkerAllocations.get(keyworker.getStaffId());

        // Remove Key worker from pool (throwing exception if Key worker not in pool)
        if (!removeKeyworker(keyworker.getStaffId()).isPresent()) {
            log.error("Key worker with staffId [{}] not in pool.", keyworker.getStaffId());

            throw new IllegalStateException("Key worker to refresh is not in Key worker pool.");
        }
        keyworker.setNumberAllocated(keyworker.getNumberAllocated() + 1);

        // Add Key worker back to pool
        reinstateKeyworker(keyworker, savedOldAllocationsList);
    }

    private Optional<KeyworkerDto> findPreviousAllocation(String offenderNo, List<OffenderKeyworker> keyWorkerAllocations) {
        log.debug("Assessing previous allocations for offender with offenderNo [{}].", offenderNo);

        Optional<KeyworkerDto> previousKeyworker;

        // Check if any were with a Key worker in the pool and sort allocations by assigned date
        if (ObjectUtils.isEmpty(keyWorkerAllocations)) {
            previousKeyworker = Optional.empty();
        } else {
            Optional<OffenderKeyworker> latestAllocation = keyWorkerAllocations.stream()
                    .filter(kwa -> kwa.getOffenderNo().equals(offenderNo) && keyworkerStaffIds.contains(kwa.getStaffId()))
                    .max(Comparator.comparing(OffenderKeyworker::getAssignedDateTime));

            if (latestAllocation.isPresent()) {
                // Key worker staff id of latest allocation
                Long keyworkerStaffId = latestAllocation.get().getStaffId();

                previousKeyworker = keyworkerPool.stream().filter(kw -> keyworkerStaffId.equals(kw.getStaffId())).findFirst();
            } else {
                previousKeyworker = Optional.empty();
            }
        }

        return previousKeyworker;
    }

    // Applies sorting algorithm to Key worker list to determine allocation priority of Key workers. Must be called
    // prior to each request for priority Key worker.
    private void prioritiseKeyworkers() {
        checkMaxCapacity();

        // Identify Key worker(s) with least number of allocations - first Key worker in pool will have least allocations
        int fewestAllocs = keyworkerPool.first().getNumberAllocated();

        // If priority Key worker has no allocations, no further processing required, otherwise identify any other Key
        // workers in pool having same number of allocations (and their keyworkerAllocations map entry remains uninitialised)
        if (fewestAllocs > 0) {
            List<KeyworkerDto> fewestAllocKeyworkers = keyworkerPool.stream()
                    .filter(kw -> kw.getNumberAllocated() == fewestAllocs)
                    .filter(kw -> keyworkerAllocations.get(kw.getStaffId()) == null)
                    .collect(Collectors.toList());

            // If only one Key worker with fewest allocations, no further processing required, otherwise retrieve
            // allocations for all Key workers with fewest allocations and update sort.
            if (fewestAllocKeyworkers.size() > 1) {
                // For each Key worker with fewest allocations, remove from pool, update allocations, reinstate to pool.
                fewestAllocKeyworkers.forEach(kw -> {
                    removeKeyworker(kw.getStaffId());
                    reinstateKeyworker(kw, keyworkerService.getAllocationsForKeyworker(kw.getStaffId()));
                });
            }
        }

        log.debug("Key worker pool prioritised - priority Key worker has {} allocations.",
                keyworkerPool.first().getNumberAllocated());
    }

    private void checkMaxCapacity() {
        final KeyworkerDto first = keyworkerPool.first();
        if (first.getNumberAllocated() >= calculateEnhancedCapacity(first)) {
            log.error(OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);

            throw AllocationException.withMessage(OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);
        }
    }

    private Optional<KeyworkerDto> removeKeyworker(long staffId) {
        log.debug("Removing Key worker with staffId [{}] from pool.", staffId);

        keyworkerStaffIds.remove(staffId);

        Optional<KeyworkerDto> keyworker = keyworkerPool.stream().filter(kw -> kw.getStaffId().equals(staffId)).findFirst();

        keyworker.ifPresent(keyworkerPool::remove);

        keyworkerAllocations.remove(staffId);
        return keyworker;
    }

    private void reinstateKeyworker(KeyworkerDto keyworker, List<OffenderKeyworker> allocations) {
        Long staffId = keyworker.getStaffId();

        log.debug("Reinstating Key worker with staffId [{}], and having [{}] allocations, to pool.",
                staffId, keyworker.getNumberAllocated());

        // Filter out manual allocations (and Provisionals obviously. Only interested in auto-allocations)
        List<OffenderKeyworker> autoAllocations = (allocations == null) ? null :
                allocations.stream().filter(kwa -> kwa.getAllocationType().isAuto()).collect(Collectors.toList());

        keyworkerAllocations.put(staffId, autoAllocations);
        keyworkerPool.add(keyworker);
        keyworkerStaffIds.add(staffId);

        log.debug("Key worker with staffId [{}] reinstated to pool. New pool size is [{}] and priority Key worker has staffId [{}] and [{}] allocations.",
                staffId, keyworkerPool.size(), keyworkerPool.first().getStaffId(), keyworkerPool.first().getNumberAllocated());
    }

    // Gets allocations for Key worker that have expired recently (between start of look back period and current time)
    private int getRecentDeallocations(Long staffId, String prisonId, Duration lookBackDuration) {
        LocalDateTime cutOff = LocalDateTime.now().minus(lookBackDuration);
        List<OffenderKeyworker> keyWorkerAllocations = offenderKeyworkerRepository.findByStaffIdAndPrisonId(staffId, prisonId);
        if (keyWorkerAllocations == null) {
            return 0;
        }
        // Extract set of offender id for active allocations
        Set<String> activeOffenderNos = keyWorkerAllocations.stream()
                .filter(kwa -> kwa.getExpiryDateTime() == null)
                .map(OffenderKeyworker::getOffenderNo)
                .collect(Collectors.toSet());

        // Allocation record is of interest if:
        //  - if it is for booking that is not in set of active allocation booking ids, and
        //  - it has an expiry datetime set, and
        //  - expiry datetime is after start of 'lookBackDuration' period.
        List<OffenderKeyworker> deallocations = keyWorkerAllocations.stream()
                .filter(kwa -> !activeOffenderNos.contains(kwa.getOffenderNo()))
                .filter(kwa -> Objects.nonNull(kwa.getExpiryDateTime()))
                .filter(kwa -> kwa.getExpiryDateTime().isAfter(cutOff))
                // TODO - filter multiple recent expired allocations for the same offender?
                .collect(Collectors.toList());

        // Removes deallocations where:
        //  - offender has active booking (in different agency)
        //  - offender has active booking in same agency and is allocated to a different Key worker
        Predicate<OffenderKeyworker> kwaDeallocPredicate = (kwa -> {
            boolean keepDeallocation = true;

            List<PrisonerDetailDto> results = nomisService.getOffender(kwa.getOffenderNo());

            // Keep deallocation if:
            //  - no offender summary found for booking id (should not happen)
            //  - latest offender booking indicates they are not currently in prison, or
            //  - latest offender booking indicates they are in same agency as allocation and
            //    they have an active allocation (to same or a different Key worker)
            for (PrisonerDetailDto summary : results) {
                if (StringUtils.equalsIgnoreCase(summary.getCurrentlyInPrison(), "Y")) {
                    if (StringUtils.equals(kwa.getPrisonId(), summary.getLatestLocationId())) {
                        final List<OffenderKeyworker> byActiveAndOffenderNo = offenderKeyworkerRepository.findByActiveAndOffenderNo(true, summary.getOffenderNo());
                        keepDeallocation = ! byActiveAndOffenderNo.stream().anyMatch(t -> t.getPrisonId().equals(kwa.getPrisonId()));
                    } else {
                        // In a different prison
                        keepDeallocation = false;
                    }
                }
            }
            return keepDeallocation;
        });
        return (int) deallocations.stream().filter(kwaDeallocPredicate).count();
    }
}
