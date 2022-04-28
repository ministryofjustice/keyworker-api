package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagingAndSortingDto {
    public static final String HEADER_PAGE_OFFSET = "Page-Offset";
    public static final String HEADER_PAGE_LIMIT = "Page-Limit";
    public static final String HEADER_SORT_FIELDS = "Sort-Fields";
    public static final String HEADER_SORT_ORDER = "Sort-Order";

    private Long pageOffset;
    private Long pageLimit;
    private String sortFields;
    private SortOrder sortOrder;

    public PagingAndSortingDto(Long pageOffset, Long pageLimit, String sortFields, SortOrder sortOrder) {
        this.pageOffset = pageOffset;
        this.pageLimit = pageLimit;
        this.sortFields = sortFields;
        this.sortOrder = sortOrder;
    }

    public PagingAndSortingDto() {
    }

    public static PagingAndSortingDtoBuilder builder() {
        return new PagingAndSortingDtoBuilder();
    }

    public Long getPageOffset() {
        return this.pageOffset;
    }

    public void setPageOffset(Long pageOffset) {
        this.pageOffset = pageOffset;
    }

    public Long getPageLimit() {
        return this.pageLimit;
    }

    public void setPageLimit(Long pageLimit) {
        this.pageLimit = pageLimit;
    }

    public String getSortFields() {
        return this.sortFields;
    }

    public void setSortFields(String sortFields) {
        this.sortFields = sortFields;
    }

    public SortOrder getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof PagingAndSortingDto)) return false;
        final PagingAndSortingDto other = (PagingAndSortingDto) o;
        if (!other.canEqual(this)) return false;
        final Object this$pageOffset = this.getPageOffset();
        final Object other$pageOffset = other.getPageOffset();
        if (this$pageOffset == null ? other$pageOffset != null : !this$pageOffset.equals(other$pageOffset))
            return false;
        final Object this$pageLimit = this.getPageLimit();
        final Object other$pageLimit = other.getPageLimit();
        if (this$pageLimit == null ? other$pageLimit != null : !this$pageLimit.equals(other$pageLimit)) return false;
        final Object this$sortFields = this.getSortFields();
        final Object other$sortFields = other.getSortFields();
        if (this$sortFields == null ? other$sortFields != null : !this$sortFields.equals(other$sortFields))
            return false;
        final Object this$sortOrder = this.getSortOrder();
        final Object other$sortOrder = other.getSortOrder();
        return this$sortOrder == null ? other$sortOrder == null : this$sortOrder.equals(other$sortOrder);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PagingAndSortingDto;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $pageOffset = this.getPageOffset();
        result = result * PRIME + ($pageOffset == null ? 43 : $pageOffset.hashCode());
        final Object $pageLimit = this.getPageLimit();
        result = result * PRIME + ($pageLimit == null ? 43 : $pageLimit.hashCode());
        final Object $sortFields = this.getSortFields();
        result = result * PRIME + ($sortFields == null ? 43 : $sortFields.hashCode());
        final Object $sortOrder = this.getSortOrder();
        result = result * PRIME + ($sortOrder == null ? 43 : $sortOrder.hashCode());
        return result;
    }

    public String toString() {
        return "PagingAndSortingDto(pageOffset=" + this.getPageOffset() + ", pageLimit=" + this.getPageLimit() + ", sortFields=" + this.getSortFields() + ", sortOrder=" + this.getSortOrder() + ")";
    }

    public static class PagingAndSortingDtoBuilder {
        private Long pageOffset;
        private Long pageLimit;
        private String sortFields;
        private SortOrder sortOrder;

        PagingAndSortingDtoBuilder() {
        }

        public PagingAndSortingDto.PagingAndSortingDtoBuilder pageOffset(Long pageOffset) {
            this.pageOffset = pageOffset;
            return this;
        }

        public PagingAndSortingDto.PagingAndSortingDtoBuilder pageLimit(Long pageLimit) {
            this.pageLimit = pageLimit;
            return this;
        }

        public PagingAndSortingDto.PagingAndSortingDtoBuilder sortFields(String sortFields) {
            this.sortFields = sortFields;
            return this;
        }

        public PagingAndSortingDto.PagingAndSortingDtoBuilder sortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public PagingAndSortingDto build() {
            return new PagingAndSortingDto(pageOffset, pageLimit, sortFields, sortOrder);
        }

        public String toString() {
            return "PagingAndSortingDto.PagingAndSortingDtoBuilder(pageOffset=" + this.pageOffset + ", pageLimit=" + this.pageLimit + ", sortFields=" + this.sortFields + ", sortOrder=" + this.sortOrder + ")";
        }
    }
}
