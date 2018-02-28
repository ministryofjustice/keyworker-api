package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class AllocationsFilterDto {
    private String agencyId;
    private Optional<AllocationType> allocationType;
    private Optional<LocalDate> fromDate;
    private Optional<LocalDate> toDate;
}
