package uk.gov.justice.digital.hmpps.keyworker.integration

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import java.time.LocalDate

data class Prisoners(
  val content: List<Prisoner>,
) {
  private val map = content.associateBy { it.prisonerNumber }

  fun personIdentifiers(): Set<String> = map.keys

  @JsonIgnore
  val size = map.keys.size

  fun isEmpty() = map.keys.isEmpty()

  operator fun get(prisonerNumber: String): Prisoner? = map[prisonerNumber]
}

data class PrisonAlert(
  val alertType: String,
  val alertCode: String,
  val active: Boolean,
  val expired: Boolean,
)

data class Prisoner(
  val prisonerNumber: String,
  val firstName: String,
  val lastName: String,
  val receptionDate: LocalDate,
  val releaseDate: LocalDate?,
  val prisonId: String,
  val lastPrisonId: String,
  val prisonName: String,
  val cellLocation: String?,
  val csra: String?,
  val complexityOfNeedLevel: ComplexityOfNeedLevel?,
  val lastAdmissionDate: LocalDate?,
  val alerts: List<PrisonAlert>,
) {
  companion object {
    fun fields() =
      setOf(
        Prisoner::prisonerNumber.name,
        Prisoner::firstName.name,
        Prisoner::lastName.name,
        Prisoner::receptionDate.name,
        Prisoner::releaseDate.name,
        Prisoner::prisonId.name,
        Prisoner::prisonName.name,
        Prisoner::lastPrisonId.name,
        Prisoner::cellLocation.name,
        Prisoner::csra.name,
        Prisoner::complexityOfNeedLevel.name,
        Prisoner::lastAdmissionDate.name,
        Prisoner::alerts.name,
      )
  }
}

fun List<PrisonAlert>.getRelevantAlertCodes() =
  filter {
    it.active && (it.alertCode == "XRF" || it.alertCode == "RNO121")
  }.map { it.alertCode }

fun List<PrisonAlert>.getRemainingAlertCount() = filter { it.active && it.alertCode != "XRF" && it.alertCode != "RNO121" }.size
