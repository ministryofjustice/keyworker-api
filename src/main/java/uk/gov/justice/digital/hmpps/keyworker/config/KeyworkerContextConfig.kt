package uk.gov.justice.digital.hmpps.keyworker.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ValidationException
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import uk.gov.justice.digital.hmpps.keyworker.security.AuthAwareAuthenticationToken

@Configuration
class KeyworkerContextConfiguration(
  private val keyworkerContextInterceptor: KeyworkerContextInterceptor,
) : WebMvcConfigurer {
  override fun addInterceptors(registry: InterceptorRegistry) {
    registry
      .addInterceptor(keyworkerContextInterceptor)
      .addPathPatterns("/**")
      .excludePathPatterns(
        "/batch/key-worker-recon",
        "/batch/update-status",
        "/queue-admin/retry-all-dlqs",
        "/prison-statistics/calculate",
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
    if (arrayOf("POST", "PUT", "PATCH", "DELETE").contains(request.method)) {
      request.setAttribute(
        KeyworkerContext::class.simpleName,
        KeyworkerContext(
          username = getUsername(),
          activeCaseloadId = request.caseloadId(),
        ),
      )
    }

    return true
  }

  private fun authentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw AccessDeniedException("User is not authenticated")

  private fun getUsername(): String =
    authentication()
      .name
      .trim()
      .takeUnless(String::isBlank)
      ?.also { if (it.length > 64) throw ValidationException("Username must be <= 64 characters") }
      ?: throw ValidationException("Could not find non empty username")

  private fun HttpServletRequest.caseloadId(): String? = getHeader(CaseloadIdHeader.NAME)
}
