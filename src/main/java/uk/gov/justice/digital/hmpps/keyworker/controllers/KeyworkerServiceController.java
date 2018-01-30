package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

@RestController
@RequestMapping(value="v1/keyworker")
@Api(tags = {"v1/key-worker"})
public class KeyworkerServiceController {
    @Autowired
    private KeyworkerService keyworkerService;

    private static final Logger logger = LoggerFactory.getLogger(KeyworkerServiceController.class);

    @RequestMapping(value="/{offenderKeyworkerId}",method = RequestMethod.GET)
    @ApiOperation(value = "Get Key-worker for an offender")
    public OffenderKeyworker getOffenderKeyworker(@PathVariable("offenderKeyworkerId") String offenderKeyworkerId) {
        logger.debug("Looking up data for keyworker {}", offenderKeyworkerId);

        OffenderKeyworker keyworker = keyworkerService.getOffenderKeyworker(offenderKeyworkerId);
        return keyworker;
    }

    @RequestMapping(value="/{offenderKeyworkerId}",method = RequestMethod.PUT)
    @ApiOperation(value = "Update Key-worker")
    public void updateOffenderKeyworker(@PathVariable("offenderKeyworkerId") String offenderKeyworkerId, @RequestBody OffenderKeyworker offenderKeyworker) {
        keyworkerService.updateOffenderKeyworker( offenderKeyworker );
    }

    @RequestMapping(value="/{offenderKeyworkerId}",method = RequestMethod.POST)
    @ApiOperation(value = "Save Key-worker")
    public void saveOffenderKeyworker(@RequestBody OffenderKeyworker offenderKeyworker) {
       keyworkerService.saveOffenderKeyworker( offenderKeyworker );
    }

    @RequestMapping(value="/{offenderKeyworkerId}",method = RequestMethod.DELETE)
    @ApiOperation(value = "Delete Key-worker ")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffenderKeyworker(@PathVariable("offenderKeyworkerId") String offenderKeyworkerId, @RequestBody OffenderKeyworker offenderKeyworker) {
        keyworkerService.deleteOffenderKeyworker( offenderKeyworker );
    }
}
