package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;

import java.util.Collection;

/**
 * Spring managed component responsible for instantiating and initialising {@code KeyworkerPool} instances.
 */
@Component
@Slf4j
public class KeyworkerPoolFactory {
    private final KeyworkerService keyworkerService;
    private final PrisonSupportedService prisonSupportedService;

    public KeyworkerPoolFactory(KeyworkerService keyworkerService,
                                PrisonSupportedService prisonSupportedService) {

        this.keyworkerService = keyworkerService;
        this.prisonSupportedService = prisonSupportedService;
    }

    /**
     * Initialise new key worker pool with set of key workers.
     */
    public KeyworkerPool getKeyworkerPool(String prisonId, Collection<KeyworkerDto> keyworkers) {
        Validate.notEmpty(keyworkers);

        KeyworkerPool keyworkerPool = new KeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, prisonId);

        log.debug("Initialised new Key worker pool with {} members for prison {}.",
                keyworkers.size(), prisonId);

        return keyworkerPool;
    }
}
