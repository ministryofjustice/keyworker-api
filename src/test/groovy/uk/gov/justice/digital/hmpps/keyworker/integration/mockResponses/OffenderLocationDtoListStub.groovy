package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses

class OffenderLocationDtoListStub {

    static getResponseForAutoAllocation(prisonId) {
        return """
[
 {"offenderNo":"UNALLOC1", "bookingId":-41, "firstName":"MOEEN", "lastName":"ALI",    "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC2", "bookingId":-40, "firstName":"JOE",   "lastName":"ROOT",   "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC3", "bookingId":-39, "firstName":"BEN",   "lastName":"STOKES", "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC4", "bookingId":-41, "firstName":"MOEEN", "lastName":"ALI",    "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC5", "bookingId":-40, "firstName":"JOE",   "lastName":"ROOT",   "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC6", "bookingId":-39, "firstName":"BEN",   "lastName":"STOKES", "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC7", "bookingId":-41, "firstName":"MOEEN", "lastName":"ALI",    "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC8", "bookingId":-40, "firstName":"JOE",   "lastName":"ROOT",   "agencyId":"${prisonId}"},
 {"offenderNo":"UNALLOC9", "bookingId":-39, "firstName":"BEN",   "lastName":"STOKES", "agencyId":"${prisonId}"},
 {"offenderNo":"ALLOCED1", "bookingId":-38, "firstName":"STEVE", "lastName":"STOKES", "agencyId":"${prisonId}"},
 {"offenderNo":"EXPIRED1", "bookingId":-37, "firstName":"CHRIS", "lastName":"WOAKES", "agencyId":"${prisonId}"} 
]"""
    }

    static getResponseOffender(prisonId, offenderNo) {
        return """
[
 {"offenderNo":"${offenderNo}", "bookingId":-41, "firstName":"MOEEN", "lastName":"LEI","agencyId":"${prisonId}"}
]"""
    }

    static getResponsePrisoner(offenderNo) {
        return """
[
 {
    "offenderNo": "${offenderNo}",
    "title": "Mr",
    "firstName": "Michael",
    "lastName": "Smith",
    "dateOfBirth": "1970-01-01",
    "gender": "M",
    "currentlyInPrison": "Y",
    "latestBookingId": -20,
    "latestLocationId": "A-1-1",
    "latestLocation": "Leeds",
    "religion": "string",
    "receptionDate": "2004-01-01"
  }
]"""
    }

}