package uk.gov.justice.digital.hmpps.keyworker.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ValidationException
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpMethod.PUT
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner

@Configuration
class KeyworkerContextConfiguration(
  private val keyworkerContextInterceptor: KeyworkerContextInterceptor,
) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    registry
      .addInterceptor(keyworkerContextInterceptor)
      .addPathPatterns("/**")
      .excludePathPatterns(
        "/staff/returning-from-leave",
        "/prison-statistics/calculate",
        "/queue-admin/retry-all-dlqs",
        "/health/**",
        "/info",
        "/ping",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/swagger-resources/**",
        "/favicon.ico"
      )
  }
}

@Configuration
class KeyworkerContextInterceptor : HandlerInterceptor {
  override fun preHandle(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
  ): Boolean {
    AllocationContext(
      username = getUsername(),
      activeCaseloadId = request.caseloadId(),
      policy = request.policy(),
    ).set()

    return true
  }

  override fun postHandle(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
    modelAndView: ModelAndView?,
  ) {
    AllocationContext.clear()
    super.postHandle(request, response, handler, modelAndView)
  }

  private fun getUsername(): String =
    SecurityContextHolder
      .getContext()
      .authentication
      .name
      .trim()
      .takeUnless(String::isBlank)
      ?.also { if (it.length > 64) throw ValidationException("Username must be <= 64 characters") }
      ?: throw ValidationException("Could not find non empty username")

  private fun HttpServletRequest.caseloadId(): String? = getHeader(CaseloadIdHeader.NAME)

  private fun HttpServletRequest.policy(): AllocationPolicy? {
    val policy = AllocationPolicy.of(getHeader(PolicyHeader.NAME))
    if (policy == null && policyNotRequired.none { requestURI.matches(it.urlRegex) && HttpMethod.valueOf(method) in it.methods }) {
      throw NoPolicyProvidedException()
    }
    return policy
  }

  companion object {
    private val policyNotRequired =
      listOf(
        "/prisoners/${Prisoner.PATTERN}/allocations/current" to setOf(GET),
        "(.*)?/info" to setOf(GET),
        "/prisons/[^/]+/personal-officer/migrate" to setOf(POST),
        "/prisons/[^/]+/policies" to setOf(GET, PUT),
        // job-classifications endpoint is used by shared component and often provides values that are not prison codes
        "/prisons/[^/]+/staff/\\d*/job-classifications" to setOf(GET),
        "/staff/returning-from-leave" to setOf(PUT),
        "/subject-access-request" to setOf(GET),
        "/key-worker/offender/${Prisoner.PATTERN}" to setOf(GET),
      ).map { PolicyNotRequired(it.first.toRegex(), it.second) }
  }
}

private data class PolicyNotRequired(
  val urlRegex: Regex,
  val methods: Set<HttpMethod>,
)
