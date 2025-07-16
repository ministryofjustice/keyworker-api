package uk.gov.justice.digital.hmpps.keyworker

import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.lang.System.getenv

val getToken =
  exec(
    exec { session -> getenv("AUTH_TOKEN")?.let { session.set("authToken", it) } ?: session }
      .doIf { it.getString("authToken").isNullOrBlank() }
      .then(
        http("Get Auth Token")
          .post(getenv("AUTH_URL"))
          .queryParam("grant_type", "client_credentials")
          .basicAuth(getenv("CLIENT_ID"), getenv("CLIENT_SECRET"))
          .check(status().shouldBe(200), jsonPath("$.access_token").exists().saveAs("authToken")),
      ),
  )

val httpProtocol =
  http
    .baseUrl(getenv("BASE_URL"))
    .acceptHeader("application/json")
    .acceptEncodingHeader("gzip, deflate")
    .contentTypeHeader("application/json")

val authorisationHeader = mapOf("authorization" to "Bearer #{authToken}")
