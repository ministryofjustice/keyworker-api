package uk.gov.justice.digital.hmpps.keyworker.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.openapitools.jackson.nullable.JsonNullableModule

object JsonHelper {
  @JvmStatic
  val objectMapper: ObjectMapper =
    jacksonObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.INDENT_OUTPUT, true)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .registerModule(JavaTimeModule())
      .registerModule(JsonNullableModule())
      .registerKotlinModule()
      .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
}
