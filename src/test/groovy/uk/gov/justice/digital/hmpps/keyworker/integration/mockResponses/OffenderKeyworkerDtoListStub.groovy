package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses


class OffenderKeyworkerDtoListStub {

    static def getResponse(prisonId) {
        def response = """
[
    {
        "offenderNo": "A1176RS",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-06-01T12:14:00",
        "userId": "ITAG_USER",
        "active": "Y",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A5576RS",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-05-01T11:14:00",
        "userId": "ITAG_USER",
        "active": "Y",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A6676RS",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2016-01-01T11:14:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A6676RS",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T11:14:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A9876RS",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T11:14:00",
        "userId": "ITAG_USER",
        "active": "Y",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234AP",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T11:14:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234AP",
        "staffId":  -5,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T12:14:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234AI",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T12:14:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234AI",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T12:13:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234AL",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2017-01-01T12:00:00",
        "userId": "ITAG_USER",
        "active": "N",
        "created": "2018-04-12T14:08:37.766",
        "createdBy": "SA"
    },
    {
        "offenderNo": "A1234XX",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2018-05-30T12:00:00",
        "userId": "ITAG_USER",
        "active": "Y",
        "created": "2018-05-31T14:08:37.766",
        "createdBy": "ELITE2_API_USER"
    },
    {
        "offenderNo": "A1234XY",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2018-05-30T12:00:00",
        "userId": "ELITE2_API_USER",
        "active": "Y",
        "created": "2018-05-31T14:08:37.766",
        "createdBy": "ITAG_USER"
    },
        {
        "offenderNo": "A1234XZ",
        "staffId":  -4,
        "agencyId": "${prisonId}",
        "assigned": "2018-05-30T12:00:00",
        "userId": "ELITE2_API_USER",
        "active": "Y",
        "created": "2018-05-31T14:08:37.766",
        "createdBy": "ITAG_USER"
    }
]
"""
        return response
    }

    static def getResponseForAutoAllocation(prisonId) {
        def response = """
[
    {"offenderNo": "ALLOCED1", "staffId": 1001, "agencyId": "${prisonId}", "assigned": "2017-06-01T12:14:00",                                  "userId": "ITAG_USER", "active": "Y"},
    {"offenderNo": "EXPIRED1", "staffId": 1002, "agencyId": "${prisonId}", "assigned": "2017-06-01T12:14:00", "expired":"2018-04-01T12:14:00", "userId": "ITAG_USER", "active": "N"}
]
"""
        return response
    }
}