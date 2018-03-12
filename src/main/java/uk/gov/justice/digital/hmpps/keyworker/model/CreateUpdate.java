package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateUpdate {
    @NotNull
    @Column(name = "CREATE_DATETIME")
    LocalDateTime creationDateTime;

    @NotNull
    @Length(max = 32)
    @Column(name = "CREATE_USER_ID")
    String createUserId;

    @Column(name = "MODIFY_DATETIME")
    LocalDateTime modifyDateTime;

    @Length(max = 32)
    @Column(name = "MODIFY_USER_ID")
    String modifyUserId;
}
