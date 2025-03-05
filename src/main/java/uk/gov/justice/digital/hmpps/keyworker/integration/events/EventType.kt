package uk.gov.justice.digital.hmpps.keyworker.integration.events

sealed interface EventType {
  val name: String

  data object CalculatePrisonStats : EventType {
    override val name = "keyworker-api.prison-statistics.calculate"
  }

  data object ComplexityOfNeedChanged : EventType {
    override val name = "complexity-of-need.level.changed"
  }

  data object PrisonMerged : EventType {
    override val name = "prison-offender-events.prisoner.merged"
  }

  data object CaseNoteCreated : EventType {
    override val name = "person.case-note.created"
  }

  data object CaseNoteUpdated : EventType {
    override val name = "person.case-note.updated"
  }

  data object CaseNoteMoved : EventType {
    override val name = "person.case-note.moved"
  }

  data object CaseNoteDeleted : EventType {
    override val name = "person.case-note.deleted"
  }

  data class Other(
    override val name: String,
  ) : EventType

  companion object {
    fun entries(): Set<EventType> =
      setOf(
        CalculatePrisonStats,
        ComplexityOfNeedChanged,
        PrisonMerged,
      )

    fun from(value: String): EventType = entries().firstOrNull { it.name.lowercase() == value.lowercase() } ?: Other(value)
  }
}
