package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.Collection;
import java.util.Set;

/**
 * Spring managed component responsible for instantiating and initialising {@code KeyworkerPool} instances.
 */
@Component
@Slf4j
public class KeyworkerPoolFactory {
    private final KeyworkerService keyworkerService;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final NomisService nomisService;

    @Value("${svc.kw.allocation.capacity.tiers:6,9}")
    private Set<Integer> capacityTiers;

    @Value("${api.keyworker.deallocation.buffer.hours:48}")
    private int deallocationBufferHours;

    public KeyworkerPoolFactory(KeyworkerService keyworkerService,
                                OffenderKeyworkerRepository offenderKeyworkerRepository,
                                NomisService nomisService) {
        this.keyworkerService = keyworkerService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.nomisService = nomisService;
    }

    /**
     * Initialise new key worker pool with set of key workers and capacity tiers.
     *
     * @param keyworkers set of key workers in the pool.
     * @return initialised key worker pool.
     */
    public KeyworkerPool getKeyworkerPool(Collection<KeyworkerDto> keyworkers) {
        Validate.notEmpty(keyworkers);

        KeyworkerPool keyworkerPool = new KeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService,
                keyworkers, capacityTiers, deallocationBufferHours);

        log.debug("Initialised new Key worker pool with {} members and {} capacity tiers.",
                keyworkers.size(), capacityTiers.size());

        return keyworkerPool;
    }
}
