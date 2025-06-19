package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME

@Component
class AllocationContextHolder {
  fun setContext(context: AllocationContext) {
    AllocationContextHolder.context.set(context)
  }

  companion object {
    private var context: ThreadLocal<AllocationContext> =
      ThreadLocal.withInitial { AllocationContext(SYSTEM_USERNAME) }

    fun getContext(): AllocationContext = context.get()
  }
}
