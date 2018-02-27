package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KeyworkerDto {
    private String staffId;
    private String firstName;
    private String lastName;
    private int numberAllocated;
}
