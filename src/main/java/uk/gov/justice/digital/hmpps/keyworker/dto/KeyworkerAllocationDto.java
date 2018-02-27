package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KeyworkerAllocationDto {
    private int bookingId;
    private String offenderNo;
    private String firstName;
    private String middleNames;
    private String lastName;
    private int staffId;
    private String agencyId;
    private String assigned;
    private String allocationType;
    private String internalLocationDesc;
}
