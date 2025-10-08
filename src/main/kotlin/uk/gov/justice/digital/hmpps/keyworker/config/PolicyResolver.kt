package uk.gov.justice.digital.hmpps.keyworker.config

import org.hibernate.cfg.AvailableSettings
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.stereotype.Component

@Component
class PolicyResolver :
  CurrentTenantIdentifierResolver<String>,
  HibernatePropertiesCustomizer {
  override fun customize(hibernateProperties: MutableMap<String, Any>) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this)
  }

  override fun resolveCurrentTenantIdentifier(): String = AllocationContext.get().policy.name

  override fun validateExistingCurrentSessions(): Boolean = true
}
