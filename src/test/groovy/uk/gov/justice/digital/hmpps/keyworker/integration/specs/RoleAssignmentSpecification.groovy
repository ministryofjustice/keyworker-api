package uk.gov.justice.digital.hmpps.keyworker.integration.specs


import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification

class RoleAssignmentSpecification extends TestSpecification {

    private static final MAINTAIN_ACCESS_ROLES_ADMIN_USER_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpbnRlcm5hbFVzZXIiOnRydWUsInVzZXJfbmFtZSI6Ik1BSU5UQUlOX0FDQ0VTU19ST0xFU19BRE1JTl9VU0VSIiwic2NvcGUiOlsicmVhZCIsIndyaXRlIl0sImV4cCI6MjgyMDE0MDIzMywiY2xpZW50X2lkIjoiZWxpdGUyYXBpY2xpZW50IiwiYXV0aG9yaXRpZXMiOlsiUk9MRV9NQUlOVEFJTl9BQ0NFU1NfUk9MRVNfQURNSU4iXSwianRpIjoiZGViZDMyODAtMzQwZC00NjU3LTg4ZGUtYWQ4MTI5MTkyNjhhIn0.ozPcQSieuYcYp3KJGrMUC0aZXAbWehHPLQG5pbfoov6L505Q0MmCQZGi_7o07ughJqeyigp_l8v5QB81hkBoS4qdGKlQYc3IeDWRb7oOYt42oocwdPw6vXS5xtZ4odI4JeYzvb2hL7kH3N0JTKZTkVCdts-WLt1CgaujlXEfGpZwUBvjrQQQ-3OxUEgRw7lEKrPqdCFZdFT0VnLF6pM-0PFztKgZ0y5cPDRApOpX6DE1IAwwiP-9VvgWpZJxbcBj3SI_YbazlbX080QOHbE3VRrSzyHU6QhYIVP3h6mu6DhI7ogrsnBEAPBUOn1_vy4B2ElmnzhRE7zqL9d6RCPJsg'
    private static final MAINTAIN_ACCESS_ROLES_USER_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpbnRlcm5hbFVzZXIiOnRydWUsInVzZXJfbmFtZSI6Ik1BSU5UQUlOX0FDQ0VTU19ST0xFU19BRE1JTl9VU0VSIiwic2NvcGUiOlsicmVhZCIsIndyaXRlIl0sImV4cCI6MjgyMDE0MTI5MSwiY2xpZW50X2lkIjoiZWxpdGUyYXBpY2xpZW50IiwiYXV0aG9yaXRpZXMiOlsiUk9MRV9NQUlOVEFJTl9BQ0NFU1NfUk9MRVMiXSwianRpIjoiZGViZDMyODAtMzQwZC00NjU3LTg4ZGUtYWQ4MTI5MTkyNjhhIn0.qOtwHPK8YccIFdYi3ROVp6qDSq1Sp4NqLR_b3c-oajCMK1toX13H7YYuREb7BBCLzwedA8s3BL-S5tIk0pW6OaMFAIURweVaiSGcJuIbILq40xK0OFQ7yiMKb-QIV6H94HkSjdBcbJnvfSveNZe_ghH5R-brg78V_NnsOOlETYfErHzTlUs8CWwBgxcmd5ZRvMLpI1wWpFZk6zcYi4NgcTMmtPrXaROux-p8US1NvoEobdT3qLGLMLRPjIv4b2fwu88oAjjEoLNZC7dVMTUVq_yAI2al-MBV3DXzsBb9li7-7OAVZsAkifazTPaufC304dc07kMQNJi4bi3qlx53pg'

    def "A user that has the MAINTAIN_ACCESS_ROLES_ADMIN role can make role assignment changes"() {
        given: "That I have the MAINTAIN_ACESS_ROLES_ADMIN role"

        def token = MAINTAIN_ACCESS_ROLES_ADMIN_USER_TOKEN

        when: "I request roles assignment changes"

        applyRoleAssignmentsSpecification(createRoleAssignmentsSpecification(), token)
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$[0].caseload').isEqualTo('MDI')
                .jsonPath('$[0].numMatchedUsers').isEqualTo(0)
                .jsonPath('$[0].numAssignRoleSucceeded').isEqualTo(0)
                .jsonPath('$[0].numAssignRoleFailed').isEqualTo(0)
                .jsonPath('$[0].numUnassignRoleSucceeded').isEqualTo(0)
                .jsonPath('$[0].numUnassignRoleIgnored').isEqualTo(0)
                .jsonPath('$[0].numUnassignRoleFailed').isEqualTo(0)

        then: "The request is accepted"

        noExceptionThrown()
    }

    def "A user that has does not have the MAINTAIN_ACCESS_ROLES_ADMIN role cannot make assignment changes"() {
        given: "That I do not have the MAINTAIN_ACCESS_ROLES_ADMIN role"

        def token = MAINTAIN_ACCESS_ROLES_USER_TOKEN

        when: "I request roles assignment changes"

        applyRoleAssignmentsSpecification(createRoleAssignmentsSpecification(), token)
                .expectStatus().isForbidden()

        then: "The request is rejected"

        noExceptionThrown()
    }

    private static RoleAssignmentsSpecification createRoleAssignmentsSpecification() {
        RoleAssignmentsSpecification.builder().caseloads(['MDI']).rolesToMatch(['KW_ADMIN']).build()
    }

    private WebTestClient.ResponseSpec applyRoleAssignmentsSpecification(RoleAssignmentsSpecification specification, String accessToken) {
        prisonapi.stubAccessCodeListForKeyAdminRole('MDI')

        HttpHeaders headers = new HttpHeaders()
        headers.setBearerAuth(accessToken)
        headers.setContentType(MediaType.APPLICATION_JSON)

        postForEntity('/caseloads-roles', createHeaderEntity(accessToken), specification)
    }
}
