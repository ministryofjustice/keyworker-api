package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class OffenderKeyworkerDto {

    private Long offenderKeyworkerId;

    private Long offenderBookingId;

    private String staffUsername;

    private Date assignedDateTime;

    private String offenderLastName;

    private String offenderFirstName;

    private String nomisId;
}
