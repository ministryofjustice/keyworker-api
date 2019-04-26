package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification
import uk.gov.justice.digital.hmpps.keyworker.integration.mockApis.Elite2Api

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations = "classpath:test-application-override.properties")
@ContextConfiguration
@ActiveProfiles("test")
@Slf4j
abstract class TestSpecification extends Specification {

    private String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlcm5hbFVzZXIiOnRydWUsInVzZXJfbmFtZSI6IklUQUdfVVNFUiIsInNjb3BlIjpbInJlYWQiLCJ3cml0ZSJdLCJleHAiOjE4NDc4ODY4MTQsImF1dGhvcml0aWVzIjpbIlJPTEVfS1dfQURNSU4iLCJST0xFX09NSUNfQURNSU4iXSwianRpIjoiMzA1MjcwZjItYzZiYS00ZWY5LWIxOGMtYTllZTE1Zjk0ZTE2IiwiY2xpZW50X2lkIjoiZWxpdGUyYXBpY2xpZW50In0.WbqnPlPqr08tC7PEilLOrkzfh0UziyuD9lFnTUVbCQIK8Gp0c4iP9aJ0NydiPhcRzoUfaA9eUoZyMBp1GwpBa5kn-YNax4mBfL0pXT-zl6c1Bx6VqmVCNmAT5BXl2tkfAi0DnbRHHAqwaAR4UlGe5JSMt6mloomGqNFHkAuJinndR1LC9zlm0473LnLgmDaCVEuJDlUbaL660o5nHSEimC2yNpm8VI7_i5nYSzECpKw_BYkBuiuIQJp_9-BLcf67Q3FkxM1NDqQIPwfKtTQ2ayJJEWdaI0Z8PrwxTlDFwm7nfSBSf-rXSXaHaL_9BBnsdboOx5lEpl5p7f1A2bOjag"
    private String adminToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlcm5hbFVzZXIiOmZhbHNlLCJzY29wZSI6WyJyZWFkIl0sImV4cCI6MTgzOTEyMTg4OSwiYXV0aG9yaXRpZXMiOlsiUk9MRV9TWVNURU1fVVNFUiIsIlJPTEVfTUFJTlRBSU5fQUNDRVNTX1JPTEVTIiwiUk9MRV9LV19NSUdSQVRJT04iXSwianRpIjoiMmZmNDhmN2EtNzM4MS00OTI0LTkzMTctMDc2MWQ5M2ZjMGRiIiwiY2xpZW50X2lkIjoib21pY2FkbWluIn0.BpPv6Jpjkcz2VDkS41mzkgY3tZTB0k0BYqyuksyUAQbpMMEC5TN3KneQgSOHtGb0A8JOixrO1-OJcyxLIoAd4uoflKUA7FekVW75efOTezYfh-aGm41Bf1s7nv4j-BZSvtunmhRuEyPBYNfQVaMo1L7gdf01pLF9mvJVe_4vp-kZalMuqo5P13mgZO9EBNjv_JrtvL8Zp8D-MnadUJXorL8__v3eRhImJuGULhkXbIb7nc7h1MsNmJ-Fvh8jO62OIyqih7SYx_ed1VBG89CETIwVmZCa9msY4zpdLmzS_Si53vmznLNyZ-lH_Gre1d_qe3jU_EC0H5F3B_U7Oq1bTg"

    @Rule
    Elite2Api elite2api = new Elite2Api()

    @Rule
    TestWatcher t = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            log.info ("Starting test '{}'", description.getDisplayName())
        }
        @Override
        protected void finished(Description description) {
            log.info ("Finished test '{}'", description.getDisplayName())
        }
    }

    @Autowired
    TestRestTemplate restTemplate

    @Autowired
    ObjectMapper objectMapper

    def migrated(prisonId){
        elite2api.stubAllocationHistory(prisonId)
        elite2api.stubAccessCodeListForKeyRole(prisonId)
        elite2api.stubAccessCodeListForKeyAdminRole(prisonId)

        def response = restTemplate.exchange("/key-worker/enable/${prisonId}/auto-allocate?migrate=true",
                HttpMethod.POST, createHeaderEntityForAdminUser("headers"), String.class)
        response.toString()
    }

    def migratedForAutoAllocation(prisonId){
        elite2api.stubAllocationHistoryForAutoAllocation(prisonId)
        elite2api.stubAccessCodeListForKeyRole(prisonId)
        elite2api.stubAccessCodeListForKeyAdminRole(prisonId)

        def response = restTemplate.exchange("/key-worker/enable/${prisonId}/auto-allocate?migrate=true&capacity=6,9&frequency=2",
                HttpMethod.POST, createHeaderEntityForAdminUser("headers"), String.class)
        response.toString()
    }

    HttpEntity createHeaderEntityForAdminUser(Object entity) {
        HttpHeaders headers = new HttpHeaders()
        headers.add("Authorization", "bearer " + adminToken)
        headers.setContentType(MediaType.APPLICATION_JSON)
        new HttpEntity<>(entity, headers)
    }

    HttpEntity createHeaderEntity(Object entity) {
        HttpHeaders headers = new HttpHeaders()
        headers.add("Authorization", "bearer " + token)
        headers.setContentType(MediaType.APPLICATION_JSON)
        new HttpEntity<>(entity, headers)
    }
}
