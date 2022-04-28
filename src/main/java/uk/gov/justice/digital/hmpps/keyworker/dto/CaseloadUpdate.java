package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;

@ApiModel(description = "Caseload Update")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseloadUpdate {

    @Schema(required = true, description = "Caseload", example = "MDI")
    @NotNull
    private String caseload;

    @Schema(required = true, description = "Number of users enabled to access API", example = "5")
    @NotNull
    private int numUsersEnabled;

  public CaseloadUpdate(@NotNull String caseload, @NotNull int numUsersEnabled) {
    this.caseload = caseload;
    this.numUsersEnabled = numUsersEnabled;
  }

  public CaseloadUpdate() {
  }

  public static CaseloadUpdateBuilder builder() {
    return new CaseloadUpdateBuilder();
  }

  public @NotNull String getCaseload() {
    return this.caseload;
  }

  public @NotNull int getNumUsersEnabled() {
    return this.numUsersEnabled;
  }

  public void setCaseload(@NotNull String caseload) {
    this.caseload = caseload;
  }

  public void setNumUsersEnabled(@NotNull int numUsersEnabled) {
    this.numUsersEnabled = numUsersEnabled;
  }

  public String toString() {
    return "CaseloadUpdate(caseload=" + this.getCaseload() + ", numUsersEnabled=" + this.getNumUsersEnabled() + ")";
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof CaseloadUpdate)) return false;
    final CaseloadUpdate other = (CaseloadUpdate) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$caseload = this.getCaseload();
    final Object other$caseload = other.getCaseload();
    if (this$caseload == null ? other$caseload != null : !this$caseload.equals(other$caseload)) return false;
    if (this.getNumUsersEnabled() != other.getNumUsersEnabled()) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CaseloadUpdate;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $caseload = this.getCaseload();
    result = result * PRIME + ($caseload == null ? 43 : $caseload.hashCode());
    result = result * PRIME + this.getNumUsersEnabled();
    return result;
  }

  public static class CaseloadUpdateBuilder {
    private @NotNull String caseload;
    private @NotNull int numUsersEnabled;

    CaseloadUpdateBuilder() {
    }

    public CaseloadUpdate.CaseloadUpdateBuilder caseload(@NotNull String caseload) {
      this.caseload = caseload;
      return this;
    }

    public CaseloadUpdate.CaseloadUpdateBuilder numUsersEnabled(@NotNull int numUsersEnabled) {
      this.numUsersEnabled = numUsersEnabled;
      return this;
    }

    public CaseloadUpdate build() {
      return new CaseloadUpdate(caseload, numUsersEnabled);
    }

    public String toString() {
      return "CaseloadUpdate.CaseloadUpdateBuilder(caseload=" + this.caseload + ", numUsersEnabled=" + this.numUsersEnabled + ")";
    }
  }
}
