package uk.gov.justice.digital.hmpps.keyworker.integration.events.domain

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

data class HmppsDomainEvent<T : AdditionalInformation>(
  val eventType: String,
  val additionalInformation: T,
  val personReference: PersonReference = PersonReference(),
  val occurredAt: ZonedDateTime = ZonedDateTime.now(),
  val detailUrl: String? = null,
  val description: String? = null,
  val version: Int = 1,
)

data class PersonReference(
  val identifiers: Set<Identifier> = setOf(),
) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value

  fun nomsNumber(): String? = get(NOMS_NUMBER_TYPE)

  companion object {
    private const val NOMS_NUMBER_TYPE = "NOMS"

    fun withIdentifier(prisonNumber: String) = PersonReference(setOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(
    val type: String,
    val value: String,
  )
}

sealed interface AdditionalInformation

data class PrisonStatisticsInfo(
  val prisonCode: String,
  val date: LocalDate,
  val policy: AllocationPolicy,
) : AdditionalInformation

data class MergeInformation(
  val nomsNumber: String,
  val removedNomsNumber: String,
) : AdditionalInformation

data class CaseNoteInformation(
  val id: UUID,
  val type: String,
  val subType: String,
  val previousNomsNumber: String?,
) : AdditionalInformation

data object CaseNoteMigrationInformation : AdditionalInformation
