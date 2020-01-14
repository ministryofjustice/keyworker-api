package uk.gov.justice.digital.hmpps.keyworker.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification.builder;

class RoleAssignmentSpecificationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validObject() {
        assertThat(validator.validate(builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("X"))
                .build())
        ).isEmpty();
    }

    @Test
    void missingCaseload() {
        assertThat(validator.validate(builder()
                .caseloads(List.of())
                .rolesToMatch(List.of("X"))
                .build())
        ).hasSize(1)
        .extracting(ConstraintViolation::getMessage)
            .containsExactlyInAnyOrder("Expected at least one 'caseload'");
    }

    @Test
    void missingRolesToMatch() {
        assertThat(validator.validate(builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of())
                .build())
        ).hasSize(1)
         .extracting(ConstraintViolation::getMessage)
            .containsExactlyInAnyOrder("Expected at least one 'rolesToMatch'");
    }

    @Test
    void allMissing() {
        assertThat(validator.validate(builder()
                .caseloads(List.of())
                .rolesToMatch(List.of())
                .build())
        ).hasSize(2);
    }
}
