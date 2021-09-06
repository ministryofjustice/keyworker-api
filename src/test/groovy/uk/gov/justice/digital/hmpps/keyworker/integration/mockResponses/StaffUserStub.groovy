package uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses

class StaffUserStub {

    static responseItag(username) {
        def response = """
{
    "staffId": -2,
    "firstName": "Prison",
    "lastName": "User",
    "username": "${username}"
}
        """
        return response
    }
}