package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.digital.hmpps.keyworker.dto.Page;

@ContextConfiguration
public abstract class AbstractServiceTest {

    @TestConfiguration
    static class Config {
        @Bean
        public ObjectPostProcessor objectPostProcessor() {
            return new ObjectPostProcessor() {
                @Override
                public Object postProcess(final Object object) {
                    return null;
                }
            };
        }

        @Bean
        public AuthenticationConfiguration authenticationConfiguration() {
            return new AuthenticationConfiguration();
        }
    }

    protected String expandUriTemplate(final String uriTemplate, final Object... uriVars) {
        final var builder = UriComponentsBuilder.fromUriString(uriTemplate);

        return builder.buildAndExpand(uriVars).toString();
    }

    protected HttpHeaders paginationHeaders(final long total, final long offset, final long limit) {
        final var headers = new HttpHeaders();

        headers.add(Page.HEADER_TOTAL_RECORDS, String.valueOf(total));
        headers.add(Page.HEADER_PAGE_OFFSET, String.valueOf(offset));
        headers.add(Page.HEADER_PAGE_LIMIT, String.valueOf(limit));

        return headers;
    }
}
