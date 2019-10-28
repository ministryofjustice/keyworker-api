package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class BookingIdentifier {

    private String type;
    private String value;
}
