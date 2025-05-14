package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.web.context.request.RequestContextHolder.getRequestAttributes
import java.time.LocalDateTime

data class KeyworkerContext(
  val requestAt: LocalDateTime = LocalDateTime.now(),
  val username: String,
  val activeCaseloadId: String? = null,
) {
  companion object {
    const val SYSTEM_USER_NAME = "SYS"

    fun get(): KeyworkerContext =
      getRequestAttributes()
        ?.getAttribute(KeyworkerContext::class.simpleName!!, 0) as KeyworkerContext?
        ?: KeyworkerContext(username = SYSTEM_USER_NAME).update()
  }
}

private fun KeyworkerContext.update() =
  also {
    getRequestAttributes()?.setAttribute(KeyworkerContext::class.simpleName!!, it, 0)
  }
