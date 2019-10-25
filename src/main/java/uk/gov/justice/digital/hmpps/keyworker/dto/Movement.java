package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Movement {
    private String offenderNo;
    private LocalDateTime createDateTime;
    private String fromAgency;
    private String toAgency;
    private String movementType;
    private String movementTypeDescription;
    private String directionCode;
    private LocalTime movementTime;
    private String movementReason;

}