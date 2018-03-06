package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelperTest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link BulkOffenderKeyworkerImporter}.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class BulkOffenderKeyworkerImporterTest {

    @Autowired
    private BulkOffenderKeyworkerImporter importer;

    @Test
    public void translateAndStore() {
        OffenderKeyworkerDto testDto = ConversionHelperTest.getActiveOffenderKeyworkerDto();

        importer.translateAndStore(Collections.singletonList(testDto));

        List<OffenderKeyworker> okws = importer.importAll();

        verifyPersistedOffenderKeyworker(testDto, okws.get(0));
    }

    private void verifyPersistedOffenderKeyworker(OffenderKeyworkerDto dto, OffenderKeyworker okw) {
        ConversionHelperTest.verifyConversion(dto, okw);

        assertThat(okw.getOffenderKeyworkerId()).isNotNull();
        assertThat(okw.getAllocationType()).isEqualTo(AllocationType.MANUAL);
        assertThat(okw.getAllocationReason()).isEqualTo(AllocationReason.MANUAL);
        assertThat(okw.getDeallocationReason()).isNull();
    }
}
