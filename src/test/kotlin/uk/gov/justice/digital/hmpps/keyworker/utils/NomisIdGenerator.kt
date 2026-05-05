package uk.gov.justice.digital.hmpps.keyworker.utils

import java.util.concurrent.atomic.AtomicLong

object NomisIdGenerator {
  private val id = AtomicLong((System.currentTimeMillis() % 100_000L) * 1_000L)
  private val letters = ('A'..'Z')
  private val numbers = (1111..9999)
  private val usedPrisonCodes = mutableSetOf<String>()

  fun newId(): Long = id.getAndIncrement()

  fun personIdentifier(): String = "${letters.random()}${numbers.random()}${letters.random()}${letters.random()}"

  fun prisonCode(attempts: Int = 10): String {
    if (attempts <= 0) throw IllegalStateException("Ran out of attempts to find a unique prison code")
    val prisonCode = (1..6).map { letters.random() }.joinToString("")
    return if (usedPrisonCodes.add(prisonCode)) prisonCode else prisonCode(attempts - 1)
  }

  fun username(): String = (0..12).joinToString("") { letters.random().toString() }
}
