package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "BATCH_HISTORY")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"batchId"})
public class BatchHistory {

    @Id()
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BATCH_ID", nullable = false)
    private Long batchId;

    @NotNull
    @Column(name = "NAME", nullable = false)
    private String name;

    @NotNull
    @Column(name = "LAST_RUN", nullable = false)
    private LocalDateTime lastRun;
}
