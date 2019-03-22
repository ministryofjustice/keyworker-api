package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;


@RunWith(MockitoJUnitRunner.class)
public class ReconciliationServiceTest {

    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private TelemetryClient telemetryClient;

    private ReconciliationService service;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ReconciliationService(nomisService, repository, telemetryClient);
    }

    @Test
    public void testit() {
        service.reconcileKeyWorkerAllocations("LEI");
    }
}
