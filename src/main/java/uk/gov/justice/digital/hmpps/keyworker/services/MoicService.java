package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class MoicService {
    @Value("${womens.estate.complex.offenders}")
    final String complexOffenders;

    @Value("${womens.estate}")
    final String womensEstatePrisons;

    public List<String> getComplexOffenders(final String prisonId, final List<String> offenderNos) {
        if(!getEnabledPrisons().contains(prisonId)) return Collections.emptyList();

        final var complex = Arrays.stream(complexOffenders.split(",")).toArray();

        return offenderNos.stream()
            .filter(offenderNo -> Arrays.asList(complex).contains(offenderNo))
            .collect(Collectors.toList());
    }

    private List<String> getEnabledPrisons() {
        if(StringUtils.isBlank(womensEstatePrisons)) return Collections.emptyList();

        return Arrays.stream(womensEstatePrisons.split(",")).collect(Collectors.toList());
    }
}
