package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode

public class AllocationsRequestDto {
    private String agencyId;
    private Optional<AllocationType> allocationType;
    private Optional<LocalDate> fromDate;
    private Optional<LocalDate> toDate;
}
