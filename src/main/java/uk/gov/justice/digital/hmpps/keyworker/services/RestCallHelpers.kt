package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

fun queryParamsOf(vararg params: String): MultiValueMap<String, String> {
  if (params.size % 2 > 0) throw IllegalArgumentException("Query parameters must come in pairs: $params")

  return params.toList()
    .chunked(2)
    .associate { it[0] to mutableListOf(it[1]) }
    .toMap(LinkedMultiValueMap())
}

fun uriVariablesOf(vararg params: String): Map<String, String> {
  if (params.size % 2 > 0) throw IllegalArgumentException("URI variables must come in pairs: $params")

  return params.toList()
    .chunked(2)
    .associate { it[0] to it[1] }
    .toMap()
}
