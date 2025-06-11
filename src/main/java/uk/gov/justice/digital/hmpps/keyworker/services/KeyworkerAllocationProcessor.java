package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class to perform various processing tasks relating to {@link LegacyKeyworkerAllocation} data.
 */
@Service
@Slf4j
public class KeyworkerAllocationProcessor {
    private final LegacyKeyworkerAllocationRepository repository;

    public KeyworkerAllocationProcessor(final LegacyKeyworkerAllocationRepository repository) {
        this.repository = repository;
    }

    /**
     * Decorates each provided offender summary DTO with offender's allocation status (indicating whether or not they
     * are currently allocated to a Key worker).
     *
     * @param dtos offender summary DTOs to decorate.
     * @return decorated offender summary DTOs.
     */
    public List<OffenderLocationDto> filterByUnallocated(final List<OffenderLocationDto> dtos) {
        Validate.notNull(dtos);

        if (dtos.isEmpty()) {
            return dtos;
        }

        // Extract set of offender numbers from provided DTOs
        final var offenderNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Obtain list of active Keyworker allocations for these offenders, if any
        final var allocs = repository.findByActiveAndPersonIdentifierIn(true, offenderNos);

        // Extract offender numbers having active allocation
        final var activeOffenderNos = allocs.stream()
            .collect(Collectors.toMap(
                LegacyKeyworkerAllocation::getPersonIdentifier,
                Function.identity(),
                (offender1, offender2) -> {
                    log.error("Prisoner {} has multiple active allocations", offender1.getPersonIdentifier());
                    return offender1;
                }
            ));

        // Return input list, filtered to remove offenders that have an active allocation
        return dtos.stream().filter(dto ->
            !activeOffenderNos.containsKey(dto.getOffenderNo())
                || activeOffenderNos.get(dto.getOffenderNo()).getAllocationType() == AllocationType.PROVISIONAL)
            .collect(Collectors.toList());
    }

    public List<KeyworkerAllocationDetailsDto> decorateAllocated(final List<LegacyKeyworkerAllocation> allocations, final List<OffenderLocationDto> allOffenders) {
        final var duplicates = allOffenders.stream()
            .map(OffenderLocationDto::getOffenderNo)
            .collect(Collectors.groupingBy(string -> string, Collectors.counting()));

        duplicates.keySet().forEach(key -> {
            final var count = duplicates.get(key);
            if (count > 1) log.error("Duplicate offender at location, offenderNo: {} count: {}", key, count);
        });

        return allocations.stream()
            .map(allocation -> transformToAllocationDetails(allocation, allOffenders))
            .collect(Collectors.toList());
    }

    private KeyworkerAllocationDetailsDto transformToAllocationDetails(final LegacyKeyworkerAllocation allocation, final List<OffenderLocationDto> allOffenders) {
        final var keyworkerAllocationDetailsDto = ConversionHelper.INSTANCE.convertOffenderKeyworkerModel2KeyworkerAllocationDetailsDto(allocation);

        final var offenderLocationDto = allOffenders.stream()
            .filter(offender -> offender.getOffenderNo().equals(allocation.getPersonIdentifier()))
            .findFirst()
            .orElse(null);

        if (offenderLocationDto != null) {
            keyworkerAllocationDetailsDto.setFirstName(offenderLocationDto.getFirstName());
            keyworkerAllocationDetailsDto.setLastName(offenderLocationDto.getLastName());
            keyworkerAllocationDetailsDto.setInternalLocationDesc(offenderLocationDto.getAssignedLivingUnitDesc());
        }

        return keyworkerAllocationDetailsDto;
    }
}
