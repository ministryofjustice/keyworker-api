@org.hibernate.annotations.GenericGenerator(
        name = "ID_GENERATOR",
        strategy = "enhanced-sequence",
        parameters = {
                @org.hibernate.annotations.Parameter(
                        name = "sequence_name",
                        value = "HIBERNATE_ID_SEQUENCE"
                )
        }
)
package uk.gov.justice.digital.hmpps.keyworker.repository;