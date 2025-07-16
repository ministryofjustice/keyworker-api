package uk.gov.justice.digital.hmpps.keyworker

import io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers
import io.gatling.javaapi.core.CoreDsl.csv
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.feed
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.lang.System.getenv
import java.time.Duration.ofMinutes

class GetCurrentAllocation : Simulation() {
  private val personIdentifiers = csv("person-identifiers-${getenv("ENVIRONMENT_NAME")}.csv").random()

  private fun getCurrentAllocation() =
    exec(
      http("Get current allocation of a person")
        .get("/prisoners/#{personIdentifier}/allocations/current")
        .headers(authorisationHeader)
        .check(status().shouldBe(200)),
    )

  private val prisonerProfile =
    scenario("Viewing prisoner profile")
      .exec(getToken)
      .repeat(10)
      .on(feed(personIdentifiers), getCurrentAllocation())

  init {
    setUp(
      prisonerProfile.injectClosed(constantConcurrentUsers(20).during(ofMinutes(10))),
    ).protocols(httpProtocol)
  }
}
