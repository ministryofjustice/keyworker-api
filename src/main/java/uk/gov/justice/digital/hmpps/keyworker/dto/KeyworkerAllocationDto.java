package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KeyworkerAllocationDto {
    private long bookingId;
    private long staffId;
    private AllocationType type;
    private String reason;
}
