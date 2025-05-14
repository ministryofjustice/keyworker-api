package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData;

import java.time.LocalDate;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Entity
@Table(name = "KEYWORKER_CONFIGURATION")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"staffId"})
@Audited(withModifiedFlag = true)
public class LegacyKeyworker {

    @Audited(withModifiedFlag = false)
    @Id @Column(name = "STAFF_ID", nullable = false)
    private Long staffId;

    @NotNull
    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    @Audited(targetAuditMode = NOT_AUDITED, withModifiedFlag = true)
    @NotNull
    @ManyToOne
    @JoinColumn(name = "STATUS_ID", nullable = false)
    private ReferenceData status;

    @NotNull
    @Column(name = "AUTO_ALLOCATION", nullable = false)
    @Builder.Default
    private Boolean autoAllocation = Boolean.TRUE;

    @Column(name = "REACTIVATE_ON")
    LocalDate reactivateOn;
}
