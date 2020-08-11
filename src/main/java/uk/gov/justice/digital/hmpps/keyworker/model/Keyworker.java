package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "KEY_WORKER")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"staffId"})
public class Keyworker {

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

    @Type(type = "yes_no")
    @NotNull
    @Column(name = "AUTO_ALLOCATION_FLAG", nullable = false)
    @Builder.Default
    private Boolean autoAllocationFlag = Boolean.TRUE;

    @Column(name = "ACTIVE_DATE")
    LocalDate activeDate;
}
