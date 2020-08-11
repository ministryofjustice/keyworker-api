package uk.gov.justice.digital.hmpps.keyworker.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.security.UserSecurityUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

import static uk.gov.justice.digital.hmpps.keyworker.utils.MdcUtility.USER_ID_HEADER;

@Slf4j
@Component
@Order(1)
public class UserMdcFilter implements Filter {

    private final UserSecurityUtils userSecurityUtils;

    @Autowired
    public UserMdcFilter(final UserSecurityUtils userSecurityUtils) {
        this.userSecurityUtils = userSecurityUtils;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // Initialise - no functionality
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        final var currentUsername = userSecurityUtils.getCurrentUsername();

        try {
            if (currentUsername != null) {
                MDC.put(USER_ID_HEADER, currentUsername);
            }
            chain.doFilter(request, response);
        } finally {
            if (currentUsername != null) {
                MDC.remove(USER_ID_HEADER);
            }
        }
    }

    @Override
    public void destroy() {
        // Destroy - no functionality
    }
}
