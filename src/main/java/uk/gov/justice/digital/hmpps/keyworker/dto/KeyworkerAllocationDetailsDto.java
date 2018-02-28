package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KeyworkerAllocationDetailsDto {
    private long bookingId;
    private String offenderNo;
    private String firstName;
    private String middleNames;
    private String lastName;
    private long staffId;
    private String agencyId;
    private String assigned;
    private AllocationType allocationType;
    private String internalLocationDesc;
}
