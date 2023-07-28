package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.MultiValueMap;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@ApiModel(description = "A specification of how to select a subset of users by caseload and role membership.  Selected users will be assigned 'rolesToAssign' in the NWEB caseload.  The 'rolesToRemove' will then be removed from each selected user at each of the specified caseloads.")
@Data
@NoArgsConstructor
public class RoleAssignmentsSpecification {

    @Schema(required = true, description = "The caseloads to search for users having roles matching 'rolesToMatch'.")
    @NotEmpty(message = "Expected at least one 'caseload'")
    private List<String> caseloads;
    @Schema(required = true, description = "users within the caseloads will be selected if they have at least one role matching the codes in rolesToMatch.")
    @NotEmpty(message = "Expected at least one 'rolesToMatch'")
    private List<String> rolesToMatch;
    @Schema(description = "Users with the named caseloads, having roles matching rolesToMatch will be assigned these roles in the 'NWEB' caseload.")
    @NotNull
    private List<String> rolesToAssign = List.of();
    @Schema(description = "For each caseload in caseloads; find the users having at least one role matching 'rolesToMatch'. For each matched user at the current caseload remove each of the roles in 'rolesToRemove' at that caseload.")
    @NotNull
    private List<String> rolesToRemove = List.of();

    @Builder
    private RoleAssignmentsSpecification(
        final List<String> caseloads,
        final List<String> rolesToMatch,
        final List<String> rolesToAssign,
        final List<String> rolesToRemove) {
        this.caseloads = caseloads;
        this.rolesToMatch = rolesToMatch;
        this.rolesToAssign = rolesToAssign == null ? List.of() : rolesToAssign;
        this.rolesToRemove = rolesToRemove == null ? List.of() : rolesToRemove;
    }

    public static RoleAssignmentsSpecification fromForm(final MultiValueMap<String, String> form) {
        return RoleAssignmentsSpecification.builder()
            .caseloads(fromFormField("caseloads", form))
            .rolesToMatch(fromFormField("rolesToMatch", form))
            .rolesToAssign(fromFormField("rolesToAssign", form))
            .rolesToRemove(fromFormField("rolesToRemove", form))
            .build();
    }

    private static List<String> fromFormField(final String key, final MultiValueMap<String, String> form) {
        return form.getOrDefault(key, Collections.emptyList())
            .stream()
            .filter(value -> !value.isBlank())
            .collect(Collectors.toList());
    }
}
