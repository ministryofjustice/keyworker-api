package uk.gov.justice.digital.hmpps.keyworker.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.expression.BeanFactoryResolver
import org.springframework.expression.spel.SpelEvaluationException
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.method.HandlerMethod
import kotlin.collections.filterIsInstance
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.toList
import kotlin.jvm.java
import kotlin.text.trimMargin

const val PRISON = "Prison"
const val MANAGE_STAFF = "Manage Staff"
const val MANAGE_ALLOCATIONS = "Manage Allocations"
const val REFERENCE_DATA = "Reference Data"
const val MANAGE_KEYWORKERS = "Manage Keyworkers"
const val ALLOCATE_KEY_WORKERS = "Allocate Keyworkers"

@Configuration
class OpenApiConfiguration(
  buildProperties: BuildProperties,
) {
  private val version: String = buildProperties.version

  @Autowired
  private lateinit var context: ApplicationContext

  @Bean
  fun customOpenAPI(): OpenAPI? =
    OpenAPI()
      .info(
        Info()
          .title("Keyworker API")
          .version(version)
          .description(
            """
            |API for retrieving and managing keyworker allocations.
            |
            |## Authentication
            |
            |This API uses OAuth2 with JWTs. You will need to pass the JWT in the `Authorization` header using the `Bearer` scheme.
            |All endpoints are designed to work with client tokens and user tokens should not be used with this service.
            |
            |## Authorisation
            |
            |The API uses roles to control access to the endpoints. The roles required for each endpoint are documented in the endpoint descriptions.
            |
            """.trimMargin(),
          ).contact(
            Contact()
              .name("HMPPS Digital Studio")
              .email("moveandimprove@justice.gov.uk"),
          ),
      ).components(
        Components().addSecuritySchemes(
          "bearer-jwt",
          SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization"),
        ),
      ).addSecurityItem(SecurityRequirement().addList("bearer-jwt", listOf("read", "write")))
      .addTagsItem(Tag().name(PRISON).description("Endpoints for prison level operations"))
      .addTagsItem(Tag().name(MANAGE_STAFF).description("Endpoints for managing staff"))
      .addTagsItem(Tag().name(MANAGE_ALLOCATIONS).description("Managing allocations"))
      .addTagsItem(Tag().name(REFERENCE_DATA).description("Endpoints for reference data"))
      .addTagsItem(Tag().name(MANAGE_KEYWORKERS).description("Endpoints for managing keyworkers"))
      .addTagsItem(Tag().name(ALLOCATE_KEY_WORKERS).description("Endpoints for allocating keyworkers"))

  @Bean
  fun preAuthorizeCustomizer(): OperationCustomizer =
    OperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
      handlerMethod.preAuthorizeForMethodOrClass()?.let {
        val preAuthExp = SpelExpressionParser().parseExpression(it)
        val evalContext = StandardEvaluationContext()
        evalContext.beanResolver = BeanFactoryResolver(context)
        evalContext.setRootObject(
          object {
            fun hasRole(role: String) = listOf(role)

            fun hasAnyRole(vararg roles: String) = roles.toList()
          },
        )

        val roles =
          try {
            (preAuthExp.getValue(evalContext) as List<*>).filterIsInstance<String>()
          } catch (e: SpelEvaluationException) {
            emptyList()
          }
        if (roles.isNotEmpty()) {
          operation.description = "${operation.description ?: ""}\n\n" +
            if (roles.isEmpty()) {
              ""
            } else {
              "Requires one of the following roles:\n" +
                roles.joinToString(prefix = "* ", separator = "\n* ")
            }
        }
      }

      operation
    }

  private fun HandlerMethod.preAuthorizeForMethodOrClass() =
    getMethodAnnotation(PreAuthorize::class.java)?.value
      ?: beanType.getAnnotation(PreAuthorize::class.java)?.value
}
