package uk.gov.justice.digital.hmpps.keyworker.utils

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.security.UserSecurityUtils
import java.io.IOException
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import kotlin.Throws

@Component
@Order(1)
class UserMdcFilter @Autowired constructor(private val userSecurityUtils: UserSecurityUtils) : Filter {

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