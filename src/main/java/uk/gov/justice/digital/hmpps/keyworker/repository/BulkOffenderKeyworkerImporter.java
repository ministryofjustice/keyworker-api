package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.apache.commons.lang3.Validate;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BulkOffenderKeyworkerImporter extends SimpleJpaRepository<OffenderKeyworker, Long> {
    private final EntityManager entityManager;
    private List<OffenderKeyworker> items = new ArrayList<>();

    public BulkOffenderKeyworkerImporter(EntityManager entityManager) {
        super(OffenderKeyworker.class, entityManager);
        this.entityManager = entityManager;
    }

    public void translateAndStore(List<OffenderKeyworkerDto> dtos) {
        Validate.notNull(dtos);

        List<OffenderKeyworker> okwList = ConversionHelper.convertOffenderKeyworkerDto2Model(dtos);

        okwList.forEach(item -> {
            item.setAllocationType(AllocationType.MANUAL);
            item.setAllocationReason(AllocationReason.MANUAL);
        });

        items.addAll(okwList);
    }

    public List<OffenderKeyworker> importAll() {
        items.forEach(entityManager::persist);

        List<OffenderKeyworker> persistedItems = new ArrayList<>(items);

        items.clear();

        return persistedItems;
    }
}
