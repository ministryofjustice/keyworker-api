package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OffenderSummaryDto {
    private int bookingId;
    private String offenderNo;
    private String title;
    private String suffix;
    private String firstName;
    private String middleNames;
    private String lastName;
    private String currentlyInPrison;
    private String agencyLocationId;
    private String agencyLocationDesc;
    private String internalLocationId;
    private String internalLocationDesc;
}
