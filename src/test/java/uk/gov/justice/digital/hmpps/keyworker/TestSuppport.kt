package uk.gov.justice.digital.hmpps.keyworker

import org.springframework.http.HttpRequest
import java.nio.charset.Charset

object TestSupport {
    val nullCharset: Charset? = null
    val nullHttpRequest: HttpRequest? = null
    val emptyBody = byteArrayOf()
}