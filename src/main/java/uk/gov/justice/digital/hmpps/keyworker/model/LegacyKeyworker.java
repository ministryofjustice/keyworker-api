package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.type.YesNoConverter;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData;

@Entity
@Table(name = "KEY_WORKER")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"staffId"})
public class LegacyKeyworker {

    @Id()
    @Column(name = "STAFF_ID", nullable = false)
    private Long staffId;

    @NotNull
    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "STATUS_ID", nullable = false)
    private ReferenceData status;

    @NotNull
    @Column(name = "AUTO_ALLOCATION_FLAG", nullable = false)
    @Builder.Default
    @Convert(converter = YesNoConverter.class)
    private Boolean autoAllocationFlag = Boolean.TRUE;

    @Column(name = "ACTIVE_DATE")
    LocalDate activeDate;
}
