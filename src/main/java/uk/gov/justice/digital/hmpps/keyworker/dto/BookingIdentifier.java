package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BookingIdentifier {

    private String type;
    private String value;
}
