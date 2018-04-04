package uk.gov.justice.digital.hmpps.keyworker.rolemigration.repository;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;

import javax.annotation.Resource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RemoteRoleServiceTest {

    private static final String TOKEN = "A_TOKEN";

    @Resource(name="elite2RoleMigrationApi")
    private RestTemplate restTemplate;

    @Autowired
    private RoleService roleService;

    @Rule
    public WireMockRule mockServer = new WireMockRule(8080);

    @Test
    public void expectSpringContextToBeValid() {
        assertThat(roleService).isNotNull();
        assertThat(restTemplate).isInstanceOf(OAuth2RestTemplate.class);
    }

    @Test
    public void givenAMigrationRequest_thenExpectOAuthClientTokenReqeust() {
        mockServer.stubFor(
                post("/oauth/token")
                        .withBasicAuth("omicadmin", "clientsecret")
                        .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                        .withRequestBody(containing("grant_type=client_credentials"))
                .willReturn(
                        okJson("{" +
                                "\"access_token\":\""+ TOKEN + "\"," +
                                "\"token_type\":\"bearer\"," +
                                "\"expires_in\":1799," +
                                "\"scope\":\"read\"," +
                                "\"internalUser\":false," +
                                "\"jti\":\"a717ecf0-4286-49be-a6cc-30923861b259\"}")
                ));

        mockServer.stubFor(
                get("/api/staff/access-roles/caseload/CL/access-role/R")
                        .withHeader(HttpHeaders.AUTHORIZATION, equalToIgnoreCase("bearer " + TOKEN))
                .willReturn(okJson("[]")));

        roleService.findStaffMatchingCaseloadAndRole("CL", "R");

    }
}
