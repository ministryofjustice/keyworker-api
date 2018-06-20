package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses

class StaffLocationRoleDtoListStub {

    static getResponse() {
        def response = """
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
"""
        return response
    }

    static getResponseForStatusUpdate() {
        def response = """
[
    {
        "staffId": -15,
        "firstName": "Another",
        "lastName": "CUser",
        "numberAllocated": 3
    },
    {
        "staffId": -13,
        "firstName": "HPA",
        "lastName": "AUser",
        "numberAllocated": 0
    },
    {
        "staffId": -14,
        "firstName": "Test",
        "lastName": "TUser",
        "numberAllocated": 2
    },
    {
        "staffId": -12,
        "firstName": "API",
        "lastName": "DUser",
        "numberAllocated": 10
    }
]
"""
        return response
    }

    static getResponseForAutoAllocation() {
        def response = """
[
   { "staffId": 1001, "firstName": "Staff", "lastName": "User1" },
   { "staffId": 1002, "firstName": "Staff", "lastName": "User2" },
   { "staffId": 1003, "firstName": "Staff", "lastName": "User3" }
]
"""
       return response
    }
}
