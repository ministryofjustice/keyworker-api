package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@ApiModel(description = "Key worker details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode()
@ToString(exclude = {"firstName", "lastName", "email"})
public class BasicKeyworkerDto {

    @Schema(required = true, description = "Unique staff identifier for Key worker.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Key worker's first name.")
    @NotBlank
    private String firstName;

    @Schema(required = true, description = "Key worker's last name.")
    @NotBlank
    private String lastName;

    @Schema(description = "Key worker's email address.")
    private String email;
}
