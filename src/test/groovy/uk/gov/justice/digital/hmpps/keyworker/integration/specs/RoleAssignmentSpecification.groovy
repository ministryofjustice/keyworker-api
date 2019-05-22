package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification

class RoleAssignmentSpecification extends TestSpecification {

    def "A user that has the MAINTAIN_ACCESS_ROLES_ADMIN role can make role assignment changes"() {
        given: "That I have the MAINTAIN_ACESS_ROLES_ADMIN role"

        HttpHeaders headers = new HttpHeaders()
        headers.setBearerAuth(adminToken)
        headers.setContentType(MediaType.APPLICATION_JSON)

        when: "I request roles assignment changes"

        elite2api.stubAccessCodeListForKeyAdminRole('MDI')

        def specification = RoleAssignmentsSpecification.builder().caseloads(['MDI']).rolesToMatch(['KW_ADMIN']).build()

        def response = restTemplate.exchange(
                '/caseloads-roles',
                HttpMethod.POST,
                new HttpEntity(specification, headers),
                Void.class)

        then: "The request is accepted"

        response.statusCode == HttpStatus.NO_CONTENT
    }

    def "A user that has neither the MAINTAIN_ACCESS_ROLES_ADMIN role nor the MAINTAIN_ACCESS_ROLE cannot make assignment changes"() {
        given: "That I have no suitable role"

        HttpHeaders headers = new HttpHeaders()
        headers.setBearerAuth(token)
        headers.setContentType(MediaType.APPLICATION_JSON)

        when: "I request roles assignment changes"

        elite2api.stubAccessCodeListForKeyAdminRole('MDI')

        def specification = RoleAssignmentsSpecification.builder().caseloads(['MDI']).rolesToMatch(['KW_ADMIN']).build()

        def response = restTemplate.exchange(
                '/caseloads-roles',
                HttpMethod.POST,
                new HttpEntity(specification, headers),
                Void.class)

        then: "The request is rejected"

        response.statusCode == HttpStatus.FORBIDDEN
    }
}
