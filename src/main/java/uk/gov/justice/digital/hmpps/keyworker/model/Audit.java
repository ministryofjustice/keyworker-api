package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.time.LocalDateTime;

@Embeddable

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)

public class Audit {

    @Column(name = "AUDIT_TIMESTAMP")
    private LocalDateTime timestamp;

    @Length(max = 32)
    @Column(name = "AUDIT_USER_ID")
    private String userId;

    @Length(max = 65)
    @Column(name = "AUDIT_MODULE_NAME")
    private String moduleName;

    @Length(max = 64)
    @Column(name = "AUDIT_CLIENT_USER_ID")
    private String clientUserId;

    @Length(max = 39)
    @Column(name = "AUDIT_CLIENT_IP_ADDRESS")
    private String clientIpAddress;

    @Length(max = 64)
    @Column(name = "AUDIT_CLIENT_WORKSTATION_NAME")
    private String clientWorkstationName;

    @Length(max = 256)
    @Column(name = "AUDIT_ADDITIONAL_INFO")
    private String additionalInfo;
}
