package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PagingAndSortingDto {
    private Integer pageOffset;
    private Integer pageLimit;
    private String sortFields;
    private SortOrder sortOrder;
}
