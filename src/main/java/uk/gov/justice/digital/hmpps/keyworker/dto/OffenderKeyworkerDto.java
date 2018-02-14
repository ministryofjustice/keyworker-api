package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OffenderKeyworkerDto {

    private Long offenderKeyworkerId;

    private Long offenderBookingId;

    private String staffUsername;

    private Date assignedDateTime;

    private String offenderLastName;

    private String offenderFirstName;

    private String nomisId;
}
