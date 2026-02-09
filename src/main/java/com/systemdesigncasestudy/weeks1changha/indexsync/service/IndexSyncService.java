package com.systemdesigncasestudy.weeks1changha.indexsync.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.repository.BusinessRepository;
import com.systemdesigncasestudy.weeks1changha.cache.GeoCellCache;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.BusinessChangeEvent;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.ChangeLogRepository;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.GeohashIndexRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IndexSyncService {

    private final ChangeLogRepository changeLogRepository;
    private final BusinessRepository businessRepository;
    private final GeohashIndexRepository geohashIndexRepository;
    private final GeoCellCache geoCellCache;
    private final int batchSize;

    public IndexSyncService(
        ChangeLogRepository changeLogRepository,
        BusinessRepository businessRepository,
        GeohashIndexRepository geohashIndexRepository,
        GeoCellCache geoCellCache,
        @Value("${app.index-sync.batch-size:500}") int batchSize
    ) {
        this.changeLogRepository = changeLogRepository;
        this.businessRepository = businessRepository;
        this.geohashIndexRepository = geohashIndexRepository;
        this.geoCellCache = geoCellCache;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.index-sync.delay-ms:30000}")
    public void scheduledSync() {
        syncOnce(batchSize);
    }

    public int syncOnce(int maxEvents) {
        List<BusinessChangeEvent> events = changeLogRepository.pollUnprocessed(maxEvents);
        if (events.isEmpty()) {
            return 0;
        }

        List<Long> processedIds = new ArrayList<>();
        for (BusinessChangeEvent event : events) {
            applyEvent(event);
            processedIds.add(event.id());
        }

        changeLogRepository.markProcessed(processedIds);
        geoCellCache.clear();
        return processedIds.size();
    }

    private void applyEvent(BusinessChangeEvent event) {
        if (event.changeType() == ChangeType.DELETED) {
            geohashIndexRepository.deleteByBusinessId(event.businessId());
            return;
        }

        Business business = businessRepository.findById(event.businessId()).orElse(null);
        if (business == null || !business.isActive()) {
            geohashIndexRepository.deleteByBusinessId(event.businessId());
            return;
        }

        geohashIndexRepository.upsert(business.geohash(), business.id());
    }
}
