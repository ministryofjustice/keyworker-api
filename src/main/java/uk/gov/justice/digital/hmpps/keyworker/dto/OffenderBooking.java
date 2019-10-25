package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OffenderBooking {
    private Long bookingId;
    private String bookingNo;
    private String offenderNo;
    private String agencyId;

}
