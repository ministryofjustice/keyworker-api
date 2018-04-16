package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Rule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification
import uk.gov.justice.digital.hmpps.keyworker.integration.mockApis.Elite2Api

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations = "classpath:test-application-override.properties")
@ContextConfiguration
abstract class TestSpecification extends Specification {

    private String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlcm5hbFVzZXIiOnRydWUsInVzZXJfbmFtZSI6IklUQUdfVVNFUiIsInNjb3BlIjpbInJlYWQiLCJ3cml0ZSJdLCJleHAiOjE4MzkxMTY0MzgsImF1dGhvcml0aWVzIjpbIlJPTEVfTElDRU5DRV9DQSIsIlJPTEVfS1dfQURNSU4iXSwianRpIjoiMDMyYzc0MmEtN2Y2OS00YjgyLTgwOGUtNDQ3MTkxM2Y0NGM1IiwiY2xpZW50X2lkIjoiZWxpdGUyYXBpY2xpZW50In0.nJRjJkZbjIJSL8Fah6Lm0eie7BitzVyjEIF2bgUM9Wh3hzciYWn0oADQ82W09qgrDqEf93EA69dHVhXMtktKNCVuT6zvwQQLOjwV2JyLpkI0Rq1TDgEx28duz1wnl_Kr6JFLDeSPmsZmM6mlPSf7oyur3x07__wwS3TXCnEeP_-M8qq-owveOa_0wPDD4fghWyb4QjjHcMYrjzHarrbiQDuAJCMnb3cxCSzHW5G99xLiISoHHGDTCTegpFquoqAXOORl5lx0H9MVl62cVjXrc_PqfqajHIAAYMNylNqL70ce-MKqHR-v1IdIYUCRvMb8mTpOQSuU6-CpTa3i4mYm9g"
    private String adminToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlcm5hbFVzZXIiOmZhbHNlLCJzY29wZSI6WyJyZWFkIl0sImV4cCI6MTgzOTEyMTg4OSwiYXV0aG9yaXRpZXMiOlsiUk9MRV9TWVNURU1fVVNFUiIsIlJPTEVfTUFJTlRBSU5fQUNDRVNTX1JPTEVTIiwiUk9MRV9LV19NSUdSQVRJT04iXSwianRpIjoiMmZmNDhmN2EtNzM4MS00OTI0LTkzMTctMDc2MWQ5M2ZjMGRiIiwiY2xpZW50X2lkIjoib21pY2FkbWluIn0.BpPv6Jpjkcz2VDkS41mzkgY3tZTB0k0BYqyuksyUAQbpMMEC5TN3KneQgSOHtGb0A8JOixrO1-OJcyxLIoAd4uoflKUA7FekVW75efOTezYfh-aGm41Bf1s7nv4j-BZSvtunmhRuEyPBYNfQVaMo1L7gdf01pLF9mvJVe_4vp-kZalMuqo5P13mgZO9EBNjv_JrtvL8Zp8D-MnadUJXorL8__v3eRhImJuGULhkXbIb7nc7h1MsNmJ-Fvh8jO62OIyqih7SYx_ed1VBG89CETIwVmZCa9msY4zpdLmzS_Si53vmznLNyZ-lH_Gre1d_qe3jU_EC0H5F3B_U7Oq1bTg"

    @Rule
    Elite2Api elite2api = new Elite2Api()

    @Autowired
    TestRestTemplate restTemplate

    @Autowired
    ObjectMapper objectMapper

    def migrated(String prisonId){
        elite2api.stubAllocationHistory(prisonId)
        elite2api.stubAccessCodeListForKeyRole(prisonId)
        elite2api.stubAccessCodeListForKeyAdminRole(prisonId)

        def response = restTemplate.exchange("/key-worker/enable/${prisonId}/auto-allocate?migrate=true", HttpMethod.POST, createHeaderEntityForAdminUser("headers"), String.class)
        response.toString()
    }


    HttpEntity createHeaderEntityForAdminUser(Object entity) {
        HttpHeaders headers = new HttpHeaders()
        headers.add("Authorization", "bearer " + adminToken)
        headers.setContentType(MediaType.APPLICATION_JSON)
        return new HttpEntity<>(entity, headers)
    }

    HttpEntity createHeaderEntity(Object entity) {
        HttpHeaders headers = new HttpHeaders()
        headers.add("Authorization", "bearer " + token)
        headers.setContentType(MediaType.APPLICATION_JSON)
        return new HttpEntity<>(entity, headers)
    }
}
