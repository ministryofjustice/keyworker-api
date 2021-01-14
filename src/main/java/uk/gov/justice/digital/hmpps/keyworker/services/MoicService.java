package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class MoicService {
    @Value("${womens.estate.complex.offenders}")
    final List<String> complexOffenders;

    @Value("${womens.estate}")
    final List<String> womensEstatePrisons;

    public List<String> getComplexOffenders(final String prisonId, final List<String> offenderNos) {
        if(!womensEstatePrisons.contains(prisonId)) return Collections.emptyList();

        return offenderNos.stream()
            .filter(complexOffenders::contains)
            .collect(Collectors.toList());
    }
}
