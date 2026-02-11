package uk.gov.justice.digital.hmpps.keyworker.utils

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper

object JsonHelper {
  @JvmStatic
  val jsonMapper: JsonMapper = jsonMapper()
}
