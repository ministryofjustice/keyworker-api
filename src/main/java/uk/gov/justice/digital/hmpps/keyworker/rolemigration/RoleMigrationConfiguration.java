package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RootUriTemplateHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.BaseOAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote.RemoteRoleService;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayInterceptor;
import uk.gov.justice.digital.hmpps.keyworker.utils.ApiGatewayTokenGenerator;

import java.util.Arrays;

@Configuration
public class RoleMigrationConfiguration {

    @Value("${elite2.api.uri.root:http://localhost:8080/api}")
    private String apiRootUri;

    @Value("${accessTokenUri:http://localhost:8080/oauth/token}")
    private String accessTokenUri;

    @Value("${use.api.gateway.auth:false}")
    private boolean useApiGateway;

    @Value("${omicAdminClientId:omicadmin}")
    private String clientId;

    @Value("${omicAdminClientPassword:clientsecret}")
    private String clientSecret;


    @Autowired
    private ApiGatewayTokenGenerator apiGatewayTokenGenerator;

    @Bean("elite2RoleMigrationApi")
    public RestTemplate restTemplate() {
        RestTemplate template =  new OAuth2RestTemplate(
                resource(),
                new DefaultOAuth2ClientContext(new DefaultAccessTokenRequest())
        );

        if (useApiGateway) {
            template.getInterceptors().add(new ApiGatewayInterceptor(apiGatewayTokenGenerator));
        }

        template.setUriTemplateHandler(new RootUriTemplateHandler(apiRootUri, template.getUriTemplateHandler()));

        return template;
    }

    @Bean("roleService")
    public RoleService roleService() {
        return new RemoteRoleService(restTemplate());
    }

    @Bean("roleMigrationService")
    public RoleMigrationService roleMigrationService() {
        return new RoleMigrationService(roleService());
    }

    private BaseOAuth2ProtectedResourceDetails resource() {

        BaseOAuth2ProtectedResourceDetails resource = new ClientCredentialsResourceDetails();
        resource.setClientId(clientId);
        resource.setClientSecret(clientSecret);
        resource.setScope(Arrays.asList("read", "admin"));
        resource.setAccessTokenUri(accessTokenUri);

        return resource;
    }

}
