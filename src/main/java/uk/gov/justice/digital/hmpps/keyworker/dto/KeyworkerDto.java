package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KeyworkerDto {
    private long staffId;
    private String firstName;
    private String lastName;
    private String email;
    private Long thumbnailId;
    private int numberAllocated;
}
