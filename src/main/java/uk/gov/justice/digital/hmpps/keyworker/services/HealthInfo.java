package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/** Adds version data to the /health endpoint. This is called by the UI to display API details */
@Component
public class HealthInfo implements HealthIndicator {

    /**
     * This reads info set up in config.yml
     */
    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Override
    public Health health() {
        return Health.up().withDetail("version", getVersion()).build();
    }

    /**
     * @return health data. Note this is unsecured so no sensitive data allowed!
     */
    private String getVersion(){
        return buildProperties == null ? "version not available" : buildProperties.getVersion();
    }
}