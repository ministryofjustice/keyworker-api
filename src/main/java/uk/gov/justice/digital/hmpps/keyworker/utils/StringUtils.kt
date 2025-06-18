package uk.gov.justice.digital.hmpps.keyworker.utils

fun String.asKeyword() = lowercase().replace("[_|-]".toRegex(), "")