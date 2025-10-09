package uk.gov.justice.digital.hmpps.keyworker.services

import java.math.BigDecimal
import java.math.RoundingMode.HALF_EVEN

object Statistic {
  internal fun percentage(
    numerator: Int,
    denominator: Int?,
  ): Double? =
    if (numerator == 0 || denominator == null || denominator == 0) {
      null
    } else {
      BigDecimal((numerator.toDouble() / denominator.toDouble()) * 100).setScale(2, HALF_EVEN).toDouble()
    }
}
