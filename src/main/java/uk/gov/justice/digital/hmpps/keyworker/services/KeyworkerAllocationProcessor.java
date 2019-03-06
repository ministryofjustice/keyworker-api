package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class to perform various processing tasks relating to {@link uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker} data.
 */
@Service
@Slf4j
public class KeyworkerAllocationProcessor {
    private final OffenderKeyworkerRepository repository;

    public KeyworkerAllocationProcessor(final OffenderKeyworkerRepository repository) {
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
        final var allocs = repository.findByActiveAndOffenderNoIn(true, offenderNos);

        // Extract offender numbers having active allocation
        final var activeOffenderNos = allocs.stream()
                .collect(Collectors.toMap(
                        OffenderKeyworker::getOffenderNo,
                        Function.identity(),
                        (offender1, offender2) -> {
                            log.error("Prisoner {} has multiple active allocations", offender1.getOffenderNo());
                            return offender1;
                        }
                ));

        // Return input list, filtered to remove offenders that have an active allocation
        return dtos.stream().filter(dto ->
                !activeOffenderNos.containsKey(dto.getOffenderNo())
                        || activeOffenderNos.get(dto.getOffenderNo()).getAllocationType() == AllocationType.PROVISIONAL)
                .collect(Collectors.toList());
    }

    public List<KeyworkerAllocationDetailsDto> decorateAllocated(final List<OffenderKeyworker> allocations, final List<OffenderLocationDto> allOffenders) {
        final var allOffendersMap = allOffenders.stream().collect(Collectors.toMap(OffenderLocationDto::getOffenderNo, Function.identity()));
        return allocations.stream().map(t -> {
            final var keyworkerAllocationDetailsDto = ConversionHelper.convertOffenderKeyworkerModel2KeyworkerAllocationDetailsDto(t);
            final var offenderLocationDto = allOffendersMap.get(t.getOffenderNo());
                    if (offenderLocationDto != null) {
                        keyworkerAllocationDetailsDto.setFirstName(offenderLocationDto.getFirstName());
                        keyworkerAllocationDetailsDto.setLastName(offenderLocationDto.getLastName());
                        keyworkerAllocationDetailsDto.setInternalLocationDesc(offenderLocationDto.getAssignedLivingUnitDesc());
                    }
                    return keyworkerAllocationDetailsDto;
                }
        ).collect(Collectors.toList());
    }
}
