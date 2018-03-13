package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderSummaryDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to perform various processing tasks relating to {@link uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker} data.
 */
@Service
public class KeyworkerAllocationProcessor {
    private final OffenderKeyworkerRepository repository;

    public KeyworkerAllocationProcessor(OffenderKeyworkerRepository repository) {
        this.repository = repository;
    }

    /**
     * Decorates each provided offender summary DTO with offender's allocation status (indicating whether or not they
     * are currently allocated to a Key worker).
     *
     * @param dtos offender summary DTOs to decorate.
     * @return decorated offender summary DTOs.
     */
    public List<OffenderSummaryDto> filterByUnallocated(List<OffenderSummaryDto> dtos) {
        Validate.notNull(dtos);

        if (dtos.isEmpty()) {
            return dtos;
        }

        // Extract set of offender numbers from provided DTOs
        Set<String> offNos = dtos.stream().map(OffenderSummaryDto::getOffenderNo).collect(Collectors.toSet());

        // Obtain list of active Keyworker allocations for these offenders, if any
        List<OffenderKeyworker> allocs = repository.findByActiveAndOffenderNoIn(true, offNos);

        // Extract offender numbers having active allocation
        Set<String> activeOffNos = allocs.stream().map(OffenderKeyworker::getOffenderNo).collect(Collectors.toSet());

        // Return input list, filtered to remove offenders that have an active allocation
        return dtos.stream().filter(dto -> !activeOffNos.contains(dto.getOffenderNo())).collect(Collectors.toList());
    }
}
