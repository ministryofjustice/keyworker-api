package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.containing

fun MappingBuilder.withBearerToken(): MappingBuilder = withHeader("Authorization", containing("Bearer "))
