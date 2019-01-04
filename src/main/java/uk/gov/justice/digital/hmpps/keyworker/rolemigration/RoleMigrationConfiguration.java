package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Data
@ConfigurationProperties(prefix="role-migration")
public class RoleMigrationConfiguration {
    private List<String> rolesToMatch = new ArrayList<>();
    private List<String> rolesToAssign = new ArrayList<>();
    private List<String> rolesToMigrate = new ArrayList<>();
}
