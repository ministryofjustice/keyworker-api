package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.*;
import org.hibernate.validator.constraints.Length;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "PRISON_SUPPORTED")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"prisonId"})
public class PrisonSupported {

    @Id()
    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_ID", nullable = false)
    private String prisonId;

    @NotNull
    @Column(name = "MIGRATED", nullable = false)
    private boolean migrated;

    @NotNull
    @Column(name = "AUTO_ALLOCATE", nullable = false)
    private boolean autoAllocate;

    @Column(name = "MIGRATED_DATE_TIME", nullable = true)
    private LocalDateTime migratedDateTime;

}
