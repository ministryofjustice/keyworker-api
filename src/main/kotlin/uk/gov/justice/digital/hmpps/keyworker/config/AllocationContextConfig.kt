package uk.gov.justice.digital.hmpps.keyworker.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ValidationException
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy.KEY_WORKER

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
        "/queue-admin/retry-all-dlqs",
        "/prison-statistics/calculate",
        "/health/**",
        "/info",
        "/ping",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/swagger-resources/**",
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

  private fun HttpServletRequest.policy(): AllocationPolicy = AllocationPolicy.of(getHeader(PolicyHeader.NAME)) ?: KEY_WORKER
}
