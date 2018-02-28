package uk.gov.justice.digital.hmpps.keyworker.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@Slf4j
public class UserContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {


        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        final String authToken = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        log.debug("I am entering the key-worker service id with auth token: {}", authToken);

        UserContext.setAuthToken(authToken);
        UserContext.setCorrelationId(httpServletRequest.getHeader(UserContext.CORRELATION_ID));

        filterChain.doFilter(httpServletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}
}