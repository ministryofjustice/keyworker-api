package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses


class StaffLocationRoleDtoListStub {

    static response = '''
[
    {
        "staffId": -5,
        "firstName": "Another",
        "lastName": "CUser",
        "agencyId": "LEI",
        "agencyDescription": "LEEDS",
        "fromDate": "2018-01-02",
        "position": "AO",
        "positionDescription": "Admin Officer",
        "role": "KW",
        "roleDescription": "Key Worker",
        "scheduleType": "FT",
        "scheduleTypeDescription": "Full Time",
        "hoursPerWeek": 11
    }
]
'''
}

