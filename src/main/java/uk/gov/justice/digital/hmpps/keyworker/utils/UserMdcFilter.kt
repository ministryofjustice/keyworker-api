package uk.gov.justice.digital.hmpps.keyworker.utils

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.security.UserSecurityUtils
import java.io.IOException

@Component
@Order(1)
class UserMdcFilter(private val userSecurityUtils: UserSecurityUtils) : Filter {

  @Throws(IOException::class, ServletException::class)
  override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val currentUsername = userSecurityUtils.currentUsername
    try {
      if (currentUsername != null) {
        MDC.put(MdcUtility.USER_ID_HEADER, currentUsername)
      }
      chain.doFilter(request, response)
    } finally {
      if (currentUsername != null) {
        MDC.remove(MdcUtility.USER_ID_HEADER)
      }
    }
  }
}
