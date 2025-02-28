package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.type.YesNoConverter;

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
    @Column(name = "STATUS", nullable = false)
    @Convert(converter = KeyworkerStatusConvertor.class)
    private KeyworkerStatus status;

    @NotNull
    @Column(name = "AUTO_ALLOCATION_FLAG", nullable = false)
    @Builder.Default
    @Convert(converter = YesNoConverter.class)
    private Boolean autoAllocationFlag = Boolean.TRUE;

    @Column(name = "ACTIVE_DATE")
    LocalDate activeDate;
}
