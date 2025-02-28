package uk.gov.justice.digital.hmpps.keyworker.utils

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
@Order(3)
class RequestLogFilter
  @Autowired
  constructor(
    @Value("\${logging.uris.exclude.regex}") excludeUris: String,
  ) : OncePerRequestFilter() {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
    private val excludeUriRegex: Pattern = Pattern.compile(excludeUris)

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
    ) {
      if (excludeUriRegex.matcher(request.requestURI).matches()) {
        MDC.put(MdcUtility.SKIP_LOGGING, "true")
      }
      try {
        val start = LocalDateTime.now()
        if (log.isTraceEnabled && MdcUtility.isLoggingAllowed) {
          log.trace("Request: {} {}", request.method, request.requestURI)
        }
        filterChain.doFilter(request, response)
        val duration = Duration.between(start, LocalDateTime.now()).toMillis()
        MDC.put(MdcUtility.REQUEST_DURATION, duration.toString())
        val status = response.status
        MDC.put(MdcUtility.RESPONSE_STATUS, status.toString())
        if (log.isTraceEnabled && MdcUtility.isLoggingAllowed) {
          log.trace(
            "Response: {} {} - Status {} - Start {}, Duration {} ms",
            request.method,
            request.requestURI,
            status,
            start.format(formatter),
            duration,
          )
        }
      } finally {
        MDC.remove(MdcUtility.REQUEST_DURATION)
        MDC.remove(MdcUtility.RESPONSE_STATUS)
        MDC.remove(MdcUtility.SKIP_LOGGING)
      }
    }

    companion object {
      private val log = LoggerFactory.getLogger(this::class.java)
    }
  }
