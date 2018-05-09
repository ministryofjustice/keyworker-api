package uk.gov.justice.digital.hmpps.keyworker.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;
import java.util.Optional;

import static uk.gov.justice.digital.hmpps.keyworker.utils.UserContext.CORRELATION_ID;

@Slf4j
@Component
public class CorrelationHeaderFilter implements Filter {

    private final MdcUtility mdcUtility;

    @Autowired
    public CorrelationHeaderFilter(MdcUtility mdcUtility) {
        this.mdcUtility = mdcUtility;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // Initialise - no functionality
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        Optional<String> correlationIdOptional = Optional.ofNullable(UserContext.getCorrelationId());

        try {
            MDC.put(CORRELATION_ID, correlationIdOptional.orElseGet(mdcUtility::generateUUID));
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    @Override
    public void destroy() {
        // destroy - no functionality
    }

}
