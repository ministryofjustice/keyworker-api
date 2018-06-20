update qrtz_job_details
  set job_class_name = 'uk.gov.justice.digital.hmpps.keyworker.batch.DeallocateQuartzJob'
where job_class_name = 'uk.gov.justice.digital.hmpps.keyworker.services.DeallocateQuartzJob';