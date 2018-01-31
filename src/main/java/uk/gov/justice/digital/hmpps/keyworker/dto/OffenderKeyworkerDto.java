package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OffenderKeyworkerDto {

    private String offenderKeyworkerId;

    private Long offenderBookingId;

    private Long officerId;

    private Date assignedDateTime;

    private String lastName;
}
