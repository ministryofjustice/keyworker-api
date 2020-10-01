package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class SwaggerUiWebMvcConfigurer : WebMvcConfigurer {
  override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
    registry.addResourceHandler("/swagger-ui/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
        .resourceChain(false)
  }

  override fun addViewControllers(registry: ViewControllerRegistry) {
    registry.addViewController("/swagger-ui/")
        .setViewName("forward:/swagger-ui/index.html")
  }
}