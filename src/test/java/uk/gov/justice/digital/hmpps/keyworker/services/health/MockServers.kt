package uk.gov.justice.digital.hmpps.whereabouts.integration.wiremock

import com.github.tomakehurst.wiremock.junit.WireMockRule

class EliteMockServer : WireMockRule(8081)

class OAuthMockServer : WireMockRule(8090)

