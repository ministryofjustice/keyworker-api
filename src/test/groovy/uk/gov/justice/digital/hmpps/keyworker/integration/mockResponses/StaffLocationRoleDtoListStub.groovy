package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses

class StaffLocationRoleDtoListStub {

    static response = '''
[
    {
        "staffId": -5,
        "firstName": "Another",
        "lastName": "CUser",
        "numberAllocated": 3
    },
    {
        "staffId": -3,
        "firstName": "HPA",
        "lastName": "AUser",
        "numberAllocated": 0
    },
    {
        "staffId": -4,
        "firstName": "Test",
        "lastName": "TUser",
        "numberAllocated": 2
    },
    {
        "staffId": -2,
        "firstName": "API",
        "lastName": "DUser",
        "numberAllocated": 10
    }
]
'''
}

