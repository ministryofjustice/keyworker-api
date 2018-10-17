package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;

import java.time.LocalDate;

@Service
@Transactional
@Validated
@Slf4j
public class KeyworkerStatsService {
    public KeyworkerStatsDto getStats(String prisonId, LocalDate startDate, LocalDate endDate) {
        return KeyworkerStatsDto.builder().build();
    }
}
