package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

import java.util.List;

@RestController
@RequestMapping(value="v1/keyworker")
@Api(tags = {"v1/key-worker"})
public class KeyworkerServiceController {
    @Autowired
    private KeyworkerService keyworkerService;

    private static final Logger logger = LoggerFactory.getLogger(KeyworkerServiceController.class);

    @RequestMapping(value="/{staffUsername}",method = RequestMethod.GET)
    @ApiOperation(value = "Get offenders of a keyworker")
    public List<OffenderKeyworkerDto> getOffendersForKeyworker(@PathVariable("staffUsername") String staffUsername) {
        logger.debug("Looking up data for staff username {}", staffUsername);

        return keyworkerService.getOffendersForKeyworker(staffUsername);
    }

}
