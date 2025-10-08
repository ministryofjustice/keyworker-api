package uk.gov.justice.digital.hmpps.keyworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
class AllocationApi

fun main(args: Array<String>) {
  runApplication<AllocationApi>(*args)
}
