package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

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
    private Boolean autoAllocationFlag = Boolean.TRUE;
}
